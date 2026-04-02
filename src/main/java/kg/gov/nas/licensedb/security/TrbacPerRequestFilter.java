package kg.gov.nas.licensedb.security;

import kg.gov.nas.licensedb.service.TrbacSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TRBAC на каждый запрос.
 *
 * Зачем нужен:
 * - Если TRBAC применяется только при входе, то пользователю нужно перелогиниться,
 *   чтобы ограничения "включились/выключились" по времени.
 * - Этот фильтр делает роли "эффективными" для каждого HTTP-запроса.
 *
 * Важный технический момент:
 * - Мы НЕ хотим перезаписывать SecurityContext в сессии навсегда.
 * - Поэтому на время выполнения запроса мы подменяем Authentication (только для текущего потока),
 *   а затем в finally восстанавливаем исходный Authentication.
 * - Благодаря этому SecurityContextPersistenceFilter сохранит в сессию исходные роли,
 *   а не временно урезанные.
 *
 * Дополнение:
 * - Если в ходе обработки запроса контроллер/логика ОСОЗНАННО меняет Authentication
 *   (например, при смене пароля мы хотим снять флаг mustChangePassword в principal),
 *   то TRBAC не должен "затирать" это изменение.
 *   Поэтому мы восстанавливаем original ТОЛЬКО если текущий Authentication всё ещё равен
 *   токену, который был подставлен TRBAC.
 */
@Component
@Slf4j
public class TrbacPerRequestFilter extends OncePerRequestFilter {

    /**
     * Настройки TRBAC берём из БД (с fallback на application.properties).
     * Это критично: иначе UI может показывать одно (из БД), а фильтр применять другое (из @Value).
     */
    private final TrbacSettingsService trbacSettingsService;

    @Value("${security.trbac.debug:false}")
    private boolean trbacDebug;

    public TrbacPerRequestFilter(TrbacSettingsService trbacSettingsService) {
        this.trbacSettingsService = trbacSettingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        Authentication original = SecurityContextHolder.getContext().getAuthentication();

        // Пользователь не аутентифицирован — ничего не делаем.
        if (original == null || !original.isAuthenticated() || (original instanceof AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        // TRBAC выключен (по настройкам из БД/фолбэка) — ничего не делаем.
        TrbacSettingsService.SettingsSnapshot s = trbacSettingsService.getSnapshot();
        if (s == null || !s.enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken trbacToken = null;

        try {
            if (isOffHoursNow(s)) {
                String role = normalizeRole(s.offHoursRole);
                List<GrantedAuthority> offAuthorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));

                // Чтобы не плодить объекты, если роль уже ровно одна и совпадает.
                if (!isAlreadyOffHoursRole(original, role)) {
                    UsernamePasswordAuthenticationToken replaced =
                        new UsernamePasswordAuthenticationToken(original.getPrincipal(), original.getCredentials(), offAuthorities);
                    replaced.setDetails(original.getDetails());
                    trbacToken = replaced;
                    SecurityContextHolder.getContext().setAuthentication(replaced);

                    if (trbacDebug) {
                        log.info("TRBAC(per-request): OFF-HOURS {}-{} ({}) uri={} user={} roles_before={} -> roles_after={}",
                            safe(s.workStart, "09:00"), safe(s.workEnd, "18:00"), safe(s.timezone, "Asia/Bishkek"),
                            request.getRequestURI(), safeName(original),
                            safeAuthorities(original),
                            safeAuthorities(replaced));
                    }
                }
            } else {
                if (trbacDebug) {
                    log.debug("TRBAC(per-request): within window {}-{} ({}) uri={} user={} roles={}",
                        safe(s.workStart, "09:00"), safe(s.workEnd, "18:00"), safe(s.timezone, "Asia/Bishkek"),
                        request.getRequestURI(), safeName(original),
                        safeAuthorities(original));
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // Возвращаем исходную аутентификацию, чтобы она не была сохранена в сессии в урезанном виде.
            // Но делаем это ТОЛЬКО если текущий auth всё ещё равен токену, который поставил TRBAC.
            if (trbacToken != null) {
                Authentication current = SecurityContextHolder.getContext().getAuthentication();
                if (current == trbacToken) {
                    SecurityContextHolder.getContext().setAuthentication(original);
                }
            }
        }
    }

    private boolean isAlreadyOffHoursRole(Authentication auth, String offRole) {
        if (auth == null || auth.getAuthorities() == null) return false;
        List<String> roles = auth.getAuthorities().stream()
            .filter(Objects::nonNull)
            .map(GrantedAuthority::getAuthority)
            .filter(Objects::nonNull)
            .map(String::trim)
            .collect(Collectors.toList());
        if (roles.size() != 1) return false;
        String a = roles.get(0);
        return a.equalsIgnoreCase("ROLE_" + offRole);
    }

    private boolean isOffHoursNow(TrbacSettingsService.SettingsSnapshot s) {
        // Страховка от некорректной конфигурации: если что-то сломано — используем значения по умолчанию.
        String tz = safe(s == null ? null : s.timezone, "Asia/Bishkek");
        String ws = safe(s == null ? null : s.workStart, "09:00");
        String we = safe(s == null ? null : s.workEnd, "18:00");

        ZoneId zone;
        try {
            zone = ZoneId.of(tz);
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Bishkek");
        }

        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(ws);
        } catch (Exception e) {
            start = LocalTime.parse("09:00");
        }
        try {
            end = LocalTime.parse(we);
        } catch (Exception e) {
            end = LocalTime.parse("18:00");
        }

        LocalTime now = LocalTime.now(zone);
        return !isWithinWindow(now, start, end);
    }

    private boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) return true; // 24/7

        if (start.isBefore(end)) {
            // обычное окно: 09:00-18:00
            return !now.isBefore(start) && now.isBefore(end);
        }

        // окно через полночь: 22:00-06:00
        return !now.isBefore(start) || now.isBefore(end);
    }

    private String normalizeRole(String role) {
        String r = (role == null) ? "" : role.trim().toUpperCase(Locale.ROOT);
        return r.isBlank() ? "VIEWER" : r;
    }

    private String safe(String v, String fallback) {
        if (v == null) return fallback;
        String s = v.trim();
        return s.isBlank() ? fallback : s;
    }

    private String safeName(Authentication a) {
        try {
            return a.getName();
        } catch (Exception e) {
            return "?";
        }
    }

    private String safeAuthorities(Authentication a) {
        if (a == null || a.getAuthorities() == null) return "[]";
        return a.getAuthorities().stream()
            .filter(Objects::nonNull)
            .map(GrantedAuthority::getAuthority)
            .filter(Objects::nonNull)
            .map(String::trim)
            .collect(Collectors.toList())
            .toString();
    }
}

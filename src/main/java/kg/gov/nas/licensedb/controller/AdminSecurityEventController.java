package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.constant.PaginationConstant;
import kg.gov.nas.licensedb.dto.Pager;
import kg.gov.nas.licensedb.dto.SecurityEventPattern;
import kg.gov.nas.licensedb.dto.SecurityEventSpecification;
import kg.gov.nas.licensedb.entity.SecurityEvent;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.repository.SecurityEventRepository;
import kg.gov.nas.licensedb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Админ-экран: события безопасности.
 *
 * Важно: если таблица ещё не создана (нет прав на DDL) или есть проблемы с БД,
 * не "роняем" страницу полностью — показываем понятное сообщение.
 */
@Controller
@RequestMapping("/admin/security-events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
@Slf4j
public class AdminSecurityEventController {

    private final SecurityEventRepository securityEventRepository;
    private final UserRepository userRepository;

    @GetMapping({"/index", "/index/"})
    public ModelAndView index() {
        SecurityEventPattern pattern = new SecurityEventPattern();
        return getModelViewSafe(pattern, "admin/security/securityEventList", null);
    }

    /**
     * ВАЖНО:
     * - AJAX (app.js -> search()) -> partial securityEventResult
     * - обычный POST -> полноценная страница securityEventList
     */
    @PostMapping({"/search", "/search/"})
    public ModelAndView search(@ModelAttribute SecurityEventPattern pattern, HttpServletRequest request) {
        boolean isAjax = request != null && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        return getModelViewSafe(pattern, isAjax ? "admin/security/securityEventResult" : "admin/security/securityEventList", request);
    }

    private ModelAndView getModelViewSafe(SecurityEventPattern pattern, String view, HttpServletRequest request) {
        try {
            return getModelView(pattern, view);
        } catch (Exception e) {
            // На практике чаще всего это: таблица app_security_event ещё не создана на БД,
            // либо у пользователя БД нет прав на CREATE/ALTER.
            log.error("SecurityEvents UI: failed to render. uri={} msg={}",
                request == null ? "/admin/security-events/index/" : request.getRequestURI(), e.toString(), e);

            ModelAndView mv = new ModelAndView(view);

            int pageSize = (pattern == null || pattern.getPageSize() == null)
                ? PaginationConstant.INITIAL_PAGE_SIZE
                : pattern.getPageSize();

            // Пустая страница результатов (чтобы шаблоны отрисовались)
            Page<SecurityEvent> empty = Page.empty(PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "eventMs")));
            Pager pager = new Pager(0, 0, PaginationConstant.BUTTONS_TO_SHOW);

            List<String> users = safeUsers();
            List<String> actions = Collections.emptyList();

            mv.addObject("items", empty);
            mv.addObject("selectedPageSize", pageSize);
            mv.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
            mv.addObject("pager", pager);
            mv.addObject("pattern", pattern == null ? new SecurityEventPattern() : pattern);
            mv.addObject("users", users);
            mv.addObject("actions", actions);

            mv.addObject("error",
                "Не удалось открыть \"События безопасности\". Возможная причина: таблица app_security_event не создана " +
                "(нет прав CREATE/ALTER) или ошибка БД. Проверь логи сервера и права пользователя БД.");

            return mv;
        }
    }

    private ModelAndView getModelView(SecurityEventPattern pattern, String view) {
        ModelAndView mv = new ModelAndView(view);

        int pageSize = pattern.getPageSize() == null ? PaginationConstant.INITIAL_PAGE_SIZE : pattern.getPageSize();
        int page = (pattern.getPage() == null || pattern.getPage() < 1) ? PaginationConstant.INITIAL_PAGE : pattern.getPage() - 1;

        Long fromMs = parseFromDateTime(pattern.getFromDateTime());
        Long toMs = parseToDateTime(pattern.getToDateTime());

        Specification<SecurityEvent> spec = Specification.where(new SecurityEventSpecification(pattern));
        if (fromMs != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("eventMs"), fromMs));
        }
        if (toMs != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("eventMs"), toMs));
        }

        Page<SecurityEvent> items = securityEventRepository.findAll(
            spec,
            PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "eventMs").and(Sort.by(Sort.Direction.DESC, "id")))
        );

        Pager pager = new Pager(items.getTotalPages(), items.getNumber(), PaginationConstant.BUTTONS_TO_SHOW);

        List<String> users = safeUsers();
        List<String> actions = securityEventRepository.findDistinctActions();

        mv.addObject("items", items);
        mv.addObject("selectedPageSize", pageSize);
        mv.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
        mv.addObject("pager", pager);
        mv.addObject("pattern", pattern);
        mv.addObject("users", users);
        mv.addObject("actions", actions);

        return mv;
    }

    private List<String> safeUsers() {
        try {
            return userRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
                .filter(Objects::nonNull)
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .map(User::getUsername)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private Long parseFromDateTime(String s) {
        LocalDateTime dt = parseLocalDateTime(s);
        if (dt == null) return null;
        ZoneId zone = ZoneId.systemDefault();
        return dt.atZone(zone).toInstant().toEpochMilli();
    }

    private Long parseToDateTime(String s) {
        LocalDateTime dt = parseLocalDateTime(s);
        if (dt == null) return null;

        ZoneId zone = ZoneId.systemDefault();
        long startOfMinute = dt.atZone(zone).toInstant().toEpochMilli();
        return startOfMinute + (60_000L - 1L);
    }

    private LocalDateTime parseLocalDateTime(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        try {
            return LocalDateTime.parse(t, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                return LocalDateTime.parse(t, f);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}

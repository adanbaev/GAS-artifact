package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.entity.SecurityEvent;
import kg.gov.nas.licensedb.repository.SecurityEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;

/**
 * Логирование событий безопасности.
 *
 * ВАЖНО: сюда не передавать и не сохранять пароли/секреты.
 */
@Service
@RequiredArgsConstructor
public class SecurityEventService {

    public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ACTION_PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String ACTION_PASSWORD_CHANGED_FORCED = "PASSWORD_CHANGED_FORCED";

    private final SecurityEventRepository repository;

    public void logPasswordReset(String actorUsername, String subjectUsername, HttpServletRequest request) {
        safeLog(ACTION_PASSWORD_RESET, actorUsername, subjectUsername, request, null);
    }

    public void logPasswordChanged(String actorUsername, HttpServletRequest request, boolean forced) {
        safeLog(forced ? ACTION_PASSWORD_CHANGED_FORCED : ACTION_PASSWORD_CHANGED,
            actorUsername, actorUsername, request,
            forced ? "forced=true" : "forced=false");
    }

    public void safeLog(String action,
                        String actorUsername,
                        String subjectUsername,
                        HttpServletRequest request,
                        String details) {
        try {
            SecurityEvent e = SecurityEvent.builder()
                .eventMs(System.currentTimeMillis())
                .action(trimTo(action, 64))
                .actorUsername(trimTo(actorUsername, 64))
                .subjectUsername(trimTo(subjectUsername, 64))
                .ip(trimTo(resolveClientIp(request), 45))
                .userAgent(trimTo(resolveUserAgent(request), 255))
                .details(trimTo(details, 512))
                .build();
            repository.save(e);
        } catch (Exception ignored) {
            // Безопасность важнее логов: событие не должно ломать бизнес-операцию.
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            // Берём первый адрес (клиент), остальное — цепочка прокси
            String first = xff.split(",")[0];
            return first == null ? null : first.trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        return ua == null ? null : ua.trim();
    }

    private String trimTo(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max);
    }
}

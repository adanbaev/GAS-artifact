package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dto.TrbacUiInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Формирует информацию для UI-баннера TRBAC.
 *
 * Важно: этот сервис НЕ меняет права доступа, он только отображает статус.
 * Сами ограничения применяются в фильтре TrbacPerRequestFilter.
 */
@Service
@RequiredArgsConstructor
public class TrbacUiInfoService {

    /**
     * Источник истины по TRBAC — настройки из БД (с fallback на application.properties).
     * Иначе баннер может показывать одно, а фактическое ограничение — другое.
     */
    private final TrbacSettingsService trbacSettingsService;

    /** Можно отключить баннер, если не нужен. */
    @Value("${security.trbac.ui-banner:true}")
    private boolean uiBannerEnabled;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public TrbacUiInfo build(boolean authenticated) {
        TrbacSettingsService.SettingsSnapshot s = trbacSettingsService.getSnapshot();
        boolean enabled = (s != null) && s.enabled;

        // На неаутентифицированных страницах (например login) баннер не нужен.
        if (!authenticated) {
            return TrbacUiInfo.builder()
                .showBanner(false)
                .enabled(enabled)
                .within(true)
                .timezone(safe(s == null ? null : s.timezone, "Asia/Bishkek"))
                .offHoursRole(normalizeRole(s == null ? null : s.offHoursRole))
                .build();
        }

        ZoneId zone = safeZoneId(s == null ? null : s.timezone);
        LocalDateTime nowDt = LocalDateTime.now(zone);
        LocalTime now = nowDt.toLocalTime();

        LocalTime start = safeTime(s == null ? null : s.workStart, "09:00");
        LocalTime end = safeTime(s == null ? null : s.workEnd, "18:00");

        boolean within = !enabled || isWithinWindow(now, start, end);

        String windowText = start.equals(end)
            ? "24/7"
            : formatHm(start) + "–" + formatHm(end);

        String nowText = DT_FMT.format(nowDt);

        boolean showBanner = uiBannerEnabled && enabled && !within;

        return TrbacUiInfo.builder()
            .showBanner(showBanner)
            .enabled(enabled)
            .within(within)
            .nowText(nowText)
            .windowText(windowText)
            .timezone(zone.getId())
            .offHoursRole(normalizeRole(s == null ? null : s.offHoursRole))
            .build();
    }

    private ZoneId safeZoneId(String tz) {
        String v = safe(tz, "Asia/Bishkek");
        try {
            return ZoneId.of(v);
        } catch (Exception e) {
            return ZoneId.of("Asia/Bishkek");
        }
    }

    private LocalTime safeTime(String value, String fallback) {
        String v = safe(value, fallback);
        try {
            return LocalTime.parse(v);
        } catch (Exception e) {
            return LocalTime.parse(fallback);
        }
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

    private String formatHm(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
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
}

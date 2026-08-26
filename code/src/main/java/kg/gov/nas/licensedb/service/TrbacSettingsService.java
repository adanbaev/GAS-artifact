package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dto.TrbacSettingsForm;
import kg.gov.nas.licensedb.entity.TrbacSettingsEntity;
import kg.gov.nas.licensedb.repository.TrbacSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Locale;
import java.util.Optional;

/**
 * TRBAC-настройки из БД с fallback на application.properties.
 *
 * Примечание:
 * Сейчас TRBAC применяется на этапе аутентификации (MyUserDetailsService).
 * Поэтому изменения через UI начинают действовать при следующем входе пользователя.
 * (Если нужно "на лету" — позже добавим фильтр, который переоценивает роли на каждый запрос.)
 */
@Service
@RequiredArgsConstructor
public class TrbacSettingsService {

    private static final long SINGLETON_ID = 1L;

    private final TrbacSettingsRepository trbacSettingsRepository;

    // Значения по умолчанию (как раньше брались из application.properties)
    @Value("${security.trbac.work-start:09:00}")
    private String defaultWorkStart;

    @Value("${security.trbac.work-end:18:00}")
    private String defaultWorkEnd;

    @Value("${security.trbac.timezone:Asia/Bishkek}")
    private String defaultTimeZone;

    @Value("${security.trbac.offhours-role:VIEWER}")
    private String defaultOffHoursRole;

    @Value("${security.trbac.enabled:true}")
    private boolean defaultEnabled;

    private volatile Cached cached;

    private static final class Cached {
        final long loadedAtMs;
        final SettingsSnapshot snapshot;

        Cached(long loadedAtMs, SettingsSnapshot snapshot) {
            this.loadedAtMs = loadedAtMs;
            this.snapshot = snapshot;
        }
    }

    /** Снимок настроек + метаданные (кто/когда). */
    public static final class SettingsSnapshot {
        public final boolean enabled;
        public final String workStart;
        public final String workEnd;
        public final String timezone;
        public final String offHoursRole;
        public final Long updatedAtMs;
        public final String updatedBy;

        public SettingsSnapshot(boolean enabled, String workStart, String workEnd, String timezone, String offHoursRole,
                                Long updatedAtMs, String updatedBy) {
            this.enabled = enabled;
            this.workStart = workStart;
            this.workEnd = workEnd;
            this.timezone = timezone;
            this.offHoursRole = offHoursRole;
            this.updatedAtMs = updatedAtMs;
            this.updatedBy = updatedBy;
        }
    }

    /** Получить актуальные TRBAC-настройки (с коротким кешированием). */
    public SettingsSnapshot getSnapshot() {
        long now = System.currentTimeMillis();
        Cached c = cached;
        if (c != null && (now - c.loadedAtMs) < 3000L) {
            return c.snapshot;
        }

        SettingsSnapshot snap = loadFromDbOrDefault();
        cached = new Cached(now, snap);
        return snap;
    }

    /** Для формы UI. */
    public TrbacSettingsForm getForm() {
        SettingsSnapshot s = getSnapshot();
        return TrbacSettingsForm.builder()
            .enabled(s.enabled)
            .workStart(s.workStart)
            .workEnd(s.workEnd)
            .timezone(s.timezone)
            .offHoursRole(s.offHoursRole)
            .build();
    }

    /**
     * Сохранить настройки из формы.
     *
     * @param form  значения из UI
     * @param actor username администратора (для аудита)
     */
    public void saveFromForm(TrbacSettingsForm form, String actor) {
        if (form == null) {
            throw new IllegalArgumentException("Пустая форма настроек TRBAC");
        }

        // Валидация и нормализация
        boolean enabled = form.getEnabled() == null ? defaultEnabled : form.getEnabled();

        String workStart = normalizeTime(form.getWorkStart(), defaultWorkStart, "Начало рабочего времени");
        String workEnd = normalizeTime(form.getWorkEnd(), defaultWorkEnd, "Окончание рабочего времени");

        String timezone = normalizeTimezone(form.getTimezone(), defaultTimeZone);
        String offRole = normalizeRole(form.getOffHoursRole(), defaultOffHoursRole);

        TrbacSettingsEntity e = trbacSettingsRepository.findById(SINGLETON_ID).orElseGet(() -> {
            TrbacSettingsEntity ne = new TrbacSettingsEntity();
            ne.setId(SINGLETON_ID);
            return ne;
        });

        e.setEnabled(enabled);
        e.setWorkStart(workStart);
        e.setWorkEnd(workEnd);
        e.setTimezone(timezone);
        e.setOffHoursRole(offRole);
        e.setUpdatedAtMs(System.currentTimeMillis());
        e.setUpdatedBy((actor == null || actor.isBlank()) ? "SYSTEM" : actor);

        trbacSettingsRepository.save(e);
        // Сразу сбрасываем кеш, чтобы UI и логика видели изменения
        cached = null;
    }

    /**
     * Текст статуса для UI (рабочее/вне окна).
     */
    public String buildStatusText() {
        SettingsSnapshot s = getSnapshot();
        if (!s.enabled) {
            return "Отключён";
        }
        ZoneId zone = ZoneId.of(s.timezone);
        LocalTime now = LocalTime.now(zone);
        LocalTime start = LocalTime.parse(s.workStart);
        LocalTime end = LocalTime.parse(s.workEnd);
        boolean within = isWithinWindow(now, start, end);
        String win = s.workStart + "–" + s.workEnd + " (" + s.timezone + ")";
        if (within) {
            return "Рабочее время: " + win;
        }
        return "Вне рабочего времени: " + win + " — права ограничены";
    }

    public boolean isWithinNow() {
        SettingsSnapshot s = getSnapshot();
        if (!s.enabled) return true;
        ZoneId zone = ZoneId.of(s.timezone);
        LocalTime now = LocalTime.now(zone);
        LocalTime start = LocalTime.parse(s.workStart);
        LocalTime end = LocalTime.parse(s.workEnd);
        return isWithinWindow(now, start, end);
    }

    // ----------------------------
    // internals
    // ----------------------------

    private SettingsSnapshot loadFromDbOrDefault() {
        Optional<TrbacSettingsEntity> opt = trbacSettingsRepository.findById(SINGLETON_ID);
        if (opt.isEmpty()) {
            return new SettingsSnapshot(
                defaultEnabled,
                safe(defaultWorkStart, "09:00"),
                safe(defaultWorkEnd, "18:00"),
                safe(defaultTimeZone, "Asia/Bishkek"),
                safe(defaultOffHoursRole, "VIEWER").trim().toUpperCase(Locale.ROOT),
                null,
                null
            );
        }

        TrbacSettingsEntity e = opt.get();
        boolean enabled = e.getEnabled() == null ? defaultEnabled : e.getEnabled();
        String workStart = safe(e.getWorkStart(), defaultWorkStart);
        String workEnd = safe(e.getWorkEnd(), defaultWorkEnd);
        String tz = safe(e.getTimezone(), defaultTimeZone);
        String offRole = safe(e.getOffHoursRole(), defaultOffHoursRole).trim().toUpperCase(Locale.ROOT);

        // Доп. страховка: если вдруг кто-то руками в БД написал мусор
        try { LocalTime.parse(workStart); } catch (Exception ex) { workStart = safe(defaultWorkStart, "09:00"); }
        try { LocalTime.parse(workEnd); } catch (Exception ex) { workEnd = safe(defaultWorkEnd, "18:00"); }
        try { ZoneId.of(tz); } catch (Exception ex) { tz = safe(defaultTimeZone, "Asia/Bishkek"); }
        if (offRole.isBlank()) offRole = safe(defaultOffHoursRole, "VIEWER").trim().toUpperCase(Locale.ROOT);

        return new SettingsSnapshot(
            enabled,
            workStart,
            workEnd,
            tz,
            offRole,
            e.getUpdatedAtMs(),
            e.getUpdatedBy()
        );
    }

    private String normalizeTime(String value, String fallback, String label) {
        String v = (value == null) ? "" : value.trim();
        if (v.isBlank()) v = safe(fallback, "");
        try {
            LocalTime.parse(v);
        } catch (Exception e) {
            throw new IllegalArgumentException(label + ": неверный формат времени (нужно HH:mm)");
        }
        // Приводим к HH:mm (LocalTime.toString может вернуть HH:mm:ss)
        LocalTime t = LocalTime.parse(v);
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
    }

    private String normalizeTimezone(String value, String fallback) {
        String tz = (value == null) ? "" : value.trim();
        if (tz.isBlank()) tz = safe(fallback, "Asia/Bishkek");
        try {
            ZoneId.of(tz);
        } catch (Exception e) {
            throw new IllegalArgumentException("Таймзона указана неверно: " + tz);
        }
        return tz;
    }

    private String normalizeRole(String value, String fallback) {
        String r = (value == null) ? "" : value.trim();
        if (r.isBlank()) r = safe(fallback, "VIEWER");
        r = r.trim().toUpperCase(Locale.ROOT);
        // Минимальная проверка, чтобы не было пробелов/запятых и т.п.
        if (!r.matches("[A-Z0-9_]{2,32}")) {
            throw new IllegalArgumentException("Роль вне рабочего времени указана неверно: " + r);
        }
        return r;
    }

    private String safe(String a, String b) {
        return (a == null || a.isBlank()) ? b : a;
    }

    private boolean isWithinWindow(LocalTime now, LocalTime start, LocalTime end) {
        if (start.equals(end)) return true; // 24/7
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        // окно через полночь
        return !now.isBefore(start) || now.isBefore(end);
    }
}
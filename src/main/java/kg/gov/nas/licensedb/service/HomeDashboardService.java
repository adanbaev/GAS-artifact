package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.IntegrityIncidentDao;
import kg.gov.nas.licensedb.dao.IntegrityLogDao;
import kg.gov.nas.licensedb.dto.FreqPattern;
import kg.gov.nas.licensedb.dto.HomeDashboardView;
import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.entity.UserFavorite;
import kg.gov.nas.licensedb.entity.UserFavoriteFreqId;
import kg.gov.nas.licensedb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис подготовки данных для вкладки "Главная".
 */
@Service
@RequiredArgsConstructor
public class HomeDashboardService {

    private final IntegrityLogDao integrityLogDao;
    private final IntegrityIncidentDao incidentDao;
    private final UserRepository userRepository;
    private final UserFavoriteService favoriteService;
    private final UserFavoriteFreqService favoriteFreqService;

    private final TrbacSettingsService trbacSettingsService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    public HomeDashboardView build() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String username = (a == null || a.getName() == null) ? "SYSTEM" : a.getName();

        TrbacSettingsService.SettingsSnapshot trbac = trbacSettingsService.getSnapshot();

        // Эффективные роли (учитывают TRBAC, т.к. применяются в MyUserDetailsService)
        Set<String> effectiveRoles = extractRoleNames(a);
        boolean effectiveAdminOrAuditor = effectiveRoles.contains("ADMIN") || effectiveRoles.contains("AUDITOR");

        // Назначенные роли (как в БД) — показываем их пользователю, чтобы было понятно,
        // какие роли у него есть в принципе.
        Set<String> assignedRoles = loadAssignedRoles(username);

        String source = pickLogSource();
        String sourceText = "LOG".equalsIgnoreCase(source)
            ? "STRICT (freq_integrity_log)"
            : "CHECKPOINT (freq_integrity_event)";

        long now = System.currentTimeMillis();
        long from24h = now - 24L * 60L * 60L * 1000L;

        // Для обычного пользователя показываем только его активность
        String actorFilter = effectiveAdminOrAuditor ? null : username;

        long insert24h = integrityLogDao.countLogs(source, actorFilter, "INSERT", null, from24h, now);
        long update24h = integrityLogDao.countLogs(source, actorFilter, "UPDATE", null, from24h, now);

        long openIncidents = incidentDao.count("OPEN", null, null, null, null);
        long openIncidents24h = incidentDao.count("OPEN", null, null, from24h, now);

        // ВАЖНО: на главной нужно показывать "ID владельца" (owner.ID), а не технический freq.ID.
        // В DAO ownerId уже подтягивается через join (freq -> site), но раньше мы его НЕ прокидывали в DTO.
        List<HomeDashboardView.LogRow> recent = integrityLogDao.searchLogs(source, actorFilter, null, null, null, null, 20, 0)
            .stream()
            .map(e -> HomeDashboardView.LogRow.builder()
                .timeText(formatMs(e.getEventMs()))
                .actor(e.getActorUsername())
                .action(e.getAction())
                .ownerId(e.getOwnerId())   // <-- FIX: прокидываем ownerId
                .freqId(e.getFreqId())
                .build())
            .collect(Collectors.toList());

        List<HomeDashboardView.IncidentRow> openIncidentRows = new ArrayList<>();
        if (effectiveAdminOrAuditor) {
            openIncidentRows = incidentDao.search("OPEN", null, null, null, null, 10, 0)
                .stream()
                .map(i -> HomeDashboardView.IncidentRow.builder()
                    .id(i.getId())
                    .timeText(formatMs(i.getCreatedAtMs()))
                    .type(i.getIncidentType())
                    .ownerId(i.getOwnerId())  // <-- FIX: прокидываем ownerId
                    .freqId(i.getFreqId())
                    .detectedBy(i.getDetectedBy())
                    .build())
                .collect(Collectors.toList());
        }

        // TRBAC: какие операции сейчас заблокированы
        List<String> blockedOps = buildTrbacBlockedOperations(assignedRoles, trbac);

        // Избранные запросы пользователя
        List<HomeDashboardView.FavoriteRow> favorites = favoriteService.getTop10ForCurrentUser().stream()
            .map(this::toFavoriteRow)
            .collect(Collectors.toList());

        // Избранные Freq ID пользователя
        List<HomeDashboardView.FavoriteFreqRow> favoriteFreqIds = favoriteFreqService.getTop20ForCurrentUser().stream()
            .map(this::toFavoriteFreqRow)
            .collect(Collectors.toList());

        return HomeDashboardView.builder()
            .username(username)
            .rolesText(String.join(", ", assignedRoles))
            .adminOrAuditor(effectiveAdminOrAuditor)
            .trbacEnabled(trbac.enabled)
            .trbacStatusText(trbacSettingsService.buildStatusText())
            .trbacBlockedOperations(blockedOps)
            .logSource(source)
            .logSourceText(sourceText)
            .scopeText(effectiveAdminOrAuditor ? "Всего" : "Мои")
            .insertCount24h(insert24h)
            .updateCount24h(update24h)
            .openIncidents(openIncidents)
            .openIncidents24h(openIncidents24h)
            .recentActions(recent)
            .openIncidentRows(openIncidentRows)
            .quickPattern(buildQuickPattern())
            .favorites(favorites)
            .favoriteFreqIds(favoriteFreqIds)
            .build();
    }

    private HomeDashboardView.FavoriteRow toFavoriteRow(UserFavorite f) {
        return HomeDashboardView.FavoriteRow.builder()
            .id(f.getId())
            .title(f.getTitle())
            .ownerId(f.getOwnerId())
            .licNumber(f.getLicNumber())
            .nominal(f.getNominal())
            .build();
    }

    private HomeDashboardView.FavoriteFreqRow toFavoriteFreqRow(UserFavoriteFreqId f) {
        return HomeDashboardView.FavoriteFreqRow.builder()
            .id(f.getId())
            .freqId(f.getFreqId())
            .title(f.getTitle())
            .build();
    }

    private FreqPattern buildQuickPattern() {
        FreqPattern p = new FreqPattern();
        p.setPage(1);
        p.setPageSize(20);
        return p;
    }

    private Set<String> extractRoleNames(Authentication a) {
        if (a == null || a.getAuthorities() == null) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>();
        for (GrantedAuthority ga : a.getAuthorities()) {
            if (ga == null || ga.getAuthority() == null) continue;
            String s = ga.getAuthority().trim();
            if (s.startsWith("ROLE_")) s = s.substring("ROLE_".length());
            out.add(s);
        }
        return out;
    }

    private Set<String> loadAssignedRoles(String username) {
        if (username == null || username.isBlank()) return Collections.emptySet();
        User u = userRepository.findByUsername(username);
        if (u == null || u.getRoles() == null) return Collections.emptySet();
        return u.getRoles().stream()
            .filter(Objects::nonNull)
            .map(Role::getName)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Если STRICT-логов нет (включен режим CHECKPOINT), переключаемся на EVENT.
     */
    private String pickLogSource() {
        long logCnt = integrityLogDao.countLogs("LOG", null, null, null, null, null);
        if (logCnt > 0) return "LOG";

        long eventCnt = integrityLogDao.countLogs("EVENT", null, null, null, null, null);
        if (eventCnt > 0) return "EVENT";

        return "LOG";
    }

    private String formatMs(long epochMs) {
        TrbacSettingsService.SettingsSnapshot trbac = trbacSettingsService.getSnapshot();
        ZoneId zone = ZoneId.of(trbac.timezone);
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone);
        return DT_FMT.format(dt);
    }

    /**
     * Список операций, которые блокируются TRBAC сейчас.
     *
     * В текущей реализации TRBAC (MyUserDetailsService) вне рабочего окна роли пользователя
     * заменяются на одну роль (security.trbac.offhours-role, по умолчанию VIEWER).
     */
    private List<String> buildTrbacBlockedOperations(Set<String> assignedRoles, TrbacSettingsService.SettingsSnapshot trbac) {
        if (trbac == null || !trbac.enabled) {
            return Collections.emptyList();
        }

        ZoneId zone = ZoneId.of(trbac.timezone);
        LocalTime now = LocalTime.now(zone);
        LocalTime start = LocalTime.parse(trbac.workStart);
        LocalTime end = LocalTime.parse(trbac.workEnd);

        boolean within = isWithinWindow(now, start, end);
        if (within) {
            return Collections.emptyList();
        }

        Set<String> baseAllowed = allowedOperationsForRoles(assignedRoles);
        Set<String> offAllowed = allowedOperationsForRoles(
            Collections.singleton(((trbac.offHoursRole == null) ? "VIEWER" : trbac.offHoursRole.trim().toUpperCase()))
        );

        // Заблокировано = то, что было бы разрешено при назначенных ролях, но не разрешено при off-hours роли.
        baseAllowed.removeAll(offAllowed);

        List<String> out = new ArrayList<>(baseAllowed);
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    private Set<String> allowedOperationsForRoles(Set<String> roles) {
        Set<String> r = (roles == null) ? Collections.emptySet() : roles;
        Set<String> ops = new LinkedHashSet<>();

        // Просмотр/поиск — всегда (не показываем как блокируемый).

        if (hasAny(r, "ADMIN", "OPERATOR")) {
            ops.add("Создание/редактирование записей");
        }
        if (hasAny(r, "ADMIN", "OPERATOR", "PRINTER")) {
            ops.add("Печать");
        }
        if (hasAny(r, "ADMIN", "OPERATOR", "PRINTER", "AUDITOR")) {
            ops.add("Экспорт (выгрузка)");
        }
        if (hasAny(r, "ADMIN", "AUDITOR")) {
            ops.add("Логи и инциденты");
        }
        if (hasAny(r, "ADMIN")) {
            ops.add("Управление пользователями");
        }

        return ops;
    }

    private boolean hasAny(Set<String> roles, String... any) {
        if (roles == null || roles.isEmpty()) return false;
        for (String a : any) {
            if (roles.contains(a)) return true;
        }
        return false;
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

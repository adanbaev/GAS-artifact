package kg.gov.nas.licensedb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель для вкладки "Главная" (рабочая панель).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeDashboardView {

    /** Текущий пользователь */
    private String username;

    /** Роли (как строка для отображения) */
    private String rolesText;

    /** Админ или аудитор (может видеть логи/инциденты) */
    private boolean adminOrAuditor;

    /** TRBAC */
    private boolean trbacEnabled;
    private String trbacStatusText;

    /** TRBAC: что сейчас заблокировано (если вне окна и ограничения включены) */
    @Builder.Default
    private List<String> trbacBlockedOperations = new ArrayList<>();

    /** Источник журнала для выборки: LOG/EVENT */
    private String logSource;
    private String logSourceText;

    /** Сводка (за 24 часа) */
    private String scopeText; // "Всего" или "Мои"
    private long insertCount24h;
    private long updateCount24h;

    /** Инциденты */
    private long openIncidents;
    private long openIncidents24h;

    /** Последние действия (таблица) */
    @Builder.Default
    private List<LogRow> recentActions = new ArrayList<>();

    /** Открытые инциденты (только для admin/auditor) */
    @Builder.Default
    private List<IncidentRow> openIncidentRows = new ArrayList<>();

    /** Быстрый поиск */
    private FreqPattern quickPattern;

    /** Избранные (частые) запросы текущего пользователя */
    @Builder.Default
    private List<FavoriteRow> favorites = new ArrayList<>();

    /**
     * Избранные ID для печати/частого доступа.
     * ВАЖНО: несмотря на историческое имя "Freq", в UI мы показываем именно "ID" (owner.ID),
     * а при отсутствии ownerId можно показывать fallback на freqId.
     */
    @Builder.Default
    private List<FavoriteFreqRow> favoriteFreqIds = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogRow {
        private String timeText;
        private String actor;
        private String action;

        /** ID владельца (owner.ID) */
        private Long ownerId;

        /** Технический ID записи (freq.ID) — fallback */
        private Long freqId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncidentRow {
        private Long id;
        private String timeText;
        private String type;

        /** ID владельца (owner.ID) */
        private Long ownerId;

        /** Технический ID записи (freq.ID) — fallback */
        private Long freqId;

        private String detectedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoriteRow {
        private Long id;
        private String title;
        private Long ownerId;
        private Integer licNumber;
        private String nominal;
    }

    /**
     * ВОТ ЭТОГО КЛАССА НЕ ХВАТАЛО -> из-за этого и была ошибка компиляции.
     * Используется как строка в списке "избранных ID" на главной.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoriteFreqRow {
        private Long id;

        /** Как показать пользователю */
        private String title;

        /** Привычный ID (owner.ID) */
        private Long ownerId;

        /** fallback, если ownerId получить нельзя */
        private Long freqId;
    }
}

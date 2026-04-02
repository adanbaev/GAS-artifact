package kg.gov.nas.licensedb.dto;

import lombok.Data;

/**
 * Паттерн фильтрации событий безопасности.
 */
@Data
public class SecurityEventPattern extends BasePattern {

    /** Кто выполнил действие. */
    private String actorUsername;

    /** Над кем выполнено действие. */
    private String subjectUsername;

    /** Тип события. */
    private String action;

    /** IP. */
    private String ip;

    /** Период (локальное время сервера), формат input datetime-local. */
    private String fromDateTime;
    private String toDateTime;
}

package kg.gov.nas.licensedb.dto;

import lombok.Data;

@Data
public class IntegrityIncidentPattern extends BasePattern {
    private String status; // OPEN / RESOLVED / (пусто = все)
    private String incidentType; // DATA_MISMATCH / SIGNATURE_MISMATCH / (пусто = все)

    /**
     * ID владельца (owner.ID).
     * Если задан, фильтруем инциденты по всем freq.ID владельца.
     */
    private Long ownerId;

    /**
     * Технический идентификатор записи (freq.ID).
     * Поле оставлено для совместимости, но в UI можно не показывать.
     */
    private Long freqId;

    private String fromDateTime;
    private String toDateTime;
}

package kg.gov.nas.licensedb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntegrityIncident {
    private Long id;

    private long createdAtMs;
    private String detectedBy;

    private String incidentType;

    /**
     * ID владельца (owner.ID).
     * Получаем через join integrity_incident.freq_id -> freq -> site.
     */
    private Long ownerId;

    private Long freqId;
    private Long lastLogId;

    private String expectedHash;
    private String actualHash;

    private String status;
    private String comment;

    private Long resolvedAtMs;
    private String resolvedBy;
}

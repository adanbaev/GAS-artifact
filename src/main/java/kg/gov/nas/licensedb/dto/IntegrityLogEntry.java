package kg.gov.nas.licensedb.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntegrityLogEntry {
    private Long id;
    private long eventMs;
    private String actorUsername;
    private String action;

    /**
     * ID владельца (owner.ID).
     * В журналах хранится freq_id (freq.ID), поэтому ownerId получаем через join freq -> site.
     */
    private Long ownerId;

    private Long freqId;
    private String dataHash;
    private String prevHash;
    private String chainHash;
}

package kg.gov.nas.licensedb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class IntegrityCheckReport {
    private boolean ok;
    private long checkedAtMs;

    private int logEntriesChecked;
    private int chainIssues;
    private int dataIssues;

    /**
     * Для проверки конкретной записи/владельца заполняются дополнительные поля:
     * - ownerId (ID владельца)
     * - freqId (технический ID записи), lastLogId, expectedHash, actualHash, source
     */
    private Long ownerId;

    /**
     * Технический ID записи (freq.ID).
     * В UI можно не показывать, но он нужен для фиксации инцидента (по конкретной записи).
     */
    private Long freqId;

    /**
     * Откуда взята "последняя известная" запись:
     * LOG   -> freq_integrity_log
     * EVENT -> freq_integrity_event
     */
    private String source;

    private Long lastLogId;
    private Long prevLogId;
    private String expectedHash;
    private String actualHash;

    private List<String> issues;
}

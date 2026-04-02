package kg.gov.nas.licensedb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegrityHashDebugReport {

    private boolean ok;
    private long checkedAtMs;

    private long freqId;

    /**
     * Откуда берём эталон: LOG или EVENT.
     */
    private String source;

    /**
     * ID последней записи журнала, из которой взят expectedHash.
     */
    private Long lastLogId;

    /**
     * ID предыдущей записи журнала (вторая по свежести) для отображения: было -> стало.
     */
    private Long prevLogId;

    private String expectedHash;
    private String actualHash;

    /**
     * Каноническая строка, которая реально хешируется (SHA-256).
     */
    private String canonicalString;

    /**
     * Те же данные, но разложенные по полям (в фиксированном порядке).
     */
    private Map<String, String> canonicalFields;

    /**
     * Текст ошибки, если что-то не удалось получить.
     */
    private String error;
}

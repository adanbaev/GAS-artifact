package kg.gov.nas.licensedb.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of internal CHECKPOINT evidence verification.
 *
 * IMPORTANT:
 * A successful report establishes consistency of the retained checkpoint
 * evidence in the current database state. It does not independently prove
 * completeness or freshness against whole-database rollback/suffix truncation;
 * those cases require an expected anchor retained outside the writable DB path.
 */
@Data
@Builder
public class CheckpointVerificationReport {
    private boolean ok;
    private long checkedAtMs;

    private int checkpointsChecked;
    private long eventsChecked;
    private int issuesCount;

    private String retainedLastCheckpointHash;
    private long retainedNextBatchNo;

    private List<String> issues;
}

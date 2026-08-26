package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dto.CheckpointVerificationReport;
import kg.gov.nas.licensedb.dto.FreqView;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration validation for finalized CHECKPOINT verification.
 *
 * IMPORTANT:
 * - Uses the configured database and therefore runs only when RUN_DB_IT=true.
 * - Registry tables are MyISAM in the evaluated environment, so test cleanup is
 *   explicit rather than transaction-rollback based.
 * - The test verifies retained same-database checkpoint consistency. It does NOT
 *   claim independent detection of a coordinated whole-database rollback or
 *   complete suffix truncation; those cases require an external expected anchor.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
public class CheckpointVerificationIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private FreqDao freqDao;
    @Autowired private FreqCrudService freqCrudService;
    @Autowired private IntegrityService integrityService;

    private Long freqId;
    private Double originalNominal;

    @BeforeEach
    void setUp() throws Exception {
        assertTableExists("freq_integrity_event");
        assertTableExists("integrity_checkpoint");
        assertTableExists("integrity_checkpoint_state");

        resetCheckpointEvidence();

        integrityService.setEnabled(true);
        integrityService.setStrategy(IntegrityService.Strategy.CHECKPOINT);
        integrityService.setCheckpointBatchSize(5);
        freqCrudService.setSignatureEnabled(false);

        freqId = pickSafeFreqId();
        assumeTrue(freqId != null, "No suitable freq record found; test skipped.");

        FreqView current = freqDao.getById(freqId);
        assertNotNull(current);
        assertNotNull(current.getFreqModel());

        originalNominal = current.getFreqModel().getNominal();
        assumeTrue(originalNominal != null, "Original nominal is null; test skipped.");
    }

    @AfterEach
    void tearDown() {
        // Stop integrity logging before restoring the registry value.
        try { integrityService.setEnabled(false); } catch (Exception ignored) {}

        try {
            if (freqId != null && originalNominal != null) {
                FreqView current = freqDao.getById(freqId);
                current.getFreqModel().setNominal(originalNominal);
                freqCrudService.updateFreqOnly(current);
            }
        } catch (Exception ignored) {}

        try { resetCheckpointEvidence(); } catch (Exception ignored) {}
    }

    @Test
    void finalizedTwoBatchCheckpointChain_verifiesSuccessfully() throws Exception {
        doUpdates(10, 0.001);

        CheckpointVerificationReport report =
                integrityService.verifyCheckpointChain();

        assertTrue(report.isOk(), () -> "Expected valid checkpoint chain, issues=" + report.getIssues());
        assertEquals(2, report.getCheckpointsChecked());
        assertEquals(10L, report.getEventsChecked());
        assertEquals(0, report.getIssuesCount());
        assertEquals(3L, report.getRetainedNextBatchNo());
        assertNotNull(report.getRetainedLastCheckpointHash());
        assertEquals(64, report.getRetainedLastCheckpointHash().length());
    }

    @Test
    void directModificationOfPersistedCheckpointEvent_isDetected() throws Exception {
        doUpdates(5, 0.001);

        CheckpointVerificationReport before =
                integrityService.verifyCheckpointChain();
        assertTrue(before.isOk(), () -> "Precondition failed: " + before.getIssues());

        Long eventId = jdbc.queryForObject(
                "select start_event_id from integrity_checkpoint order by batch_no asc limit 1",
                Long.class
        );
        assertNotNull(eventId);

        int changed = jdbc.update(
                "update freq_integrity_event set data_hash=repeat('0',64) where id=?",
                eventId
        );
        assertEquals(1, changed);

        CheckpointVerificationReport after =
                integrityService.verifyCheckpointChain();

        assertFalse(after.isOk(), "Tampered event must fail checkpoint verification");
        assertTrue(
                after.getIssues().stream().anyMatch(s -> s.startsWith("ROOT_HASH_MISMATCH")),
                () -> "Expected ROOT_HASH_MISMATCH, issues=" + after.getIssues()
        );
    }

    @Test
    void directModificationOfCheckpointHash_isDetected() throws Exception {
        doUpdates(5, 0.001);

        CheckpointVerificationReport before =
                integrityService.verifyCheckpointChain();
        assertTrue(before.isOk(), () -> "Precondition failed: " + before.getIssues());

        int changed = jdbc.update(
                "update integrity_checkpoint set checkpoint_hash=repeat('0',64) " +
                        "where batch_no=1"
        );
        assertEquals(1, changed);

        CheckpointVerificationReport after =
                integrityService.verifyCheckpointChain();

        assertFalse(after.isOk(), "Tampered checkpoint hash must fail verification");
        assertTrue(
                after.getIssues().stream().anyMatch(s -> s.startsWith("CHECKPOINT_HASH_MISMATCH")),
                () -> "Expected CHECKPOINT_HASH_MISMATCH, issues=" + after.getIssues()
        );
    }

    @Test
    void directModificationOfCheckpointState_isDetected() throws Exception {
        doUpdates(5, 0.001);

        CheckpointVerificationReport before =
                integrityService.verifyCheckpointChain();
        assertTrue(before.isOk(), () -> "Precondition failed: " + before.getIssues());

        int changed = jdbc.update(
                "update integrity_checkpoint_state " +
                        "set last_checkpoint_hash=repeat('0',64) where id=1"
        );
        assertEquals(1, changed);

        CheckpointVerificationReport after =
                integrityService.verifyCheckpointChain();

        assertFalse(after.isOk(), "Tampered retained checkpoint state must fail verification");
        assertTrue(
                after.getIssues().stream().anyMatch(s -> s.startsWith("CHECKPOINT_STATE_HASH_MISMATCH")),
                () -> "Expected CHECKPOINT_STATE_HASH_MISMATCH, issues=" + after.getIssues()
        );
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void doUpdates(int times, double delta) throws Exception {
        for (int i = 0; i < times; i++) {
            FreqView current = freqDao.getById(freqId);
            double base = current.getFreqModel().getNominal();
            current.getFreqModel().setNominal(base + delta);

            boolean ok = freqCrudService.updateFreqOnly(current);
            assertTrue(ok, "updateFreqOnly must succeed at i=" + i);
        }
    }

    private void resetCheckpointEvidence() {
        // Clear volatile accumulator first so no unfinished batch survives between tests.
        integrityService.resetCheckpointAccumulator();

        jdbc.update("delete from freq_integrity_event");
        jdbc.update("delete from integrity_checkpoint");
        jdbc.update(
                "update integrity_checkpoint_state " +
                        "set last_checkpoint_hash='GENESIS', next_batch_no=1 where id=1"
        );
    }

    private Long pickSafeFreqId() {
        String sql =
                "select f.ID " +
                "from freq f " +
                "join site s on s.ID = f.IDsite " +
                "join owner ow on ow.ID = s.IDowner " +
                "where f.type <> 4294967295 " +
                "and f.mode <> 4294967295 " +
                "and s.polar <> 4294967295 " +
                "order by f.ID asc limit 1";
        try {
            return jdbc.queryForObject(sql, Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbc.queryForObject(
                "select count(*) from information_schema.tables " +
                        "where table_schema=database() and table_name=?",
                Integer.class,
                tableName
        );
        assertNotNull(count);
        assertTrue(count > 0, "Required table not found: " + tableName);
    }
}

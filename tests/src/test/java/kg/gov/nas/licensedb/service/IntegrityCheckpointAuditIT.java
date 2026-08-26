package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dto.FreqView;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for checkpoint-based audit trail.
 *
 * IMPORTANT:
 * - This test writes to the configured DB (it is not rolled back), because DAO update commits explicitly.
 * - To avoid accidental execution on a non-test DB, it runs only if env var RUN_DB_IT=true is set.
 *
 * Run:
 *   Windows (PowerShell):  $env:RUN_DB_IT="true"; mvn -Dtest=IntegrityCheckpointAuditIT test
 *   Linux/macOS:          RUN_DB_IT=true mvn -Dtest=IntegrityCheckpointAuditIT test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
public class IntegrityCheckpointAuditIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private FreqDao freqDao;
    @Autowired private FreqCrudService freqCrudService;
    @Autowired private IntegrityService integrityService;

    private Long freqId;
    private Double originalNominal;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure checkpoint tables exist; if not -> fail fast (this is the "evidence" check).
        assertTableExists("freq_integrity_event");
        assertTableExists("integrity_checkpoint");
        assertTableExists("integrity_checkpoint_state");

        // Reset checkpoint tables/state
        resetCheckpointTables();

        // Configure services for checkpoint mode with small K to keep test fast
        integrityService.setEnabled(true);
        integrityService.setStrategy(IntegrityService.Strategy.CHECKPOINT);
        integrityService.setCheckpointBatchSize(5); // K=5 for test speed

        // Signature not needed for this audit-trail proof
        freqCrudService.setSignatureEnabled(false);

        // Choose a "safe" freq record (avoid dirty sentinel values)
        freqId = pickSafeFreqId();
        assumeTrue(freqId != null, "No suitable freq record found in DB; test skipped.");

        // Load view and store original nominal to restore later
        FreqView v = freqDao.getById(freqId);
        assertNotNull(v);
        assertNotNull(v.getFreqModel());
        originalNominal = v.getFreqModel().getNominal();
        assumeTrue(originalNominal != null, "Original nominal is null; test skipped.");
    }

    @AfterEach
    void tearDown() {
        // Restore nominal to original value to keep DB stable after test.
        // Also clean integrity tables to avoid side-effects.
        try {
            if (freqId != null && originalNominal != null) {
                FreqView v = freqDao.getById(freqId);
                v.getFreqModel().setNominal(originalNominal);
                freqCrudService.updateFreqOnly(v);
            }
        } catch (Exception ignored) {}

        try {
            resetCheckpointTables();
        } catch (Exception ignored) {}
    }

    @Test
    void checkpointTrail_createsEventRows_andCheckpointEveryK() throws Exception {
        // --- First batch of K=5 updates ---
        doUpdates(5, 0.001);

        long eventCount = jdbc.queryForObject("select count(*) from freq_integrity_event", Long.class);
        long checkpointCount = jdbc.queryForObject("select count(*) from integrity_checkpoint", Long.class);

        assertEquals(5L, eventCount, "Should insert K event rows");
        assertEquals(1L, checkpointCount, "Should create 1 checkpoint after K events");

        String lastHash = jdbc.queryForObject(
                "select last_checkpoint_hash from integrity_checkpoint_state where id=1",
                String.class
        );
        Long nextBatchNo = jdbc.queryForObject(
                "select next_batch_no from integrity_checkpoint_state where id=1",
                Long.class
        );

        assertNotNull(lastHash);
        assertNotEquals("GENESIS", lastHash, "Checkpoint state hash should advance from GENESIS");
        assertEquals(2L, Objects.requireNonNull(nextBatchNo), "Next batch no should advance to 2");

        // Validate checkpoint row shape
        Long eventCountInCp = jdbc.queryForObject(
                "select event_count from integrity_checkpoint order by id asc limit 1",
                Long.class
        );
        Long startId = jdbc.queryForObject(
                "select start_event_id from integrity_checkpoint order by id asc limit 1",
                Long.class
        );
        Long endId = jdbc.queryForObject(
                "select end_event_id from integrity_checkpoint order by id asc limit 1",
                Long.class
        );
        String prevCp = jdbc.queryForObject(
                "select prev_checkpoint_hash from integrity_checkpoint order by id asc limit 1",
                String.class
        );
        String cpHash = jdbc.queryForObject(
                "select checkpoint_hash from integrity_checkpoint order by id asc limit 1",
                String.class
        );

        assertEquals(5L, Objects.requireNonNull(eventCountInCp), "Checkpoint must record K events");
        assertNotNull(startId);
        assertNotNull(endId);
        assertEquals(5L, endId - startId + 1, "Event id range should match K events (single writer assumption)");
        assertEquals("GENESIS", prevCp, "First checkpoint must link to GENESIS");
        assertNotNull(cpHash);
        assertEquals(64, cpHash.length(), "SHA-256 hex length should be 64");

        // --- Second batch of K=5 updates => second checkpoint ---
        doUpdates(5, 0.002);

        eventCount = jdbc.queryForObject("select count(*) from freq_integrity_event", Long.class);
        checkpointCount = jdbc.queryForObject("select count(*) from integrity_checkpoint", Long.class);

        assertEquals(10L, eventCount, "Should have 2K event rows after two batches");
        assertEquals(2L, checkpointCount, "Should have 2 checkpoints after two batches");

        String firstCpHash = jdbc.queryForObject(
                "select checkpoint_hash from integrity_checkpoint order by id asc limit 1",
                String.class
        );
        String secondPrev = jdbc.queryForObject(
                "select prev_checkpoint_hash from integrity_checkpoint order by id desc limit 1",
                String.class
        );
        assertEquals(firstCpHash, secondPrev, "Second checkpoint must chain to first checkpoint");
    }

    @Test
    void whenIntegrityDisabled_noAuditRowsAreCreated() throws Exception {
        resetCheckpointTables();

        integrityService.setEnabled(false);
        integrityService.setStrategy(IntegrityService.Strategy.CHECKPOINT);
        integrityService.setCheckpointBatchSize(5);

        doUpdates(5, 0.003);

        long eventCount = jdbc.queryForObject("select count(*) from freq_integrity_event", Long.class);
        long checkpointCount = jdbc.queryForObject("select count(*) from integrity_checkpoint", Long.class);

        assertEquals(0L, eventCount, "No events should be created when integrity is disabled");
        assertEquals(0L, checkpointCount, "No checkpoints should be created when integrity is disabled");
    }

    // ---------------- helpers ----------------

    private void doUpdates(int times, double delta) throws Exception {
        for (int i = 1; i <= times; i++) {
            FreqView v = freqDao.getById(freqId);
            double base = v.getFreqModel().getNominal();
            v.getFreqModel().setNominal(base + delta);
            boolean ok = freqCrudService.updateFreqOnly(v);
            assertTrue(ok, "updateFreqOnly must return true");
        }
    }

    private Long pickSafeFreqId() {
        // Filters out dirty sentinel values that caused earlier crashes
        // and ensures owner/site exist.
        String sql =
                "select f.ID " +
                "from freq f " +
                "join site s on s.ID = f.IDsite " +
                "join owner ow on ow.ID = s.IDowner " +
                "where f.type <> 4294967295 and f.mode <> 4294967295 and s.polar <> 4294967295 " +
                "order by f.ID asc limit 1";
        try {
            return jdbc.queryForObject(sql, Long.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void resetCheckpointTables() {
        jdbc.update("delete from freq_integrity_event");
        jdbc.update("delete from integrity_checkpoint");
        jdbc.update("update integrity_checkpoint_state set last_checkpoint_hash='GENESIS', next_batch_no=1 where id=1");
    }

    private void assertTableExists(String tableName) {
        Integer cnt = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = database() and table_name = ?",
                Integer.class,
                tableName
        );
        assertNotNull(cnt);
        assertTrue(cnt > 0, "Required table not found: " + tableName);
    }
}

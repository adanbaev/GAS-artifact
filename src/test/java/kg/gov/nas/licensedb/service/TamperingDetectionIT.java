package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dto.FreqView;
import kg.gov.nas.licensedb.util.SecurityUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test: tampering detection works.
 *
 * What it proves:
 * 1) The application writes an integrity event (data_hash) for a "known-good" state.
 * 2) A direct DB update (bypassing the application) changes the record.
 * 3) The stored data_hash no longer matches the recomputed hash => tampering is detectable.
 *
 * IMPORTANT:
 * - This test writes to the configured DB (changes a freq.nominal twice, then restores).
 * - It runs only if env var RUN_DB_IT=true is set (to avoid accidental execution).
 *
 * Run:
 *   Windows (PowerShell):  $env:RUN_DB_IT="true"; mvn -Dtest=TamperingDetectionIT test
 *   Linux/macOS:          RUN_DB_IT=true mvn -Dtest=TamperingDetectionIT test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_IT", matches = "true")
public class TamperingDetectionIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private FreqDao freqDao;
    @Autowired private FreqCrudService freqCrudService;
    @Autowired private IntegrityService integrityService;

    private Long freqId;
    private Double originalNominalMHz;

    @BeforeEach
    void setUp() throws Exception {
        // Ensure event table exists (this is part of "evidence")
        assertTableExists("freq_integrity_event");

        // Clean tables to make assertions deterministic
        resetCheckpointTables();

        // Configure integrity to write immediately (K=1) so the event appears after a single update.
        integrityService.setEnabled(true);
        integrityService.setStrategy(IntegrityService.Strategy.CHECKPOINT);
        integrityService.setCheckpointBatchSize(1);

        // Signature not required for this proof
        freqCrudService.setSignatureEnabled(false);

        // Choose a "safe" record to avoid dirty sentinel values (4294967295 issues)
        freqId = pickSafeFreqId();
        assumeTrue(freqId != null, "No suitable freq record found in DB; test skipped.");

        FreqView v = freqDao.getById(freqId);
        assertNotNull(v);
        assertNotNull(v.getFreqModel());
        originalNominalMHz = v.getFreqModel().getNominal();
        assumeTrue(originalNominalMHz != null, "Original nominal is null; test skipped.");
    }

    @AfterEach
    void tearDown() {
        // Disable integrity so cleanup doesn't create extra audit rows
        try { integrityService.setEnabled(false); } catch (Exception ignored) {}

        // Restore nominal to original value
        try {
            if (freqId != null && originalNominalMHz != null) {
                FreqView v = freqDao.getById(freqId);
                v.getFreqModel().setNominal(originalNominalMHz);
                freqCrudService.updateFreqOnly(v);
            }
        } catch (Exception ignored) {}

        // Clean audit tables
        try { resetCheckpointTables(); } catch (Exception ignored) {}
    }

    @Test
    void tamperingIsDetectableByMismatchBetweenStoredEventHashAndCurrentStateHash() throws Exception {
        // --- Step 1: legitimate update through the application (creates integrity event) ---
        FreqView good = freqDao.getById(freqId);
        double base = good.getFreqModel().getNominal();
        good.getFreqModel().setNominal(base + 0.001);
        assertTrue(freqCrudService.updateFreqOnly(good), "Legitimate update must succeed");

        // fetch last stored event hash for this freqId
        String storedHash = jdbc.queryForObject(
                "select data_hash from freq_integrity_event where freq_id=? order by id desc limit 1",
                String.class,
                freqId
        );
        assertNotNull(storedHash);
        assertEquals(64, storedHash.length(), "data_hash must be a SHA-256 hex string (64 chars)");

        // compute hash of current (known-good) state and verify it matches stored hash
        FreqView currentGood = freqDao.getById(freqId);
        String computedGoodHash = SecurityUtil.freqDataHash(
                currentGood.getOwnerModel(), currentGood.getSiteModel(), currentGood.getFreqModel()
        );
        assertEquals(storedHash, computedGoodHash, "Stored event hash must match recomputed hash for known-good state");

        // --- Step 2: unauthorized modification bypassing application logic (direct DB update) ---
        // freq.nominal is stored in DB units (kHz in your schema); a small increment is enough.
        int updated = jdbc.update("update freq set nominal = nominal + 1 where ID = ?", freqId);
        assertEquals(1, updated, "Direct DB tampering update must affect exactly 1 row");

        // --- Step 3: verify tampering is detectable (hash mismatch) ---
        FreqView tampered = freqDao.getById(freqId);
        String computedTamperedHash = SecurityUtil.freqDataHash(
                tampered.getOwnerModel(), tampered.getSiteModel(), tampered.getFreqModel()
        );

        assertNotEquals(storedHash, computedTamperedHash,
                "After unauthorized DB update, stored event hash must NOT match recomputed current state hash");
    }

    // ---------------- helpers ----------------

    private Long pickSafeFreqId() {
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
        // These may exist even if you are not using checkpoints in this test.
        // Ignore failures if some tables are absent.
        try { jdbc.update("delete from freq_integrity_event"); } catch (Exception ignored) {}
        try { jdbc.update("delete from integrity_checkpoint"); } catch (Exception ignored) {}
        try { jdbc.update("update integrity_checkpoint_state set last_checkpoint_hash='GENESIS', next_batch_no=1 where id=1"); } catch (Exception ignored) {}
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

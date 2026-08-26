package kg.gov.nas.licensedb.bench;

import kg.gov.nas.licensedb.LicensedbApplication;
import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dto.FreqView;
import kg.gov.nas.licensedb.service.FreqCrudService;
import kg.gov.nas.licensedb.service.IntegrityService;
import kg.gov.nas.licensedb.util.SecurityUtil;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.FileWriter;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Reproducible service-layer benchmark for the integrity modes evaluated in the paper.
 *
 * IMPORTANT:
 *  - Legacy freq.signature generation is disabled in ALL benchmark modes.
 *  - HMAC_ONLY uses IntegrityService.Strategy.FINGERPRINT and persists one unchained
 *    HMAC-SHA-256 evidence row per successful registry update.
 *  - HMAC_CHECKPOINT computes the same HMAC evidence and adds checkpoint chaining (K).
 *  - HMAC_STRICT computes the same HMAC evidence and adds a strict per-write global chain.
 *  - Every measured run verifies success/failure counts and integrity-table row counts.
 *  - TPS is calculated from verified successful operations, not merely scheduled calls.
 */
public class MicrobenchmarkRunner {

    // Prevents the diagnostic HMAC computation from being optimized away.
    private static volatile String diagnosticHmacSink;

    private static final int[] SIZES = new int[]{1_000, 5_000, 10_000, 50_000, 100_000};
    private static final int WARMUP_OPS = 10_000;
    private static final int REPEATS = 30;

    private static final int CHECKPOINT_K = 100;
    private static final int K_SENSITIVITY_N = 100_000;
    private static final int[] K_SENSITIVITY_K_VALUES = new int[]{50, 100, 500};

    private static final int[] CONCURRENT_THREAD_COUNTS = new int[]{1, 2, 4, 8, 16, 32};
    private static final int CONCURRENT_OPS_PER_THREAD = 3_000;
    private static final int CONCURRENT_RECORDS_PER_THREAD = 500;
    private static final int CONCURRENT_REPEATS = 16;
    private static final int CONCURRENT_MAX_RECORDS =
            CONCURRENT_THREAD_COUNTS[CONCURRENT_THREAD_COUNTS.length - 1] * CONCURRENT_RECORDS_PER_THREAD;

    private static final Mode STANDARD =
            new Mode("STANDARD", false, null);
    private static final Mode HMAC_ONLY =
            new Mode("HMAC_ONLY", true, IntegrityService.Strategy.FINGERPRINT);
    private static final Mode HMAC_CHECKPOINT =
            new Mode("HMAC_CHECKPOINT", true, IntegrityService.Strategy.CHECKPOINT);
    private static final Mode HMAC_STRICT =
            new Mode("HMAC_STRICT", true, IntegrityService.Strategy.STRICT);

    private static final Mode[] MODES = new Mode[]{
            STANDARD,
            HMAC_ONLY,
            HMAC_CHECKPOINT,
            HMAC_STRICT
    };

    private static final class Mode {
        final String name;
        final boolean integrityEnabled;
        final IntegrityService.Strategy strategy;

        Mode(String name, boolean integrityEnabled, IntegrityService.Strategy strategy) {
            this.name = name;
            this.integrityEnabled = integrityEnabled;
            this.strategy = strategy;
        }
    }

    private static final class EvidenceCounts {
        final long eventRows;
        final long logRows;
        final long checkpointRows;

        EvidenceCounts(long eventRows, long logRows, long checkpointRows) {
            this.eventRows = eventRows;
            this.logRows = logRows;
            this.checkpointRows = checkpointRows;
        }
    }

    private static final class RunResult {
        final long planned;
        final long successful;
        final long failed;
        final double totalMs;
        final double tps;
        final EvidenceCounts evidence;

        RunResult(long planned, long successful, long failed, double totalMs,
                  double tps, EvidenceCounts evidence) {
            this.planned = planned;
            this.successful = successful;
            this.failed = failed;
            this.totalMs = totalMs;
            this.tps = tps;
            this.evidence = evidence;
        }
    }

    private static final class Summary {
        final double medianTotalMs;
        final double medianTps;
        final double meanTotalMs;
        final double stddevTotalMs;
        final double minTotalMs;
        final double maxTotalMs;
        final double iqrTotalMs;

        Summary(double medianTotalMs, double medianTps, double meanTotalMs,
                double stddevTotalMs, double minTotalMs, double maxTotalMs,
                double iqrTotalMs) {
            this.medianTotalMs = medianTotalMs;
            this.medianTps = medianTps;
            this.meanTotalMs = meanTotalMs;
            this.stddevTotalMs = stddevTotalMs;
            this.minTotalMs = minTotalMs;
            this.maxTotalMs = maxTotalMs;
            this.iqrTotalMs = iqrTotalMs;
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("spring.devtools.restart.enabled", "false");
        System.setProperty("spring.devtools.livereload.enabled", "false");

        String stage = resolveStage(args);

        Map<String, Object> props = new HashMap<>();
        props.put("spring.main.web-application-type", "none");
        props.put("spring.devtools.restart.enabled", "false");
        props.put("spring.datasource.hikari.maximum-pool-size", "64");

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(LicensedbApplication.class)
                .properties(props)
                .run(args);

        try {
            runBench(ctx, stage);
        } finally {
            ctx.close();
        }
    }

    private static String resolveStage(String[] args) {
        String stage = "all";
        for (String a : args) {
            if (a != null && !a.startsWith("--") && !a.isBlank()) {
                stage = a.trim().toLowerCase(Locale.US);
                break;
            }
        }
        if (!stage.equals("all") && !stage.equals("smoke") && !stage.equals("single")
                && !stage.equals("isolation") && !stage.equals("concurrent") && !stage.equals("ksens")) {
            throw new IllegalArgumentException("Unknown stage '" + stage
                    + "'. Use: smoke | single | isolation | concurrent | ksens | all");
        }
        return stage;
    }

    private static void runBench(ConfigurableApplicationContext ctx, String stage) throws Exception {
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        FreqDao freqDao = ctx.getBean(FreqDao.class);
        FreqCrudService freqCrudService = ctx.getBean(FreqCrudService.class);
        IntegrityService integrityService = ctx.getBean(IntegrityService.class);

        // The old 3-field SHA-256 freq.signature mechanism is deliberately excluded from
        // the revised scientific benchmark. Only the 23-field HMAC evidence path is measured.
        freqCrudService.setSignatureEnabled(false);

        verifyStorageEngines(jdbc);

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String outPath = Paths.get("bench-results-revised-" + stage + "-" + stamp + ".csv")
                .toAbsolutePath().toString();
        System.out.println("[BENCH] stage=" + stage + " | output=" + outPath);

        try (FileWriter fw = new FileWriter(outPath, false)) {
            fw.write("record_type,mode,n_ops,repeat,threads,k_value,planned_ops,successful_ops,failed_ops,"
                    + "total_ms,avg_ms_per_success,tps_success,event_rows,log_rows,checkpoint_rows,"
                    + "mean_total_ms,stddev_total_ms,min_total_ms,max_total_ms,iqr_total_ms\n");

            if (stage.equals("smoke")) {
                runSmokeBench(jdbc, freqDao, freqCrudService, integrityService, fw);
            }
            if (stage.equals("all") || stage.equals("single")) {
                runSingleThreadBench(jdbc, freqDao, freqCrudService, integrityService, fw);
            }
            if (stage.equals("isolation")) {
                runIsolationBench(jdbc, freqDao, freqCrudService, integrityService, fw);
            }
            if (stage.equals("all") || stage.equals("concurrent")) {
                runConcurrentBench(jdbc, freqDao, freqCrudService, integrityService, fw);
            }
            if (stage.equals("all") || stage.equals("ksens")) {
                runKSensitivityBench(jdbc, freqDao, freqCrudService, integrityService, fw);
            }
        }

        System.out.println("[BENCH] CSV saved to: " + outPath);
    }

    // ---------------------------------------------------------------------
    // Environment / configuration
    // ---------------------------------------------------------------------

    private static void verifyStorageEngines(JdbcTemplate jdbc) {
        Map<String, String> engines = jdbc.query(
                "select TABLE_NAME, ENGINE from information_schema.TABLES "
                        + "where TABLE_SCHEMA=DATABASE() and TABLE_NAME in "
                        + "('freq','owner','site','freq_integrity_event','freq_integrity_log',"
                        + "'integrity_chain_state','integrity_checkpoint','integrity_checkpoint_state')",
                rs -> {
                    Map<String, String> m = new LinkedHashMap<>();
                    while (rs.next()) {
                        m.put(rs.getString(1), rs.getString(2));
                    }
                    return m;
                }
        );

        System.out.println("[BENCH] storage engines=" + engines);
        requireEngine(engines, "freq", "MyISAM");
        requireEngine(engines, "owner", "MyISAM");
        requireEngine(engines, "site", "MyISAM");
        requireEngine(engines, "freq_integrity_event", "InnoDB");
        requireEngine(engines, "freq_integrity_log", "InnoDB");
        requireEngine(engines, "integrity_chain_state", "InnoDB");
        requireEngine(engines, "integrity_checkpoint", "InnoDB");
        requireEngine(engines, "integrity_checkpoint_state", "InnoDB");
    }

    private static void requireEngine(Map<String, String> engines, String table, String expected) {
        String actual = engines.get(table);
        if (actual == null || !actual.equalsIgnoreCase(expected)) {
            throw new IllegalStateException("Unexpected storage engine for " + table
                    + ": expected " + expected + ", actual=" + actual);
        }
    }

    private static void configureMode(Mode mode, IntegrityService integrityService,
                                      FreqCrudService freqCrudService, int checkpointK) {
        // Never include the legacy freq.signature path in the revised benchmark.
        freqCrudService.setSignatureEnabled(false);

        integrityService.resetCheckpointAccumulator();
        integrityService.setEnabled(mode.integrityEnabled);
        if (mode.integrityEnabled) {
            integrityService.setStrategy(mode.strategy);
            if (mode.strategy == IntegrityService.Strategy.CHECKPOINT) {
                integrityService.setCheckpointBatchSize(checkpointK);
            }
        }
    }

    private static void resetEvidenceState(JdbcTemplate jdbc, IntegrityService integrityService) {
        integrityService.resetCheckpointAccumulator();

        jdbc.update("delete from freq_integrity_log");
        jdbc.update("update integrity_chain_state set last_hash='GENESIS' where id=1");

        jdbc.update("delete from freq_integrity_event");
        jdbc.update("delete from integrity_checkpoint");
        jdbc.update("update integrity_checkpoint_state "
                + "set last_checkpoint_hash='GENESIS', next_batch_no=1 where id=1");
    }


    // ---------------------------------------------------------------------
    // Fast preflight / smoke test
    // ---------------------------------------------------------------------

    private static void runSmokeBench(JdbcTemplate jdbc, FreqDao freqDao,
                                      FreqCrudService freqCrudService,
                                      IntegrityService integrityService,
                                      FileWriter fw) throws Exception {
        final int smokeOps = 100;
        final int smokeRows = 10;
        final int smokeK = 10;
        List<FreqView> views = loadViews(jdbc, freqDao, smokeRows);
        if (views.size() < smokeRows) {
            throw new IllegalStateException("Smoke test needs " + smokeRows
                    + " eligible rows, found " + views.size());
        }

        System.out.println("\n=== SMOKE TEST: 100 operations per mode ===");
        for (Mode mode : MODES) {
            int k = mode.strategy == IntegrityService.Strategy.CHECKPOINT ? smokeK : CHECKPOINT_K;
            configureMode(mode, integrityService, freqCrudService, k);
            resetEvidenceState(jdbc, integrityService);

            long success = 0;
            long failed = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < smokeOps; i++) {
                FreqView view = views.get(i % smokeRows);
                double base = view.getFreqModel().getNominal();
                int cycle = i / smokeRows;
                double delta = ((cycle & 1) == 0) ? 0.001 : -0.001;
                view.getFreqModel().setNominal(base + delta);
                boolean ok;
                try {
                    ok = freqCrudService.updateFreqOnly(view);
                } catch (RuntimeException ex) {
                    ok = false;
                    System.err.println("[SMOKE] exception mode=" + mode.name
                            + " i=" + i + ": " + ex.getMessage());
                }
                if (ok) success++; else failed++;
            }
            long t1 = System.nanoTime();

            double totalMs = (t1 - t0) / 1_000_000.0;
            EvidenceCounts evidence = readEvidenceCounts(jdbc);
            validateMeasuredRun(mode, success, failed, evidence, k);
            double tps = success * 1000.0 / Math.max(0.001, totalMs);
            double avg = totalMs / Math.max(1L, success);

            writeRunRow(fw, mode.name, smokeOps, 1, 1,
                    mode.strategy == IntegrityService.Strategy.CHECKPOINT ? smokeK : 0,
                    smokeOps, success, failed, totalMs, avg, tps, evidence);

            System.out.printf(Locale.US,
                    "SMOKE PASS | MODE=%s | success=%d | failed=%d | events=%d | logs=%d | checkpoints=%d | TPS=%.2f%n",
                    mode.name, success, failed, evidence.eventRows, evidence.logRows,
                    evidence.checkpointRows, tps);
        }
        System.out.println("=== ALL SMOKE TESTS PASSED ===");
    }

    // ---------------------------------------------------------------------
    // Single-thread benchmark
    // ---------------------------------------------------------------------

    private static void runSingleThreadBench(JdbcTemplate jdbc, FreqDao freqDao,
                                             FreqCrudService freqCrudService,
                                             IntegrityService integrityService,
                                             FileWriter fw) throws Exception {
        System.out.println("\n=== REVISED SINGLE-THREAD BENCHMARK ===");
        Map<String, Map<Integer, Summary>> summaries = new LinkedHashMap<>();

        for (Mode mode : MODES) {
            configureMode(mode, integrityService, freqCrudService, CHECKPOINT_K);
            Map<Integer, Summary> byN = new LinkedHashMap<>();
            summaries.put(mode.name, byN);

            System.out.println("\n--- MODE: " + mode.name + " ---");

            for (int nRequested : SIZES) {
                List<FreqView> views = loadViews(jdbc, freqDao, nRequested);
                int n = views.size();
                if (n == 0) {
                    throw new IllegalStateException("No eligible rows for N=" + nRequested);
                }

                // Warm up the exact mode, then reset persistent + transient evidence before timing.
                warmup(freqCrudService, views);
                resetEvidenceState(jdbc, integrityService);

                List<Double> totalTimes = new ArrayList<>(REPEATS);
                List<Double> tpsValues = new ArrayList<>(REPEATS);

                for (int rep = 1; rep <= REPEATS; rep++) {
                    resetEvidenceState(jdbc, integrityService);

                    long t0 = System.nanoTime();
                    long success = 0;
                    long failed = 0;

                    for (int i = 0; i < n; i++) {
                        FreqView view = views.get(i);
                        double base = view.getFreqModel().getNominal();
                        double delta = (rep % 2 == 0) ? -0.001 : 0.001;
                        view.getFreqModel().setNominal(base + delta);

                        boolean ok;
                        try {
                            ok = freqCrudService.updateFreqOnly(view);
                        } catch (RuntimeException ex) {
                            ok = false;
                            System.err.println("[BENCH] exception mode=" + mode.name
                                    + " N=" + n + " rep=" + rep + " i=" + i + ": " + ex.getMessage());
                        }
                        if (ok) success++; else failed++;
                    }
                    long t1 = System.nanoTime();

                    double totalMs = (t1 - t0) / 1_000_000.0;
                    EvidenceCounts evidence = readEvidenceCounts(jdbc);
                    validateMeasuredRun(mode, success, failed, evidence, CHECKPOINT_K);

                    double tps = success * 1000.0 / Math.max(0.001, totalMs);
                    double avg = totalMs / Math.max(1L, success);

                    totalTimes.add(totalMs);
                    tpsValues.add(tps);

                    System.out.printf(Locale.US,
                            "MODE=%s | N=%d | rep=%d | success=%d | failed=%d | total=%.2f ms | TPS=%.2f | events=%d | logs=%d | checkpoints=%d%n",
                            mode.name, n, rep, success, failed, totalMs, tps,
                            evidence.eventRows, evidence.logRows, evidence.checkpointRows);

                    writeRunRow(fw, mode.name, n, rep, 1,
                            mode.strategy == IntegrityService.Strategy.CHECKPOINT ? CHECKPOINT_K : 0,
                            n, success, failed, totalMs, avg, tps, evidence);
                }

                Summary s = summarize(totalTimes, tpsValues);
                byN.put(n, s);
                writeSummaryRow(fw, mode.name, n, 1,
                        mode.strategy == IntegrityService.Strategy.CHECKPOINT ? CHECKPOINT_K : 0, s);

                System.out.printf(Locale.US,
                        "SUMMARY | MODE=%s | N=%d | median=%.2f ms | medianTPS=%.2f | SD=%.2f | IQR=%.2f%n",
                        mode.name, n, s.medianTotalMs, s.medianTps, s.stddevTotalMs, s.iqrTotalMs);
            }
        }

        printSingleOverheads(summaries);
    }

    private static void printSingleOverheads(Map<String, Map<Integer, Summary>> summaries) {
        Map<Integer, Summary> base = summaries.get(STANDARD.name);
        if (base == null) return;

        System.out.println("\n=== SINGLE-THREAD OVERHEAD vs STANDARD (median total time) ===");
        for (Mode mode : MODES) {
            if (mode == STANDARD) continue;
            System.out.println("\n-- " + mode.name + " --");
            Map<Integer, Summary> r = summaries.get(mode.name);
            for (int n : SIZES) {
                Summary b = base.get(n);
                Summary x = r.get(n);
                if (b == null || x == null) continue;
                double overhead = 100.0 * (x.medianTotalMs - b.medianTotalMs) / b.medianTotalMs;
                System.out.printf(Locale.US,
                        "N=%d | standard=%.2f ms | %s=%.2f ms | overhead=%.2f%%%n",
                        n, b.medianTotalMs, mode.name, x.medianTotalMs, overhead);
            }
        }
    }


    // ---------------------------------------------------------------------
    // Diagnostic isolation benchmark
    // ---------------------------------------------------------------------

    /**
     * Diagnostic-only experiment used to decompose the cost of the proposed HMAC path.
     *
     * STANDARD:
     *   registry update only; integrity service disabled before any post-write DB reread.
     *
     * HMAC_COMPUTE:
     *   the same registry update, followed by a fresh DB reread and the exact 23-field,
     *   length-prefixed HMAC-SHA-256 computation used by SecurityUtil.freqDataHash(),
     *   but WITHOUT persisting integrity evidence.
     *
     * HMAC_COMPUTE is not a deployable integrity mode because no expected fingerprint is
     * retained. It is an experimental control that separates post-write reread + HMAC cost
     * from the cost of durable evidence persistence.
     *
     * For each repeat, STANDARD and HMAC_COMPUTE are executed as a paired block.
     * Their order alternates between repeats (AB / BA), and opposite nominal deltas are used
     * within each pair so the tested records return to their pre-pair values.
     */
    private static void runIsolationBench(JdbcTemplate jdbc, FreqDao freqDao,
                                          FreqCrudService freqCrudService,
                                          IntegrityService integrityService,
                                          FileWriter fw) throws Exception {
        System.out.println("\n=== HMAC PATH ISOLATION BENCHMARK ===");
        System.out.println("Diagnostic comparison: STANDARD vs HMAC_COMPUTE "
                + "(fresh DB reread + 23-field HMAC; no evidence persistence).");

        // Both diagnostic paths must exclude all durable integrity logging and the legacy signature.
        configureMode(STANDARD, integrityService, freqCrudService, CHECKPOINT_K);
        resetEvidenceState(jdbc, integrityService);

        for (int nRequested : SIZES) {
            List<FreqView> views = loadViews(jdbc, freqDao, nRequested);
            int n = views.size();
            if (n == 0) {
                throw new IllegalStateException("No eligible rows for N=" + nRequested);
            }

            // Warm the ordinary write path.
            warmup(freqCrudService, views);
            resetEvidenceState(jdbc, integrityService);

            // Warm the fresh-reread + HMAC path separately.
            warmupIsolation(freqDao, freqCrudService, views);
            resetEvidenceState(jdbc, integrityService);

            List<Double> standardTimes = new ArrayList<>(REPEATS);
            List<Double> standardTps = new ArrayList<>(REPEATS);
            List<Double> computeTimes = new ArrayList<>(REPEATS);
            List<Double> computeTps = new ArrayList<>(REPEATS);

            for (int rep = 1; rep <= REPEATS; rep++) {
                // AB/BA alternation reduces bias from slow temporal drift during the long run.
                boolean standardFirst = (rep % 2 == 1);

                DiagnosticResult first;
                DiagnosticResult second;

                if (standardFirst) {
                    first = runIsolationOnce(
                            "STANDARD", n, rep, +0.001,
                            false, jdbc, freqDao, freqCrudService, integrityService, views, fw);
                    second = runIsolationOnce(
                            "HMAC_COMPUTE", n, rep, -0.001,
                            true, jdbc, freqDao, freqCrudService, integrityService, views, fw);
                } else {
                    first = runIsolationOnce(
                            "HMAC_COMPUTE", n, rep, +0.001,
                            true, jdbc, freqDao, freqCrudService, integrityService, views, fw);
                    second = runIsolationOnce(
                            "STANDARD", n, rep, -0.001,
                            false, jdbc, freqDao, freqCrudService, integrityService, views, fw);
                }

                DiagnosticResult standard = first.mode.equals("STANDARD") ? first : second;
                DiagnosticResult compute = first.mode.equals("HMAC_COMPUTE") ? first : second;

                standardTimes.add(standard.totalMs);
                standardTps.add(standard.tps);
                computeTimes.add(compute.totalMs);
                computeTps.add(compute.tps);

                double pairedOverhead =
                        100.0 * (compute.totalMs - standard.totalMs) / standard.totalMs;

                System.out.printf(Locale.US,
                        "PAIR | N=%d | rep=%d | order=%s | STANDARD=%.2f ms | "
                                + "HMAC_COMPUTE=%.2f ms | paired_overhead=%.2f%%%n",
                        n, rep, standardFirst ? "STANDARD->HMAC_COMPUTE" : "HMAC_COMPUTE->STANDARD",
                        standard.totalMs, compute.totalMs, pairedOverhead);
            }

            Summary standardSummary = summarize(standardTimes, standardTps);
            Summary computeSummary = summarize(computeTimes, computeTps);

            writeSummaryRow(fw, "STANDARD", n, 1, 0, standardSummary);
            writeSummaryRow(fw, "HMAC_COMPUTE", n, 1, 0, computeSummary);

            double overhead = 100.0
                    * (computeSummary.medianTotalMs - standardSummary.medianTotalMs)
                    / standardSummary.medianTotalMs;
            double extraUsPerOp =
                    (computeSummary.medianTotalMs - standardSummary.medianTotalMs) * 1000.0 / n;

            System.out.printf(Locale.US,
                    "ISOLATION SUMMARY | N=%d | STANDARD=%.2f ms (%.2f TPS) | "
                            + "HMAC_COMPUTE=%.2f ms (%.2f TPS) | overhead=%.2f%% | "
                            + "extra=%.2f us/op%n",
                    n,
                    standardSummary.medianTotalMs, standardSummary.medianTps,
                    computeSummary.medianTotalMs, computeSummary.medianTps,
                    overhead, extraUsPerOp);
        }

        // Keep the sink observable for the JVM and provide a simple end-of-run sanity signal.
        if (diagnosticHmacSink == null || diagnosticHmacSink.length() != 64) {
            throw new IllegalStateException("Diagnostic HMAC sink was not populated correctly");
        }
        System.out.println("=== HMAC PATH ISOLATION BENCHMARK COMPLETED ===");
    }

    private static final class DiagnosticResult {
        final String mode;
        final double totalMs;
        final double tps;

        DiagnosticResult(String mode, double totalMs, double tps) {
            this.mode = mode;
            this.totalMs = totalMs;
            this.tps = tps;
        }
    }

    private static DiagnosticResult runIsolationOnce(
            String modeName,
            int n,
            int rep,
            double delta,
            boolean computeHmac,
            JdbcTemplate jdbc,
            FreqDao freqDao,
            FreqCrudService freqCrudService,
            IntegrityService integrityService,
            List<FreqView> views,
            FileWriter fw) throws Exception {

        configureMode(STANDARD, integrityService, freqCrudService, CHECKPOINT_K);
        resetEvidenceState(jdbc, integrityService);

        long success = 0;
        long failed = 0;
        long t0 = System.nanoTime();

        for (int i = 0; i < n; i++) {
            FreqView view = views.get(i);
            double base = view.getFreqModel().getNominal();
            view.getFreqModel().setNominal(base + delta);

            boolean ok;
            try {
                ok = freqCrudService.updateFreqOnly(view);

                if (ok && computeHmac) {
                    Long freqId = view.getFreqModel().getFreqId();
                    FreqView persisted = freqDao.getById(freqId);
                    if (persisted == null || persisted.getFreqModel() == null) {
                        throw new IllegalStateException(
                                "Fresh DB reread returned no record for freqId=" + freqId);
                    }

                    diagnosticHmacSink = SecurityUtil.freqDataHash(
                            persisted.getOwnerModel(),
                            persisted.getSiteModel(),
                            persisted.getFreqModel()
                    );
                }
            } catch (Exception ex) {
                ok = false;
                System.err.println("[ISOLATION] exception mode=" + modeName
                        + " N=" + n + " rep=" + rep + " i=" + i + ": " + ex.getMessage());
            }

            if (ok) success++; else failed++;
        }

        long t1 = System.nanoTime();
        double totalMs = (t1 - t0) / 1_000_000.0;

        EvidenceCounts evidence = readEvidenceCounts(jdbc);
        if (failed != 0 || success != n) {
            throw new IllegalStateException("Isolation run failed: mode=" + modeName
                    + " N=" + n + " rep=" + rep
                    + " success=" + success + " failed=" + failed);
        }
        if (evidence.eventRows != 0 || evidence.logRows != 0 || evidence.checkpointRows != 0) {
            throw new IllegalStateException("Isolation run unexpectedly persisted integrity evidence: "
                    + "mode=" + modeName + " events=" + evidence.eventRows
                    + " logs=" + evidence.logRows + " checkpoints=" + evidence.checkpointRows);
        }

        double tps = success * 1000.0 / Math.max(0.001, totalMs);
        double avg = totalMs / success;

        writeRunRow(fw, modeName, n, rep, 1, 0,
                n, success, failed, totalMs, avg, tps, evidence);

        return new DiagnosticResult(modeName, totalMs, tps);
    }

    private static void warmupIsolation(FreqDao freqDao,
                                        FreqCrudService freqCrudService,
                                        List<FreqView> views) {
        int m = Math.min(views.size(), 200);
        if (m == 0) {
            throw new IllegalStateException("Isolation warm-up requires at least one row");
        }

        for (int i = 0; i < WARMUP_OPS; i++) {
            FreqView view = views.get(i % m);
            int cycle = i / m;
            double delta = ((cycle & 1) == 0) ? 0.0001 : -0.0001;
            double base = view.getFreqModel().getNominal();
            view.getFreqModel().setNominal(base + delta);

            boolean ok = freqCrudService.updateFreqOnly(view);
            if (!ok) {
                throw new IllegalStateException("Isolation warm-up update failed at i=" + i);
            }

            try {
                Long freqId = view.getFreqModel().getFreqId();
                FreqView persisted = freqDao.getById(freqId);
                if (persisted == null || persisted.getFreqModel() == null) {
                    throw new IllegalStateException(
                            "Isolation warm-up reread failed for freqId=" + freqId);
                }
                diagnosticHmacSink = SecurityUtil.freqDataHash(
                        persisted.getOwnerModel(),
                        persisted.getSiteModel(),
                        persisted.getFreqModel()
                );
            } catch (Exception ex) {
                throw new IllegalStateException("Isolation warm-up failed at i=" + i, ex);
            }
        }
    }


    // ---------------------------------------------------------------------
    // Concurrency benchmark
    // ---------------------------------------------------------------------

    private static void runConcurrentBench(JdbcTemplate jdbc, FreqDao freqDao,
                                           FreqCrudService freqCrudService,
                                           IntegrityService integrityService,
                                           FileWriter fw) throws Exception {
        System.out.println("\n=== REVISED CONCURRENCY BENCHMARK ===");

        List<FreqView> allViews = loadViews(jdbc, freqDao, CONCURRENT_MAX_RECORDS);
        if (allViews.size() < CONCURRENT_MAX_RECORDS) {
            throw new IllegalStateException("Need " + CONCURRENT_MAX_RECORDS
                    + " eligible rows for disjoint concurrency slices, found " + allViews.size());
        }

        for (Mode mode : MODES) {
            configureMode(mode, integrityService, freqCrudService, CHECKPOINT_K);
            System.out.println("\n--- MODE: " + mode.name + " ---");

            for (int threadCount : CONCURRENT_THREAD_COUNTS) {
                // Warm-up with the same number of worker threads.
                resetEvidenceState(jdbc, integrityService);
                ConcurrentRunResult warm = runConcurrentOnce(threadCount, allViews, freqCrudService, true);
                if (warm.failed != 0) {
                    throw new IllegalStateException("Warm-up failures: mode=" + mode.name
                            + " threads=" + threadCount + " failed=" + warm.failed);
                }
                resetEvidenceState(jdbc, integrityService);

                List<Double> totalTimes = new ArrayList<>(CONCURRENT_REPEATS);
                List<Double> tpsValues = new ArrayList<>(CONCURRENT_REPEATS);

                for (int rep = 1; rep <= CONCURRENT_REPEATS; rep++) {
                    resetEvidenceState(jdbc, integrityService);

                    ConcurrentRunResult rr = runConcurrentOnce(
                            threadCount, allViews, freqCrudService, false);
                    EvidenceCounts evidence = readEvidenceCounts(jdbc);
                    validateMeasuredRun(mode, rr.successful, rr.failed, evidence, CHECKPOINT_K);

                    totalTimes.add(rr.wallMs);
                    tpsValues.add(rr.tps);

                    long planned = (long) threadCount * CONCURRENT_OPS_PER_THREAD;
                    double avg = rr.wallMs / Math.max(1L, rr.successful);

                    System.out.printf(Locale.US,
                            "MODE=%s | threads=%d | rep=%d | success=%d | failed=%d | wall=%.2f ms | aggTPS=%.2f | events=%d | logs=%d | checkpoints=%d%n",
                            mode.name, threadCount, rep, rr.successful, rr.failed,
                            rr.wallMs, rr.tps, evidence.eventRows, evidence.logRows, evidence.checkpointRows);

                    writeRunRow(fw, mode.name, (int) planned, rep, threadCount,
                            mode.strategy == IntegrityService.Strategy.CHECKPOINT ? CHECKPOINT_K : 0,
                            planned, rr.successful, rr.failed, rr.wallMs, avg, rr.tps, evidence);
                }

                Summary s = summarize(totalTimes, tpsValues);
                long planned = (long) threadCount * CONCURRENT_OPS_PER_THREAD;
                writeSummaryRow(fw, mode.name, (int) planned, threadCount,
                        mode.strategy == IntegrityService.Strategy.CHECKPOINT ? CHECKPOINT_K : 0, s);

                System.out.printf(Locale.US,
                        "SUMMARY | MODE=%s | threads=%d | medianTPS=%.2f | medianWall=%.2f ms | SDwall=%.2f | IQRwall=%.2f%n",
                        mode.name, threadCount, s.medianTps, s.medianTotalMs,
                        s.stddevTotalMs, s.iqrTotalMs);
            }
        }
    }

    private static final class ConcurrentRunResult {
        final long successful;
        final long failed;
        final double wallMs;
        final double tps;

        ConcurrentRunResult(long successful, long failed, double wallMs, double tps) {
            this.successful = successful;
            this.failed = failed;
            this.wallMs = wallMs;
            this.tps = tps;
        }
    }

    private static ConcurrentRunResult runConcurrentOnce(int threadCount, List<FreqView> allViews,
                                                         FreqCrudService freqCrudService,
                                                         boolean warmup) throws InterruptedException {
        final int opsPerThread = warmup
                ? Math.min(2_000, CONCURRENT_OPS_PER_THREAD)
                : CONCURRENT_OPS_PER_THREAD;

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicLong success = new AtomicLong();
        AtomicLong failed = new AtomicLong();

        for (int t = 0; t < threadCount; t++) {
            final int tIdx = t;
            pool.submit(() -> {
                int from = tIdx * CONCURRENT_RECORDS_PER_THREAD;
                int to = from + CONCURRENT_RECORDS_PER_THREAD;
                List<FreqView> slice = allViews.subList(from, to);
                int sz = slice.size();

                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        FreqView view = slice.get(i % sz);
                        // Alternate by full slice cycle, so every row returns to its starting
                        // nominal value after every pair of cycles; this avoids long-run drift.
                        double delta = (((i / sz) & 1) == 0) ? 0.001 : -0.001;
                        double base = view.getFreqModel().getNominal();
                        view.getFreqModel().setNominal(base + delta);

                        try {
                            if (freqCrudService.updateFreqOnly(view)) {
                                success.incrementAndGet();
                            } else {
                                failed.incrementAndGet();
                            }
                        } catch (RuntimeException ex) {
                            failed.incrementAndGet();
                            System.err.println("[BENCH] concurrent exception mode-thread="
                                    + threadCount + " t=" + tIdx + " i=" + i + ": " + ex.getMessage());
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failed.addAndGet(opsPerThread);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long t0 = System.nanoTime();
        start.countDown();
        done.await();
        long t1 = System.nanoTime();
        pool.shutdown();

        double wallMs = (t1 - t0) / 1_000_000.0;
        long ok = success.get();
        long bad = failed.get();
        double tps = ok * 1000.0 / Math.max(0.001, wallMs);

        return new ConcurrentRunResult(ok, bad, wallMs, tps);
    }

    // ---------------------------------------------------------------------
    // K-sensitivity benchmark
    // ---------------------------------------------------------------------

    /**
     * Balanced K-sensitivity experiment at N=100,000.
     *
     * Five conditions are interleaved within every repeat:
     *   STANDARD
     *   HMAC_ONLY
     *   HMAC_CHECKPOINT K=50
     *   HMAC_CHECKPOINT K=100
     *   HMAC_CHECKPOINT K=500
     *
     * The order is cyclically rotated across 30 repeats. Because 30 is exactly
     * divisible by 5, every condition appears exactly six times in each temporal
     * position (1..5). This limits systematic time/order bias.
     *
     * All five conditions in an odd-numbered repeat use +0.001 nominal delta;
     * all five conditions in the following even-numbered repeat use -0.001.
     * Thus each pair of repeats restores the tested records to their prior values.
     */
    private static void runKSensitivityBench(JdbcTemplate jdbc, FreqDao freqDao,
                                             FreqCrudService freqCrudService,
                                             IntegrityService integrityService,
                                             FileWriter fw) throws Exception {
        System.out.println("\n=== BALANCED K-SENSITIVITY BENCHMARK ===");
        System.out.println("N=" + K_SENSITIVITY_N
                + " | repeats=" + REPEATS
                + " | conditions=STANDARD,HMAC_ONLY,K50,K100,K500");

        List<FreqView> views = loadViews(jdbc, freqDao, K_SENSITIVITY_N);
        int n = views.size();
        if (n == 0) {
            throw new IllegalStateException("No eligible rows for K-sensitivity benchmark");
        }
        if (n != K_SENSITIVITY_N) {
            throw new IllegalStateException("K-sensitivity requires exactly "
                    + K_SENSITIVITY_N + " eligible rows, found " + n);
        }

        KSensCondition[] conditions = new KSensCondition[]{
                new KSensCondition("STANDARD", STANDARD, 0),
                new KSensCondition("HMAC_ONLY", HMAC_ONLY, 0),
                new KSensCondition("HMAC_CHECKPOINT_K50", HMAC_CHECKPOINT, 50),
                new KSensCondition("HMAC_CHECKPOINT_K100", HMAC_CHECKPOINT, 100),
                new KSensCondition("HMAC_CHECKPOINT_K500", HMAC_CHECKPOINT, 500)
        };

        // Warm every tested path once before measured interleaving.
        for (KSensCondition condition : conditions) {
            int effectiveK = condition.mode.strategy == IntegrityService.Strategy.CHECKPOINT
                    ? condition.k : CHECKPOINT_K;
            configureMode(condition.mode, integrityService, freqCrudService, effectiveK);
            resetEvidenceState(jdbc, integrityService);
            warmup(freqCrudService, views);
            resetEvidenceState(jdbc, integrityService);
            System.out.println("[K-SENS] warm-up complete: " + condition.label);
        }

        Map<String, List<Double>> totalTimesByCondition = new LinkedHashMap<>();
        Map<String, List<Double>> tpsByCondition = new LinkedHashMap<>();
        for (KSensCondition condition : conditions) {
            totalTimesByCondition.put(condition.label, new ArrayList<>(REPEATS));
            tpsByCondition.put(condition.label, new ArrayList<>(REPEATS));
        }

        for (int rep = 1; rep <= REPEATS; rep++) {
            int rotation = (rep - 1) % conditions.length;
            double delta = (rep % 2 == 1) ? +0.001 : -0.001;

            StringBuilder orderText = new StringBuilder();
            for (int position = 0; position < conditions.length; position++) {
                if (position > 0) orderText.append(" -> ");
                KSensCondition condition =
                        conditions[(position + rotation) % conditions.length];
                orderText.append(condition.label);
            }

            System.out.println("K-SENS REPEAT " + rep
                    + " | delta=" + String.format(Locale.US, "%+.3f", delta)
                    + " | order: " + orderText);

            Map<String, KSensRunResult> repeatResults = new LinkedHashMap<>();

            for (int position = 0; position < conditions.length; position++) {
                KSensCondition condition =
                        conditions[(position + rotation) % conditions.length];

                KSensRunResult rr = runKSensOnce(
                        jdbc,
                        freqCrudService,
                        integrityService,
                        fw,
                        condition,
                        views,
                        n,
                        rep,
                        position + 1,
                        delta
                );

                repeatResults.put(condition.label, rr);
                totalTimesByCondition.get(condition.label).add(rr.totalMs);
                tpsByCondition.get(condition.label).add(rr.tps);
            }

            KSensRunResult std = repeatResults.get("STANDARD");
            KSensRunResult hmacOnly = repeatResults.get("HMAC_ONLY");

            for (KSensCondition condition : conditions) {
                if (condition.mode != HMAC_CHECKPOINT) continue;
                KSensRunResult cp = repeatResults.get(condition.label);

                double vsStd = 100.0 * (cp.totalMs - std.totalMs) / std.totalMs;
                double vsHmac = 100.0 * (cp.totalMs - hmacOnly.totalMs) / hmacOnly.totalMs;

                System.out.printf(Locale.US,
                        "K-SENS PAIR | rep=%d | K=%d | checkpoint=%.2f ms | "
                                + "vs_STANDARD=%+.2f%% | vs_HMAC_ONLY=%+.2f%%%n",
                        rep, condition.k, cp.totalMs, vsStd, vsHmac);
            }
        }

        // Write one summary row per condition and print the median comparisons.
        Map<String, Summary> summaries = new LinkedHashMap<>();
        for (KSensCondition condition : conditions) {
            Summary s = summarize(
                    totalTimesByCondition.get(condition.label),
                    tpsByCondition.get(condition.label)
            );
            summaries.put(condition.label, s);

            writeSummaryRow(
                    fw,
                    condition.mode.name,
                    n,
                    1,
                    condition.mode.strategy == IntegrityService.Strategy.CHECKPOINT
                            ? condition.k : 0,
                    s
            );
        }

        Summary standard = summaries.get("STANDARD");
        Summary hmacOnly = summaries.get("HMAC_ONLY");

        System.out.printf(Locale.US,
                "%n[K-SENS] BASELINES | STANDARD median=%.2f ms (%.2f TPS) | "
                        + "HMAC_ONLY median=%.2f ms (%.2f TPS)%n",
                standard.medianTotalMs, standard.medianTps,
                hmacOnly.medianTotalMs, hmacOnly.medianTps);

        for (KSensCondition condition : conditions) {
            if (condition.mode != HMAC_CHECKPOINT) continue;
            Summary cp = summaries.get(condition.label);

            double vsStd = 100.0 * (cp.medianTotalMs - standard.medianTotalMs)
                    / standard.medianTotalMs;
            double vsHmac = 100.0 * (cp.medianTotalMs - hmacOnly.medianTotalMs)
                    / hmacOnly.medianTotalMs;

            System.out.printf(Locale.US,
                    "[K-SENS] K=%d | checkpoint median=%.2f ms (%.2f TPS) | "
                            + "overhead vs STANDARD=%+.2f%% | "
                            + "relative vs HMAC_ONLY=%+.2f%%%n",
                    condition.k,
                    cp.medianTotalMs,
                    cp.medianTps,
                    vsStd,
                    vsHmac);
        }

        System.out.println("=== BALANCED K-SENSITIVITY BENCHMARK COMPLETED ===");
    }

    private static final class KSensCondition {
        final String label;
        final Mode mode;
        final int k;

        KSensCondition(String label, Mode mode, int k) {
            this.label = label;
            this.mode = mode;
            this.k = k;
        }
    }

    private static final class KSensRunResult {
        final double totalMs;
        final double tps;

        KSensRunResult(double totalMs, double tps) {
            this.totalMs = totalMs;
            this.tps = tps;
        }
    }

    private static KSensRunResult runKSensOnce(
            JdbcTemplate jdbc,
            FreqCrudService freqCrudService,
            IntegrityService integrityService,
            FileWriter fw,
            KSensCondition condition,
            List<FreqView> views,
            int n,
            int rep,
            int position,
            double delta) throws Exception {

        int effectiveK = condition.mode.strategy == IntegrityService.Strategy.CHECKPOINT
                ? condition.k : CHECKPOINT_K;

        configureMode(
                condition.mode,
                integrityService,
                freqCrudService,
                effectiveK
        );
        resetEvidenceState(jdbc, integrityService);

        long success = 0;
        long failed = 0;

        long t0 = System.nanoTime();

        for (int i = 0; i < n; i++) {
            FreqView view = views.get(i);
            double base = view.getFreqModel().getNominal();
            view.getFreqModel().setNominal(base + delta);

            boolean ok;
            try {
                ok = freqCrudService.updateFreqOnly(view);
            } catch (RuntimeException ex) {
                ok = false;
                System.err.println("[K-SENS] exception condition=" + condition.label
                        + " rep=" + rep + " position=" + position
                        + " i=" + i + ": " + ex.getMessage());
            }

            if (ok) success++; else failed++;
        }

        long t1 = System.nanoTime();

        double totalMs = (t1 - t0) / 1_000_000.0;
        EvidenceCounts evidence = readEvidenceCounts(jdbc);

        validateMeasuredRun(
                condition.mode,
                success,
                failed,
                evidence,
                effectiveK
        );

        if (success != n || failed != 0) {
            throw new IllegalStateException(
                    "K-sensitivity run failed: condition=" + condition.label
                            + " rep=" + rep
                            + " position=" + position
                            + " planned=" + n
                            + " success=" + success
                            + " failed=" + failed
            );
        }

        double tps = success * 1000.0 / Math.max(0.001, totalMs);
        double avg = totalMs / success;

        writeRunRow(
                fw,
                condition.mode.name,
                n,
                rep,
                1,
                condition.mode.strategy == IntegrityService.Strategy.CHECKPOINT
                        ? effectiveK : 0,
                n,
                success,
                failed,
                totalMs,
                avg,
                tps,
                evidence
        );

        System.out.printf(Locale.US,
                "K-SENS RUN | condition=%s | rep=%d | position=%d | "
                        + "success=%d | failed=%d | events=%d | logs=%d | "
                        + "checkpoints=%d | total=%.2f ms | TPS=%.2f%n",
                condition.label,
                rep,
                position,
                success,
                failed,
                evidence.eventRows,
                evidence.logRows,
                evidence.checkpointRows,
                totalMs,
                tps);

        return new KSensRunResult(totalMs, tps);
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    private static List<FreqView> loadViews(JdbcTemplate jdbc, FreqDao freqDao, int requested) {
        List<Long> ids = jdbc.queryForList(
                "select f.ID from freq f "
                        + "join site s on s.ID=f.IDsite "
                        + "join owner ow on ow.ID=s.IDowner "
                        + "where f.type <> 4294967295 and f.mode <> 4294967295 "
                        + "and s.polar <> 4294967295 "
                        + "order by f.ID asc limit ?",
                Long.class, requested);

        if (ids == null || ids.isEmpty()) return List.of();

        return ids.stream().map(id -> {
            try {
                return freqDao.getById(id);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to prefetch freqId=" + id, ex);
            }
        }).collect(Collectors.toList());
    }

    private static void warmup(FreqCrudService freqCrudService, List<FreqView> views) {
        int m = Math.min(views.size(), 200);
        if (m == 0) throw new IllegalStateException("Warm-up requires at least one row");

        for (int i = 0; i < WARMUP_OPS; i++) {
            FreqView view = views.get(i % m);
            int cycle = i / m;
            double delta = ((cycle & 1) == 0) ? 0.0001 : -0.0001;
            double base = view.getFreqModel().getNominal();
            view.getFreqModel().setNominal(base + delta);

            boolean ok = freqCrudService.updateFreqOnly(view);
            if (!ok) {
                throw new IllegalStateException("Warm-up update failed at i=" + i);
            }
        }
    }

    private static EvidenceCounts readEvidenceCounts(JdbcTemplate jdbc) {
        long events = jdbc.queryForObject("select count(*) from freq_integrity_event", Long.class);
        long logs = jdbc.queryForObject("select count(*) from freq_integrity_log", Long.class);
        long checkpoints = jdbc.queryForObject("select count(*) from integrity_checkpoint", Long.class);
        return new EvidenceCounts(events, logs, checkpoints);
    }

    private static void validateMeasuredRun(Mode mode, long success, long failed,
                                            EvidenceCounts evidence, int checkpointK) {
        if (failed != 0) {
            throw new IllegalStateException("Measured run contains failed operations: mode="
                    + mode.name + " success=" + success + " failed=" + failed);
        }

        long expectedEvents = 0;
        long expectedLogs = 0;
        long expectedCheckpoints = 0;

        if (mode.strategy == IntegrityService.Strategy.FINGERPRINT) {
            expectedEvents = success;
        } else if (mode.strategy == IntegrityService.Strategy.STRICT) {
            expectedLogs = success;
        } else if (mode.strategy == IntegrityService.Strategy.CHECKPOINT) {
            if (checkpointK < 1) throw new IllegalArgumentException("checkpointK must be >= 1");
            if (success % checkpointK != 0) {
                throw new IllegalStateException("Measured CHECKPOINT run must end on a flush boundary. "
                        + "success=" + success + " K=" + checkpointK);
            }
            expectedEvents = success;
            expectedCheckpoints = success / checkpointK;
        }

        if (evidence.eventRows != expectedEvents
                || evidence.logRows != expectedLogs
                || evidence.checkpointRows != expectedCheckpoints) {
            throw new IllegalStateException("Integrity evidence count mismatch for mode=" + mode.name
                    + " | expected events/logs/checkpoints=" + expectedEvents + "/"
                    + expectedLogs + "/" + expectedCheckpoints
                    + " | actual=" + evidence.eventRows + "/" + evidence.logRows
                    + "/" + evidence.checkpointRows);
        }
    }

    private static Summary summarize(List<Double> totalTimes, List<Double> tpsValues) {
        return new Summary(
                median(totalTimes),
                median(tpsValues),
                mean(totalTimes),
                stddev(totalTimes),
                min(totalTimes),
                max(totalTimes),
                iqr(totalTimes)
        );
    }

    private static void writeRunRow(FileWriter fw, String mode, int nOps, int repeat,
                                    int threads, int kValue, long planned, long successful,
                                    long failed, double totalMs, double avgMs, double tps,
                                    EvidenceCounts evidence) throws Exception {
        fw.write(String.format(Locale.US,
                "RUN,%s,%d,%d,%d,%d,%d,%d,%d,%.3f,%.9f,%.3f,%d,%d,%d,,,,,\n",
                mode, nOps, repeat, threads, kValue, planned, successful, failed,
                totalMs, avgMs, tps, evidence.eventRows, evidence.logRows, evidence.checkpointRows));
        fw.flush();
    }

    private static void writeSummaryRow(FileWriter fw, String mode, int nOps,
                                        int threads, int kValue, Summary s) throws Exception {
        fw.write(String.format(Locale.US,
                "SUMMARY,%s,%d,-1,%d,%d,,,,%.3f,%.9f,%.3f,,,,%.3f,%.3f,%.3f,%.3f,%.3f\n",
                mode, nOps, threads, kValue,
                s.medianTotalMs,
                s.medianTotalMs / Math.max(1, nOps),
                s.medianTps,
                s.meanTotalMs, s.stddevTotalMs, s.minTotalMs,
                s.maxTotalMs, s.iqrTotalMs));
        fw.flush();
    }

    private static double mean(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double x : xs) sum += x;
        return sum / xs.size();
    }

    private static double stddev(List<Double> xs) {
        if (xs == null || xs.size() < 2) return 0.0;
        double m = mean(xs);
        double sum = 0.0;
        for (double x : xs) sum += (x - m) * (x - m);
        return Math.sqrt(sum / (xs.size() - 1));
    }

    private static double min(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        double m = xs.get(0);
        for (double x : xs) if (x < m) m = x;
        return m;
    }

    private static double max(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        double m = xs.get(0);
        for (double x : xs) if (x > m) m = x;
        return m;
    }

    private static double median(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        List<Double> s = new ArrayList<>(xs);
        s.sort(Double::compareTo);
        int n = s.size();
        if ((n & 1) == 1) return s.get(n / 2);
        return (s.get(n / 2 - 1) + s.get(n / 2)) / 2.0;
    }

    private static double iqr(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        List<Double> s = new ArrayList<>(xs);
        s.sort(Double::compareTo);
        return percentile(s, 75.0) - percentile(s, 25.0);
    }

    private static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        double idx = p / 100.0 * (sorted.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted.get(lo);
        double frac = idx - lo;
        return sorted.get(lo) * (1.0 - frac) + sorted.get(hi) * frac;
    }
}

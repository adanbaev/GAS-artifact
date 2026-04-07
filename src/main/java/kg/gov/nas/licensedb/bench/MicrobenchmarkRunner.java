package kg.gov.nas.licensedb.bench;

import kg.gov.nas.licensedb.LicensedbApplication;
import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dto.FreqView;
import kg.gov.nas.licensedb.service.FreqCrudService;
import kg.gov.nas.licensedb.service.IntegrityService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MicrobenchmarkRunner {

    // Размеры для тестирования
    private static final int[] SIZES = new int[]{1000, 5000, 10000, 50000, 100000};

    // Операции прогрева (на уровне сервисов, как в нашей методологии)
    private static final int WARMUP_OPS = 10_000;

    // Повторяется для каждого (mode, N) для уменьшения шума
    private static final int REPEATS = 30;

    // Checkpoint K (used when integrityEnabled=true; IntegrityService uses CHECKPOINT strategy)
    private static final int CHECKPOINT_K = 100;

    // K-sensitivity experiment
    private static final int K_SENSITIVITY_N = 100_000;
    private static final int[] K_SENSITIVITY_K_VALUES = new int[]{50, 100, 500};

    private static final int[] THREAD_COUNTS = new int[]{2, 4};
    private static final int CONCURRENT_N = 10_000;

    private static final Mode[] MODES = new Mode[]{
            new Mode("STANDARD", false, false),
            new Mode("SIGNATURE_ONLY", false, true),
            new Mode("CHAIN_ONLY", true, false),
            new Mode("FULL_HYBRID", true, true)
    };

    private static final class Mode {
        final String name;
        final boolean integrityEnabled;
        final boolean signatureEnabled;

        Mode(String name, boolean integrityEnabled, boolean signatureEnabled) {
            this.name = name;
            this.integrityEnabled = integrityEnabled;
            this.signatureEnabled = signatureEnabled;
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, Object> props = new HashMap<>();
        props.put("spring.main.web-application-type", "none");

        // Disable DevTools restart for cleaner benchmark runs
        props.put("spring.devtools.restart.enabled", "false");

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(LicensedbApplication.class)
                .properties(props)
                .run(args);

        try {
            runBench(ctx);
        } finally {
            ctx.close();
        }
    }

    private static void runBench(ConfigurableApplicationContext ctx) throws Exception {
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        FreqDao freqDao = ctx.getBean(FreqDao.class);
        FreqCrudService freqCrudService = ctx.getBean(FreqCrudService.class);
        IntegrityService integrityService = ctx.getBean(IntegrityService.class);

        String outPath = Paths.get("bench-results.csv").toAbsolutePath().toString();

        // For printing overhead vs STANDARD using medians:
        Map<String, BenchResult[]> medianResultsByMode = new LinkedHashMap<>();

        try (FileWriter fw = new FileWriter(outPath, false)) {
            fw.write("mode,n_ops,repeat,total_ms,avg_ms_per_op,tps,threads,mean_ms,stddev_ms,min_ms,max_ms,iqr_ms,k_value\n");

            for (Mode m : MODES) {
                BenchResult[] medians = runOneModeWithRepeats(
                        m, jdbc, freqDao, freqCrudService, integrityService, fw
                );
                medianResultsByMode.put(m.name, medians);
            }

            // Print comparisons vs STANDARD (median-based)
            BenchResult[] base = medianResultsByMode.get("STANDARD");
            if (base == null) {
                System.out.println("No STANDARD baseline results - cannot compute overhead.");
                System.out.println("CSV saved to: " + outPath);
                return;
            }

            System.out.println("\n=== OVERHEAD vs STANDARD (median total time) ===");
            for (String modeName : medianResultsByMode.keySet()) {
                if ("STANDARD".equals(modeName)) continue;
                BenchResult[] r = medianResultsByMode.get(modeName);
                System.out.println("\n-- " + modeName + " --");
                for (int i = 0; i < SIZES.length; i++) {
                    BenchResult b = base[i];
                    BenchResult x = r[i];

                    // robust: never NPE
                    if (b == null || x == null || b.totalMs <= 0.0) {
                        System.out.printf("N=%d | skipped (insufficient data)%n", SIZES[i]);
                        continue;
                    }

                    double oh = 100.0 * (x.totalMs - b.totalMs) / b.totalMs;
                    System.out.printf(Locale.US,
                            "N=%d | standard=%.2f ms | %s=%.2f ms | overhead=%.2f%%%n",
                            SIZES[i], b.totalMs, modeName, x.totalMs, oh
                    );
                }
            }

            System.out.println("\n=== Throughput retained vs STANDARD (median TPS) ===");
            for (String modeName : medianResultsByMode.keySet()) {
                if ("STANDARD".equals(modeName)) continue;
                BenchResult[] r = medianResultsByMode.get(modeName);
                System.out.println("\n-- " + modeName + " --");
                for (int i = 0; i < SIZES.length; i++) {
                    BenchResult b = base[i];
                    BenchResult x = r[i];

                    if (b == null || x == null || b.tps <= 0.0) {
                        System.out.printf("N=%d | skipped (insufficient data)%n", SIZES[i]);
                        continue;
                    }

                    double retained = 100.0 * (x.tps / b.tps);
                    System.out.printf(Locale.US,
                            "N=%d | standard=%.2f TPS | %s=%.2f TPS | retained=%.2f%%%n",
                            SIZES[i], b.tps, modeName, x.tps, retained
                    );
                }
            }

            runConcurrentBench(jdbc, freqDao, freqCrudService, integrityService, fw);

            runKSensitivityExperiment(jdbc, freqDao, freqCrudService, integrityService, fw);

            System.out.println("\nCSV saved to: " + outPath);
        }
    }

    private static BenchResult[] runOneModeWithRepeats(
            Mode mode,
            JdbcTemplate jdbc,
            FreqDao freqDao,
            FreqCrudService freqCrudService,
            IntegrityService integrityService,
            FileWriter fw
    ) throws Exception {

        System.out.println("\n=== MODE: " + mode.name + " ===");

        // Configure toggles
        integrityService.setEnabled(mode.integrityEnabled);
        freqCrudService.setSignatureEnabled(mode.signatureEnabled);

        // If integrity enabled, use CHECKPOINT strategy with K
        if (mode.integrityEnabled) {
            integrityService.setStrategy(IntegrityService.Strategy.CHECKPOINT);
            integrityService.setCheckpointBatchSize(CHECKPOINT_K);
        }

        BenchResult[] medianResults = new BenchResult[SIZES.length];

        for (int idx = 0; idx < SIZES.length; idx++) {
            int nRequested = SIZES[idx];

            // Find candidate IDs (avoid dirty sentinel values causing enum/date issues)
            List<Long> ids = jdbc.queryForList(
                    "select f.ID " +
                            "from freq f " +
                            "join site s on s.ID = f.IDsite " +
                            "join owner ow on ow.ID = s.IDowner " +
                            "where f.type <> 4294967295 and f.mode <> 4294967295 " +
                            "and s.polar <> 4294967295 " +
                            "order by f.ID asc limit ?",
                    Long.class,
                    nRequested
            );

            if (ids == null || ids.isEmpty()) {
                System.out.printf("N=%d | no records available -> skipped%n", nRequested);
                medianResults[idx] = BenchResult.zero(mode.name, nRequested);
                continue;
            }

            int n = Math.min(nRequested, ids.size());

            // Prefetch views OUTSIDE measurement (so we measure writes)
            List<FreqView> views = ids.subList(0, n).stream()
                    .map(id -> {
                        try {
                            return freqDao.getById(id);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            // Warm-up
            warmup(freqCrudService, views);

            // Repeat measurements
            List<Double> totals = new ArrayList<>(REPEATS);
            List<Double> tpsList = new ArrayList<>(REPEATS);

            for (int rep = 1; rep <= REPEATS; rep++) {
                resetIntegrityTablesIfPresent(jdbc);

                long t0 = System.nanoTime();
                for (int i = 0; i < n; i++) {
                    FreqView v = views.get(i);

                    // Change nominal slightly so UPDATE is real; alternate sign to avoid drift
                    double base = v.getFreqModel().getNominal();
                    double delta = (rep % 2 == 0) ? -0.001 : 0.001;
                    v.getFreqModel().setNominal(base + delta);

                    freqCrudService.updateFreqOnly(v);
                }
                long t1 = System.nanoTime();

                double totalMs = (t1 - t0) / 1_000_000.0;
                double avgMs = totalMs / n;
                double tps = 1000.0 * n / Math.max(1.0, totalMs);

                totals.add(totalMs);
                tpsList.add(tps);

                System.out.printf(Locale.US,
                        "N=%d | rep=%d | total=%.2f ms | avg=%.6f ms/op | TPS=%.2f%n",
                        n, rep, totalMs, avgMs, tps
                );

                fw.write(String.format(Locale.US,
                        "%s,%d,%d,%.3f,%.6f,%.2f,1,,,,,,\n",
                        mode.name, n, rep, totalMs, avgMs, tps
                ));
                fw.flush();
            }

            // Summary statistics
            double totalMedian = median(totals);
            double tpsMedian   = median(tpsList);
            double avgMedian   = totalMedian / n;
            double totalMean   = mean(totals);
            double totalStddev = stddev(totals);
            double totalMin    = min(totals);
            double totalMax    = max(totals);
            double totalIqr    = iqr(totals);

            medianResults[idx] = new BenchResult(mode.name, n, totalMedian, avgMedian, tpsMedian,
                    totalMean, totalStddev, totalMin, totalMax, totalIqr);
            System.out.printf(Locale.US,
                    "N=%d | MEDIAN=%.2f ms | MEAN=%.2f ms | SD=%.2f ms | MIN=%.2f ms | MAX=%.2f ms | IQR=%.2f ms | MEDIAN TPS=%.2f%n",
                    n, totalMedian, totalMean, totalStddev, totalMin, totalMax, totalIqr, tpsMedian
            );
            fw.write(String.format(Locale.US,
                    "%s,%d,-1,%.3f,%.6f,%.2f,1,%.3f,%.3f,%.3f,%.3f,%.3f,\n",
                    mode.name, n, totalMedian, avgMedian, tpsMedian,
                    totalMean, totalStddev, totalMin, totalMax, totalIqr
            ));
            fw.flush();
        }

        // Ensure no nulls (safety)
        for (int i = 0; i < medianResults.length; i++) {
            if (medianResults[i] == null) {
                medianResults[i] = BenchResult.zero(mode.name, SIZES[i]);
            }
        }

        return medianResults;
    }

    private static void runKSensitivityExperiment(
            JdbcTemplate jdbc,
            FreqDao freqDao,
            FreqCrudService freqCrudService,
            IntegrityService integrityService,
            FileWriter fw
    ) throws Exception {
        System.out.println("\n=== K-SENSITIVITY EXPERIMENT (N=" + K_SENSITIVITY_N + ", repeats=" + REPEATS + ") ===");

        // Hardcoded STANDARD N=100000 median total_ms from the previous full benchmark run
        final double standardBaselineMs = 327583.533;
        System.out.printf(Locale.US, "[INFO] Using hardcoded STANDARD baseline: %.3f ms%n", standardBaselineMs);

        // Prefetch IDs and views once for all (mode, K) combinations
        List<Long> ids;
        try {
            ids = jdbc.queryForList(
                    "select f.ID " +
                    "from freq f " +
                    "join site s on s.ID = f.IDsite " +
                    "join owner ow on ow.ID = s.IDowner " +
                    "where f.type <> 4294967295 and f.mode <> 4294967295 " +
                    "and s.polar <> 4294967295 " +
                    "order by f.ID asc limit ?",
                    Long.class, K_SENSITIVITY_N
            );
        } catch (Exception e) {
            System.out.println("ERROR: DB connection failed while fetching IDs — datasource may be closed: " + e.getMessage());
            return;
        }

        if (ids == null || ids.isEmpty()) {
            System.out.println("No records available for K-sensitivity experiment -> skipped");
            return;
        }

        int n = Math.min(K_SENSITIVITY_N, ids.size());
        List<FreqView> views;
        try {
            views = ids.subList(0, n).stream()
                    .map(id -> {
                        try { return freqDao.getById(id); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.out.println("ERROR: DB connection failed while prefetching views — datasource may be closed: " + e.getMessage());
            return;
        }

        // Only CHAIN_ONLY and FULL_HYBRID
        Mode[] kModes = Arrays.stream(MODES)
                .filter(m -> "CHAIN_ONLY".equals(m.name) || "FULL_HYBRID".equals(m.name))
                .toArray(Mode[]::new);

        // modeName -> k -> median BenchResult
        Map<String, Map<Integer, BenchResult>> kResults = new LinkedHashMap<>();

        for (Mode mode : kModes) {
            kResults.put(mode.name, new LinkedHashMap<>());

            for (int k : K_SENSITIVITY_K_VALUES) {
                System.out.printf("%nK-sensitivity | MODE=%s | K=%d%n", mode.name, k);

                integrityService.setEnabled(mode.integrityEnabled);
                freqCrudService.setSignatureEnabled(mode.signatureEnabled);
                integrityService.setStrategy(IntegrityService.Strategy.CHECKPOINT);
                integrityService.setCheckpointBatchSize(k);

                warmup(freqCrudService, views);

                List<Double> totals = new ArrayList<>(REPEATS);
                List<Double> tpsList = new ArrayList<>(REPEATS);

                for (int rep = 1; rep <= REPEATS; rep++) {
                    resetIntegrityTablesIfPresent(jdbc);

                    long t0 = System.nanoTime();
                    for (int i = 0; i < n; i++) {
                        FreqView v = views.get(i);
                        double base = v.getFreqModel().getNominal();
                        double delta = (rep % 2 == 0) ? -0.001 : 0.001;
                        v.getFreqModel().setNominal(base + delta);
                        freqCrudService.updateFreqOnly(v);
                    }
                    long t1 = System.nanoTime();

                    double totalMs = (t1 - t0) / 1_000_000.0;
                    double avgMs = totalMs / n;
                    double tps = 1000.0 * n / Math.max(1.0, totalMs);

                    totals.add(totalMs);
                    tpsList.add(tps);

                    System.out.printf(Locale.US,
                            "K=%d | rep=%d | total=%.2f ms | avg=%.6f ms/op | TPS=%.2f%n",
                            k, rep, totalMs, avgMs, tps
                    );

                    fw.write(String.format(Locale.US,
                            "%s,%d,%d,%.3f,%.6f,%.2f,1,,,,,%d\n",
                            mode.name, n, rep, totalMs, avgMs, tps, k
                    ));
                    fw.flush();
                }

                double totalMedian = median(totals);
                double tpsMedian   = median(tpsList);
                double avgMedian   = totalMedian / n;
                double totalMean   = mean(totals);
                double totalStddev = stddev(totals);
                double totalMin    = min(totals);
                double totalMax    = max(totals);
                double totalIqr    = iqr(totals);

                BenchResult result = new BenchResult(mode.name, n, totalMedian, avgMedian, tpsMedian,
                        totalMean, totalStddev, totalMin, totalMax, totalIqr);
                kResults.get(mode.name).put(k, result);

                System.out.printf(Locale.US,
                        "K=%d | MEDIAN=%.2f ms | MEAN=%.2f ms | SD=%.2f ms | MIN=%.2f ms | MAX=%.2f ms | IQR=%.2f ms | MEDIAN TPS=%.2f%n",
                        k, totalMedian, totalMean, totalStddev, totalMin, totalMax, totalIqr, tpsMedian
                );

                // Summary row for this (mode, K)
                fw.write(String.format(Locale.US,
                        "%s,%d,-1,%.3f,%.6f,%.2f,1,%.3f,%.3f,%.3f,%.3f,%.3f,%d\n",
                        mode.name, n, totalMedian, avgMedian, tpsMedian,
                        totalMean, totalStddev, totalMin, totalMax, totalIqr, k
                ));
                fw.flush();
            }
        }

        // Print median overhead vs STANDARD
        System.out.println("\n=== K-SENSITIVITY: OVERHEAD vs STANDARD (N=" + K_SENSITIVITY_N + ", median total time) ===");
        for (Mode mode : kModes) {
            System.out.println("\n-- " + mode.name + " --");
            for (int k : K_SENSITIVITY_K_VALUES) {
                BenchResult r = kResults.get(mode.name).get(k);
                if (r == null || standardBaselineMs <= 0.0) {
                    System.out.printf("K=%d | skipped (insufficient data)%n", k);
                    continue;
                }
                double oh = 100.0 * (r.totalMs - standardBaselineMs) / standardBaselineMs;
                System.out.printf(Locale.US,
                        "K=%d | standard=%.2f ms | %s=%.2f ms | overhead=%.2f%%%n",
                        k, standardBaselineMs, mode.name, r.totalMs, oh
                );
            }
        }
    }

    private static void warmup(FreqCrudService freqCrudService, List<FreqView> views) {
        int m = Math.min(views.size(), 200); // no need for 10k distinct records
        for (int i = 0; i < WARMUP_OPS; i++) {
            FreqView v = views.get(i % m);

            double base = v.getFreqModel().getNominal();
            v.getFreqModel().setNominal(base + 0.0001);

            try {
                freqCrudService.updateFreqOnly(v);
            } catch (Exception ignored) {
                // warm-up is best effort
            }
        }
    }

    private static void resetIntegrityTablesIfPresent(JdbcTemplate jdbc) {
        // strict-chain tables (if exist)
        try {
            jdbc.update("delete from freq_integrity_log");
            jdbc.update("update integrity_chain_state set last_hash='GENESIS' where id=1");
        } catch (Exception ignored) {}

        // checkpoint-chain tables (if exist)
        try {
            jdbc.update("delete from freq_integrity_event");
            jdbc.update("delete from integrity_checkpoint");
            jdbc.update("update integrity_checkpoint_state set last_checkpoint_hash='GENESIS', next_batch_no=1 where id=1");
        } catch (Exception ignored) {}
    }

    private static void runConcurrentBench(
            JdbcTemplate jdbc,
            FreqDao freqDao,
            FreqCrudService freqCrudService,
            IntegrityService integrityService,
            FileWriter fw
    ) throws Exception {
        System.out.println("\n=== CONCURRENT BENCHMARK (N=" + CONCURRENT_N + " ops/thread) ===");

        for (Mode m : MODES) {
            integrityService.setEnabled(m.integrityEnabled);
            freqCrudService.setSignatureEnabled(m.signatureEnabled);
            if (m.integrityEnabled) {
                integrityService.setStrategy(IntegrityService.Strategy.CHECKPOINT);
                integrityService.setCheckpointBatchSize(CHECKPOINT_K);
            }

            List<Long> ids = jdbc.queryForList(
                    "select f.ID " +
                    "from freq f " +
                    "join site s on s.ID = f.IDsite " +
                    "join owner ow on ow.ID = s.IDowner " +
                    "where f.type <> 4294967295 and f.mode <> 4294967295 " +
                    "and s.polar <> 4294967295 " +
                    "order by f.ID asc limit ?",
                    Long.class, CONCURRENT_N
            );

            if (ids == null || ids.isEmpty()) {
                System.out.printf("MODE=%s | no records available -> skipped%n", m.name);
                continue;
            }

            int n = Math.min(CONCURRENT_N, ids.size());
            List<FreqView> views = ids.subList(0, n).stream()
                    .map(id -> {
                        try { return freqDao.getById(id); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    })
                    .collect(Collectors.toList());

            for (int threadCount : THREAD_COUNTS) {
                resetIntegrityTablesIfPresent(jdbc);

                java.util.concurrent.ExecutorService pool =
                        java.util.concurrent.Executors.newFixedThreadPool(threadCount);
                java.util.concurrent.CountDownLatch latch =
                        new java.util.concurrent.CountDownLatch(threadCount);

                long wallStart = System.nanoTime();

                for (int t = 0; t < threadCount; t++) {
                    final int tIdx = t;
                    pool.submit(() -> {
                        try {
                            double delta = (tIdx % 2 == 0) ? 0.001 : -0.001;
                            for (int i = 0; i < n; i++) {
                                FreqView v = views.get(i % views.size());
                                v.getFreqModel().setNominal(v.getFreqModel().getNominal() + delta);
                                freqCrudService.updateFreqOnly(v);
                            }
                        } catch (Exception ignored) {
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await();
                long wallEnd = System.nanoTime();
                pool.shutdown();

                double wallMs  = (wallEnd - wallStart) / 1_000_000.0;
                double totalOps = (double) threadCount * n;
                double aggTps  = totalOps / Math.max(1.0, wallMs) * 1000.0;

                System.out.printf(Locale.US,
                        "MODE=%s | threads=%d | N_per_thread=%d | wall=%.2f ms | aggTPS=%.2f%n",
                        m.name, threadCount, n, wallMs, aggTps
                );

                fw.write(String.format(Locale.US,
                        "%s,%d,concurrent,%.3f,%.6f,%.2f,%d,,,,,,\n",
                        m.name, n, wallMs, wallMs / totalOps, aggTps, threadCount
                ));
                fw.flush();
            }
        }
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

    private static double iqr(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        List<Double> s = new ArrayList<>(xs);
        s.sort(Double::compareTo);
        return percentile(s, 75.0) - percentile(s, 25.0);
    }

    private static double percentile(List<Double> sorted, double p) {
        int n = sorted.size();
        if (n == 1) return sorted.get(0);
        double idx = p / 100.0 * (n - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted.get(lo);
        return sorted.get(lo) * (1.0 - (idx - lo)) + sorted.get(hi) * (idx - lo);
    }

    private static double median(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return 0.0;
        List<Double> s = new ArrayList<>(xs);
        s.sort(Double::compareTo);
        int n = s.size();
        if (n % 2 == 1) return s.get(n / 2);
        return 0.5 * (s.get(n / 2 - 1) + s.get(n / 2));
    }

    private static final class BenchResult {
        final String mode;
        final int nOps;
        final double totalMs;
        final double avgMsPerOp;
        final double tps;
        final double mean;
        final double stddev;
        final double min;
        final double max;
        final double iqr;

        BenchResult(String mode, int nOps, double totalMs, double avgMsPerOp, double tps,
                    double mean, double stddev, double min, double max, double iqr) {
            this.mode = mode;
            this.nOps = nOps;
            this.totalMs = totalMs;
            this.avgMsPerOp = avgMsPerOp;
            this.tps = tps;
            this.mean = mean;
            this.stddev = stddev;
            this.min = min;
            this.max = max;
            this.iqr = iqr;
        }

        static BenchResult zero(String mode, int nOps) {
            return new BenchResult(mode, nOps, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
    }
}

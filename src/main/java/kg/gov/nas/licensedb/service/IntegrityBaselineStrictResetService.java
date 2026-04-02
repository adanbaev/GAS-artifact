package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.IntegrityLogDao;
import kg.gov.nas.licensedb.dto.FreqModel;
import kg.gov.nas.licensedb.dto.OwnerModel;
import kg.gov.nas.licensedb.dto.SiteModel;
import kg.gov.nas.licensedb.enums.FreqMode;
import kg.gov.nas.licensedb.enums.FreqType;
import kg.gov.nas.licensedb.util.FreqUtil;
import kg.gov.nas.licensedb.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Policy A (RESET): сброс журналов целостности и первичная инициализация STRICT-цепочки (baseline)
 * для "операционного контура" данных (freq -> site -> owner).
 *
 * Ключевой принцип UI: пользователи работают по owner.ID (как в старой программе).
 * Технический ключ логов/инцидентов остается freq.ID (как и в таблицах integrity_*).
 *
 * Baseline создает записи в freq_integrity_log в детерминированном порядке:
 *   order by owner.ID asc, freq.ID asc
 *
 * Важно:
 * - Baseline должен выполняться в окно обслуживания (без параллельных UPDATE/INSERT).
 * - Для скорости baseline НЕ использует freqDao.getById (иначе будет сотни тысяч отдельных запросов).
 */
@Service
@RequiredArgsConstructor
public class IntegrityBaselineStrictResetService {

    private static final String ACTOR = "SYSTEM_BASELINE";
    private static final String ACTION = "BASELINE";

    private final JdbcTemplate jdbcTemplate;
    private final IntegrityLogDao integrityLogDao;

    private DataSource ds() {
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) throw new IllegalStateException("DataSource is null");
        return ds;
    }

    public static final class BaselineResult {
        public final long startedAtMs;
        public final long finishedAtMs;
        public final long processedRows;
        public final long insertedLogs;
        public final Long ownerFrom;
        public final Long ownerTo;
        public final int batchSize;

        public BaselineResult(long startedAtMs, long finishedAtMs, long processedRows, long insertedLogs,
                              Long ownerFrom, Long ownerTo, int batchSize) {
            this.startedAtMs = startedAtMs;
            this.finishedAtMs = finishedAtMs;
            this.processedRows = processedRows;
            this.insertedLogs = insertedLogs;
            this.ownerFrom = ownerFrom;
            this.ownerTo = ownerTo;
            this.batchSize = batchSize;
        }
    }

    /**
     * Полный RESET+baseline для STRICT.
     *
     * @param ownerFrom фильтр по owner.ID (включительно), null = без ограничения
     * @param ownerTo   фильтр по owner.ID (включительно), null = без ограничения
     * @param batchSize размер коммита/батча INSERT в freq_integrity_log (рекомендуется 2000..20000)
     */
    public BaselineResult resetAndBaselineStrict(Long ownerFrom, Long ownerTo, int batchSize) {
        if (batchSize < 100) batchSize = 100;

        long started = System.currentTimeMillis();

        // 1) RESET integrity tables (Policy A)
        resetIntegrityTables();

        // 2) Stream operational contour rows and insert baseline logs in STRICT chain
        long processed = 0;
        long inserted = 0;

        final long baseEventMs = System.currentTimeMillis();
        long eventSeq = 0;

        List<RowData> buffer = new ArrayList<>(batchSize);

        // чтение делаем отдельным соединением (MyISAM), запись/цепочка - отдельными транзакциями
        try (Connection readConn = ds().getConnection()) {
            readConn.setReadOnly(true);

            String sql = buildBaselineSelectSql(ownerFrom, ownerTo);

            try (PreparedStatement ps = readConn.prepareStatement(sql,
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY)) {

                int paramIdx = 1;
                if (ownerFrom != null) ps.setLong(paramIdx++, ownerFrom);
                if (ownerTo != null) ps.setLong(paramIdx++, ownerTo);

                // Пытаемся подсказать драйверу fetchSize. Для 667k строк это помогает избежать лишней памяти.
                ps.setFetchSize(2000);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        RowData rd = mapRow(rs);
                        if (rd == null) continue;

                        buffer.add(rd);
                        processed++;

                        if (buffer.size() >= batchSize) {
                            long[] out = persistBaselineBatch(buffer, baseEventMs, eventSeq);
                            inserted += out[0];
                            eventSeq = out[1];
                            buffer.clear();
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Baseline read failed: " + ex.getMessage(), ex);
        }

        if (!buffer.isEmpty()) {
            long[] out = persistBaselineBatch(buffer, baseEventMs, eventSeq);
            inserted += out[0];
            eventSeq = out[1];
            buffer.clear();
        }

        long finished = System.currentTimeMillis();
        return new BaselineResult(started, finished, processed, inserted, ownerFrom, ownerTo, batchSize);
    }

    private void resetIntegrityTables() {
        // Инциденты и логи
        jdbcTemplate.update("delete from integrity_incident");
        jdbcTemplate.update("delete from freq_integrity_log");

        // CHECKPOINT-таблицы сбрасываем тоже, чтобы не было "хвостов" от предыдущих тестов
        integrityLogDao.resetCheckpointTables();

        // STRICT chain state
        int upd = jdbcTemplate.update("update integrity_chain_state set last_hash='GENESIS' where id=1");
        if (upd == 0) {
            jdbcTemplate.update("insert into integrity_chain_state (id, last_hash) values (1,'GENESIS')");
        }
    }

    private String buildBaselineSelectSql(Long ownerFrom, Long ownerTo) {
        // Важно: ownerId берём по site.IDowner (а не freq.IDowner), так как в legacy-схеме freq.IDowner часто 0.
        StringBuilder sb = new StringBuilder();
        sb.append("select ");
        sb.append("  s.IDowner as owner_id, ");
        sb.append("  o.Name as owner_name, ");
        sb.append("  o.date_v0, o.date_v1, o.date_v2, ");
        sb.append("  o.date_ok0, o.date_ok1, o.date_ok2, ");
        sb.append("  f.ID as freq_id, ");
        sb.append("  f.IDsite as site_id, ");
        sb.append("  s.latitude0, s.latitude1, s.latitude2, ");
        sb.append("  s.longtitude0, s.longtitude1, s.longtitude2, ");
        sb.append("  f.nominal, f.band, f.mode, f.type, f.channel, f.deviation, f.SNCH, f.inco, f.SatRadius, f.mob_stan, f.Obozn ");
        sb.append("from freq f ");
        sb.append("join site s on s.ID = f.IDsite ");
        sb.append("join owner o on o.ID = s.IDowner ");
        sb.append("where f.IDsite <> 0 and s.IDowner <> 0 ");

        if (ownerFrom != null) sb.append("and s.IDowner >= ? ");
        if (ownerTo != null) sb.append("and s.IDowner <= ? ");

        sb.append("order by s.IDowner asc, f.ID asc");
        return sb.toString();
    }

    private static final class RowData {
        final long ownerId;
        final String ownerName;
        final LocalDate issueDate;
        final LocalDate expireDate;

        final long siteId;
        final int lat0, lat1, lat2;
        final int lon0, lon1, lon2;

        final long freqId;
        final double nominalMHz;
        final double band;
        final Integer modeCode;   // raw db code (may be null)
        final Integer typeCode;   // raw db code (may be null)
        final Integer channel;    // uint32 -> int
        final double deviation;
        final double snch;
        final Integer incoCode;   // uint32 -> int
        final double satRadius;
        final Integer mobStan;    // uint32 -> int
        final String meaning;

        RowData(long ownerId, String ownerName, LocalDate issueDate, LocalDate expireDate,
                long siteId, int lat0, int lat1, int lat2, int lon0, int lon1, int lon2,
                long freqId, double nominalMHz, double band, Integer modeCode, Integer typeCode,
                Integer channel, double deviation, double snch, Integer incoCode, double satRadius,
                Integer mobStan, String meaning) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.issueDate = issueDate;
            this.expireDate = expireDate;
            this.siteId = siteId;
            this.lat0 = lat0;
            this.lat1 = lat1;
            this.lat2 = lat2;
            this.lon0 = lon0;
            this.lon1 = lon1;
            this.lon2 = lon2;
            this.freqId = freqId;
            this.nominalMHz = nominalMHz;
            this.band = band;
            this.modeCode = modeCode;
            this.typeCode = typeCode;
            this.channel = channel;
            this.deviation = deviation;
            this.snch = snch;
            this.incoCode = incoCode;
            this.satRadius = satRadius;
            this.mobStan = mobStan;
            this.meaning = meaning;
        }
    }

    private RowData mapRow(ResultSet rs) throws SQLException {
        long ownerId = rs.getLong("owner_id");
        if (ownerId == 0) return null;

        long freqId = rs.getLong("freq_id");
        if (freqId == 0) return null;

        long siteId = rs.getLong("site_id");
        if (siteId == 0) return null;

        String ownerName = rs.getString("owner_name");

        LocalDate issue = dateFromInts(rs.getInt("date_v2"), rs.getInt("date_v1"), rs.getInt("date_v0"));
        LocalDate expire = dateFromInts(rs.getInt("date_ok2"), rs.getInt("date_ok1"), rs.getInt("date_ok0"));

        int lat0 = rs.getInt("latitude0");
        int lat1 = rs.getInt("latitude1");
        int lat2 = rs.getInt("latitude2");
        int lon0 = rs.getInt("longtitude0");
        int lon1 = rs.getInt("longtitude1");
        int lon2 = rs.getInt("longtitude2");

        double nominalMHz = 0.0;
        try {
            nominalMHz = FreqUtil.kHzToMHz(rs.getDouble("nominal"));
        } catch (Exception ignored) {}

        double band = rs.getDouble("band");
        double deviation = rs.getDouble("deviation");
        double snch = rs.getDouble("SNCH");
        double satRadius = rs.getDouble("SatRadius");

        // uint/int safe conversions
        Integer modeCode = safeUInt32AsInt(rs, "mode");
        Integer typeCode = safeUInt32AsInt(rs, "type");
        Integer channel = safeUInt32AsInt(rs, "channel");
        Integer incoCode = safeUInt32AsInt(rs, "inco");
        Integer mobStan = safeUInt32AsInt(rs, "mob_stan");

        String meaning = rs.getString("Obozn");

        return new RowData(ownerId, ownerName, issue, expire,
                siteId, lat0, lat1, lat2, lon0, lon1, lon2,
                freqId, nominalMHz, band, modeCode, typeCode,
                channel, deviation, snch, incoCode, satRadius, mobStan, meaning);
    }

    private LocalDate dateFromInts(int y, int m, int d) {
        if (y == 0 || m == 0 || d == 0) return null;
        try {
            return LocalDate.of(y, m, d);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Чтение "INT UNSIGNED" безопасно. В JDBC getInt() может бросать SQLDataException
     * на значениях типа 4294967295. Берём getLong() и приводим как в FreqDao.
     */
    private Integer safeUInt32AsInt(ResultSet rs, String col) {
        try {
            long v = rs.getLong(col);
            if (rs.wasNull()) return null;

            if (v > Integer.MAX_VALUE) {
                return (int) (v - 4294967296L);
            }
            return (int) v;
        } catch (SQLException ex) {
            return null;
        }
    }

    /**
     * Пишем baseline-лог пачкой, продолжая STRICT-цепочку.
     *
     * @return [insertedCount, newEventSeq]
     */
    private long[] persistBaselineBatch(List<RowData> rows, long baseEventMs, long eventSeqStart) {
        if (rows == null || rows.isEmpty()) return new long[]{0, eventSeqStart};

        long inserted = 0;
        long eventSeq = eventSeqStart;

        try (Connection conn = ds().getConnection()) {
            conn.setAutoCommit(false);

            // 1) lock last_hash
            String lastHash = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "select last_hash from integrity_chain_state where id=1 for update")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) lastHash = rs.getString(1);
                }
            }
            if (lastHash == null || lastHash.isBlank()) lastHash = "GENESIS";

            // 2) batch insert
            try (PreparedStatement ins = conn.prepareStatement(
                    "insert into freq_integrity_log (event_ms, actor_username, action, freq_id, data_hash, prev_hash, chain_hash) " +
                            "values (?,?,?,?,?,?,?)")) {

                for (RowData r : rows) {
                    OwnerModel owner = new OwnerModel();
                    owner.setOwnerId(r.ownerId);
                    owner.setOwnerName(r.ownerName);
                    owner.setIssueDate(r.issueDate);
                    owner.setExpireDate(r.expireDate);

                    SiteModel site = new SiteModel();
                    site.setSiteId(r.siteId);
                    site.setLatitude0(r.lat0);
                    site.setLatitude1(r.lat1);
                    site.setLatitude2(r.lat2);
                    site.setLongitude0(r.lon0);
                    site.setLongitude1(r.lon1);
                    site.setLongitude2(r.lon2);

                    FreqModel freq = new FreqModel();
                    freq.setFreqId(r.freqId);
                    freq.setNominal(r.nominalMHz);
                    freq.setBand(r.band);
                    freq.setDeviation(r.deviation);
                    freq.setSnch(r.snch);
                    freq.setSatRadius(r.satRadius);
                    freq.setMeaning(r.meaning == null ? "" : r.meaning);

                    freq.setChannel(r.channel == null ? 0 : r.channel);
                    freq.setMobStan(r.mobStan == null ? 0 : r.mobStan);
                    int incoInt = (r.incoCode == null) ? 0 : r.incoCode;
                    freq.setInco(incoInt == 41);

                    // enums
                    FreqMode mode = null;
                    if (r.modeCode != null) {
                        try { mode = FreqMode.fromCode(r.modeCode); } catch (Exception ignored) {}
                    }
                    FreqType type = null;
                    if (r.typeCode != null) {
                        try { type = FreqType.fromCode(r.typeCode); } catch (Exception ignored) {}
                    }
                    freq.setMode(mode);
                    freq.setType(type);

                    String dataHash = SecurityUtil.freqDataHash(owner, site, freq);

                    long eventMs = baseEventMs + eventSeq;
                    eventSeq++;

                    String prevHash = lastHash;
                    String material = prevHash + "|" + eventMs + "|" + ACTOR + "|" + ACTION + "|" +
                            r.freqId + "|" + dataHash + "|" + SecurityUtil.getChainSecret();
                    String chainHash = SecurityUtil.sha256Hex(material);

                    ins.setLong(1, eventMs);
                    ins.setString(2, ACTOR);
                    ins.setString(3, ACTION);
                    ins.setLong(4, r.freqId);
                    ins.setString(5, dataHash);
                    ins.setString(6, prevHash);
                    ins.setString(7, chainHash);
                    ins.addBatch();

                    lastHash = chainHash;
                    inserted++;
                }

                ins.executeBatch();
            }

            // 3) update chain state
            try (PreparedStatement up = conn.prepareStatement(
                    "update integrity_chain_state set last_hash=? where id=1")) {
                up.setString(1, lastHash);
                up.executeUpdate();
            }

            conn.commit();
            return new long[]{inserted, eventSeq};

        } catch (SQLException ex) {
            throw new RuntimeException("Baseline write failed: " + ex.getMessage(), ex);
        }
    }
}

package kg.gov.nas.licensedb.dao;

import kg.gov.nas.licensedb.dto.IntegrityIncident;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class IntegrityIncidentDao {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<IntegrityIncident> mapper = (rs, rowNum) -> IntegrityIncident.builder()
        .id(rs.getLong("id"))
        .createdAtMs(rs.getLong("created_at_ms"))
        .detectedBy(rs.getString("detected_by"))
        .incidentType(rs.getString("incident_type"))
        .ownerId(rs.getObject("owner_id") == null ? null : rs.getLong("owner_id"))
        .freqId(rs.getLong("freq_id"))
        .lastLogId(rs.getObject("last_log_id") == null ? null : rs.getLong("last_log_id"))
        .expectedHash(rs.getString("expected_hash"))
        .actualHash(rs.getString("actual_hash"))
        .status(rs.getString("status"))
        .comment(rs.getString("comment"))
        .resolvedAtMs(rs.getObject("resolved_at_ms") == null ? null : rs.getLong("resolved_at_ms"))
        .resolvedBy(rs.getString("resolved_by"))
        .build();

    /** Есть ли открытый (OPEN) инцидент по записи */
    public boolean existsOpenForFreq(long freqId) {
        Integer cnt = jdbcTemplate.queryForObject(
            "select count(*) from integrity_incident where freq_id=? and status='OPEN'",
            Integer.class,
            freqId
        );
        return cnt != null && cnt > 0;
    }

    /** Получить инцидент по ID (без join'ов). Нужен для строгой логики закрытия. */
    public IntegrityIncident findById(long id) {
        List<IntegrityIncident> items = jdbcTemplate.query(
            "select id, created_at_ms, detected_by, incident_type, freq_id, last_log_id, expected_hash, actual_hash, status, comment, resolved_at_ms, resolved_by " +
                "from integrity_incident where id=? limit 1",
            (rs, rowNum) -> IntegrityIncident.builder()
                .id(rs.getLong("id"))
                .createdAtMs(rs.getLong("created_at_ms"))
                .detectedBy(rs.getString("detected_by"))
                .incidentType(rs.getString("incident_type"))
                .ownerId(null)
                .freqId(rs.getLong("freq_id"))
                .lastLogId(rs.getObject("last_log_id") == null ? null : rs.getLong("last_log_id"))
                .expectedHash(rs.getString("expected_hash"))
                .actualHash(rs.getString("actual_hash"))
                .status(rs.getString("status"))
                .comment(rs.getString("comment"))
                .resolvedAtMs(rs.getObject("resolved_at_ms") == null ? null : rs.getLong("resolved_at_ms"))
                .resolvedBy(rs.getString("resolved_by"))
                .build(),
            id
        );
        return items.isEmpty() ? null : items.get(0);
    }

    public Long findOpenIncidentId(long freqId, String incidentType) {
        List<Long> ids = jdbcTemplate.queryForList(
            "select id from integrity_incident where freq_id=? and incident_type=? and status='OPEN' limit 1",
            Long.class, freqId, incidentType
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    public long insertOpenIncident(long createdAtMs, String detectedBy, String incidentType, long freqId,
                                   Long lastLogId, String expectedHash, String actualHash, String comment) {
        jdbcTemplate.update(
            "insert into integrity_incident (created_at_ms, detected_by, incident_type, freq_id, last_log_id, expected_hash, actual_hash, status, comment) " +
                "values (?,?,?,?,?,?,?,'OPEN',?)",
            createdAtMs, detectedBy, incidentType, freqId, lastLogId, expectedHash, actualHash, comment
        );
        Long id = jdbcTemplate.queryForObject("select last_insert_id()", Long.class);
        return id == null ? -1L : id;
    }

    public void updateOpenIncident(long id, long createdAtMs, String detectedBy,
                                   Long lastLogId, String expectedHash, String actualHash, String comment) {
        jdbcTemplate.update(
            "update integrity_incident set created_at_ms=?, detected_by=?, last_log_id=?, expected_hash=?, actual_hash=?, comment=? " +
                "where id=? and status='OPEN'",
            createdAtMs, detectedBy, lastLogId, expectedHash, actualHash, comment, id
        );
    }

    /** Закрыть только OPEN, чтобы не было “тихих” повторных закрытий */
    public int resolve(long id, long resolvedAtMs, String resolvedBy, String comment) {
        return jdbcTemplate.update(
            "update integrity_incident set status='RESOLVED', resolved_at_ms=?, resolved_by=?, comment=? where id=? and status='OPEN'",
            resolvedAtMs, resolvedBy, comment, id
        );
    }

    /**
     * Автозакрытие после корректировки записи администратором.
     * Закрываем только те типы, которые действительно устраняются правкой данных.
     */
    public int resolveOpenForFreq(long freqId, long resolvedAtMs, String resolvedBy, String comment) {
        return jdbcTemplate.update(
            "update integrity_incident set status='RESOLVED', resolved_at_ms=?, resolved_by=?, comment=? " +
                "where freq_id=? and status='OPEN' and incident_type in ('DATA_MISMATCH','NO_LOG_FOR_FREQ')",
            resolvedAtMs, resolvedBy, comment, freqId
        );
    }

    public long count(String status, String incidentType, Long freqId, Long fromMs, Long toMs) {
        Sql sp = buildWhere(status, incidentType, null, freqId, fromMs, toMs, true);
        Long cnt = jdbcTemplate.queryForObject(sp.sql, Long.class, sp.params.toArray());
        return cnt == null ? 0L : cnt;
    }

    public List<IntegrityIncident> search(String status, String incidentType, Long freqId, Long fromMs, Long toMs,
                                          int limit, int offset) {
        Sql sp = buildWhere(status, incidentType, null, freqId, fromMs, toMs, false);
        String sql = sp.sql + " order by created_at_ms desc, id desc limit ? offset ?";
        List<Object> params = new ArrayList<>(sp.params);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, mapper, params.toArray());
    }

    // ---------------------------
    // Поиск/фильтрация по ID владельца (owner.ID)
    // ---------------------------

    public long count(String status, String incidentType, Long ownerId, Long freqId, Long fromMs, Long toMs) {
        Sql sp = buildWhere(status, incidentType, ownerId, freqId, fromMs, toMs, true);
        Long cnt = jdbcTemplate.queryForObject(sp.sql, Long.class, sp.params.toArray());
        return cnt == null ? 0L : cnt;
    }

    public List<IntegrityIncident> search(String status, String incidentType, Long ownerId, Long freqId, Long fromMs, Long toMs,
                                          int limit, int offset) {
        Sql sp = buildWhere(status, incidentType, ownerId, freqId, fromMs, toMs, false);
        String sql = sp.sql + " order by created_at_ms desc, id desc limit ? offset ?";
        List<Object> params = new ArrayList<>(sp.params);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, mapper, params.toArray());
    }

    private Sql buildWhere(String status, String incidentType, Long ownerId, Long freqId, Long fromMs, Long toMs, boolean countOnly) {
        StringBuilder sql = new StringBuilder(countOnly
            ? "select count(*) from integrity_incident where 1=1"
            : "select i.id, i.created_at_ms, i.detected_by, i.incident_type, i.freq_id, i.last_log_id, i.expected_hash, i.actual_hash, i.status, i.comment, i.resolved_at_ms, i.resolved_by, " +
            "COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
            "from integrity_incident i " +
            "left join freq f on f.ID = i.freq_id " +
            "left join site s on s.ID = f.IDsite " +
            "where 1=1");

        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" and status=?");
            params.add(status.trim());
        }
        if (incidentType != null && !incidentType.isBlank()) {
            sql.append(" and incident_type=?");
            params.add(incidentType.trim());
        }

        // Поиск по ID владельца (owner.ID)
        if (ownerId != null) {
            sql.append(" and freq_id in (select f2.ID from freq f2 left join site s2 on s2.ID = f2.IDsite where (f2.IDowner = ? or s2.IDowner = ?))");
            params.add(ownerId);
            params.add(ownerId);
        }
        if (freqId != null) {
            sql.append(" and freq_id=?");
            params.add(freqId);
        }
        if (fromMs != null) {
            sql.append(" and created_at_ms>=?");
            params.add(fromMs);
        }
        if (toMs != null) {
            sql.append(" and created_at_ms<=?");
            params.add(toMs);
        }

        return new Sql(sql.toString(), params);
    }

    private static final class Sql {
        final String sql;
        final List<Object> params;
        private Sql(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}

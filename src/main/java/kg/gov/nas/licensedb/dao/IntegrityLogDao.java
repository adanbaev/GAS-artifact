package kg.gov.nas.licensedb.dao;

import kg.gov.nas.licensedb.dto.IntegrityLogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class IntegrityLogDao {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<IntegrityLogEntry> mapper = new RowMapper<>() {
        @Override
        public IntegrityLogEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            return IntegrityLogEntry.builder()
                .id(rs.getLong("id"))
                .eventMs(rs.getLong("event_ms"))
                .actorUsername(rs.getString("actor_username"))
                .action(rs.getString("action"))
                .ownerId(rs.getObject("owner_id") == null ? null : rs.getLong("owner_id"))
                .freqId(rs.getObject("freq_id") == null ? null : rs.getLong("freq_id"))
                .dataHash(rs.getString("data_hash"))
                .prevHash(rs.getString("prev_hash"))
                .chainHash(rs.getString("chain_hash"))
                .build();
        }
    };

    // ---------------------------
    // CHECKPOINT event batch
    // ---------------------------

    public Long getMaxEventId() {
        return jdbcTemplate.queryForObject("select max(id) from freq_integrity_event", Long.class);
    }

    public void insertEventsBatch(java.util.List<EventRow> rows) {
        jdbcTemplate.batchUpdate(
            "insert into freq_integrity_event (event_ms, actor_username, action, freq_id, data_hash) values (?,?,?,?,?)",
            rows,
            1000,
            (ps, r) -> {
                ps.setLong(1, r.eventMs);
                ps.setString(2, r.actorUsername);
                ps.setString(3, r.action);
                if (r.freqId == null) ps.setNull(4, Types.BIGINT);
                else ps.setLong(4, r.freqId);
                ps.setString(5, r.dataHash);
            }
        );
    }

    public static final class EventRow {
        public final long eventMs;
        public final String actorUsername;
        public final String action;
        public final Long freqId;
        public final String dataHash;

        public EventRow(long eventMs, String actorUsername, String action, Long freqId, String dataHash) {
            this.eventMs = eventMs;
            this.actorUsername = actorUsername;
            this.action = action;
            this.freqId = freqId;
            this.dataHash = dataHash;
        }
    }

    // ---------------------------
    // STRICT chain state
    // ---------------------------

    public String lockAndGetLastHash() {
        return jdbcTemplate.queryForObject(
            "select last_hash from integrity_chain_state where id=1 for update",
            String.class
        );
    }

    public void updateLastHash(String lastHash) {
        jdbcTemplate.update(
            "update integrity_chain_state set last_hash=? where id=1",
            lastHash
        );
    }

    public void insertLog(long eventMs, String actor, String action, Long freqId,
                          String dataHash, String prevHash, String chainHash) {
        jdbcTemplate.update(
            "insert into freq_integrity_log (event_ms, actor_username, action, freq_id, data_hash, prev_hash, chain_hash) " +
                "values (?,?,?,?,?,?,?)",
            eventMs, actor, action, freqId, dataHash, prevHash, chainHash
        );
    }

    public List<IntegrityLogEntry> findAllOrdered() {
        return jdbcTemplate.query(
            "select l.id, l.event_ms, l.actor_username, l.action, l.freq_id, l.data_hash, l.prev_hash, l.chain_hash, " +
                "COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_log l " +
                "left join freq f on f.ID = l.freq_id " +
                "left join site s on s.ID = f.IDsite " +
                "order by l.id asc",
            mapper
        );
    }

    /**
     * Берём последние записи по freq_id для проверки “текущее состояние == последнее залогированное”.
     * ВАЖНО: сортируем по max_id desc, чтобы выборка была детерминированной.
     */
    public List<IntegrityLogEntry> findLatestPerFreq(int limit) {
        String sql =
            "select l.id, l.event_ms, l.actor_username, l.action, l.freq_id, l.data_hash, l.prev_hash, l.chain_hash, " +
                "COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_log l " +
                "join (select freq_id, max(id) as max_id " +
                "      from freq_integrity_log where freq_id is not null group by freq_id) x " +
                "  on x.freq_id = l.freq_id and x.max_id = l.id " +
                "left join freq f on f.ID = l.freq_id " +
                "left join site s on s.ID = f.IDsite " +
                "order by l.id desc " +
                "limit ?";
        return jdbcTemplate.query(sql, mapper, limit);
    }

    public long insertEvent(long eventMs, String actor, String action, Long freqId, String dataHash) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "insert into freq_integrity_event (event_ms, actor_username, action, freq_id, data_hash) values (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, eventMs);
            ps.setString(2, actor);
            ps.setString(3, action);
            if (freqId == null) ps.setNull(4, Types.BIGINT);
            else ps.setLong(4, freqId);
            ps.setString(5, dataHash);
            return ps;
        }, kh);

        Number key = kh.getKey();
        return key == null ? -1L : key.longValue();
    }

    public CheckpointState lockCheckpointState() {
        return jdbcTemplate.queryForObject(
            "select last_checkpoint_hash, next_batch_no from integrity_checkpoint_state where id=1 for update",
            (rs, rowNum) -> new CheckpointState(rs.getString(1), rs.getLong(2))
        );
    }

    public void updateCheckpointState(String lastCheckpointHash, long nextBatchNo) {
        jdbcTemplate.update(
            "update integrity_checkpoint_state set last_checkpoint_hash=?, next_batch_no=? where id=1",
            lastCheckpointHash, nextBatchNo
        );
    }

    public void insertCheckpoint(long batchNo, long startEventId, long endEventId, int eventCount,
                                 String rootHash, String prevCheckpointHash, String checkpointHash) {
        jdbcTemplate.update(
            "insert into integrity_checkpoint (batch_no, start_event_id, end_event_id, event_count, root_hash, prev_checkpoint_hash, checkpoint_hash) " +
                "values (?,?,?,?,?,?,?)",
            batchNo, startEventId, endEventId, eventCount, rootHash, prevCheckpointHash, checkpointHash
        );
    }

    public void resetCheckpointTables() {
        jdbcTemplate.update("delete from freq_integrity_event");
        jdbcTemplate.update("delete from integrity_checkpoint");
        jdbcTemplate.update("update integrity_checkpoint_state set last_checkpoint_hash='GENESIS', next_batch_no=1 where id=1");
    }

    public static final class CheckpointState {
        public final String lastCheckpointHash;
        public final long nextBatchNo;

        public CheckpointState(String lastCheckpointHash, long nextBatchNo) {
            this.lastCheckpointHash = lastCheckpointHash;
            this.nextBatchNo = nextBatchNo;
        }
    }

    // ---------------------------
    // Для вкладки "Логи" (ADMIN): поиск/фильтрация
    // ---------------------------

    public List<IntegrityLogEntry> searchLogs(String source,
                                              String actorUsername,
                                              String action,
                                              Long freqId,
                                              Long fromMs,
                                              Long toMs,
                                              int limit,
                                              int offset) {

        // Совместимость со старым вызовом (без ownerId)
        return searchLogs(source, actorUsername, action, null, freqId, fromMs, toMs, limit, offset);
    }

    public long countLogs(String source,
                          String actorUsername,
                          String action,
                          Long freqId,
                          Long fromMs,
                          Long toMs) {

        // Совместимость со старым вызовом (без ownerId)
        return countLogs(source, actorUsername, action, null, freqId, fromMs, toMs);
    }

    /**
     * Поиск логов с дополнительным фильтром ownerId (owner.ID).
     */
    public List<IntegrityLogEntry> searchLogs(String source,
                                              String actorUsername,
                                              String action,
                                              Long ownerId,
                                              Long freqId,
                                              Long fromMs,
                                              Long toMs,
                                              int limit,
                                              int offset) {

        SqlWithParams sp = buildBaseSql(source, actorUsername, action, ownerId, freqId, fromMs, toMs, false);

        StringBuilder sql = new StringBuilder(sp.sql);
        sql.append(" order by event_ms desc, id desc limit ? offset ?");

        List<Object> params = new ArrayList<>(sp.params);
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), mapper, params.toArray());
    }

    public long countLogs(String source,
                          String actorUsername,
                          String action,
                          Long ownerId,
                          Long freqId,
                          Long fromMs,
                          Long toMs) {

        SqlWithParams sp = buildBaseSql(source, actorUsername, action, ownerId, freqId, fromMs, toMs, true);
        Long cnt = jdbcTemplate.queryForObject(sp.sql, Long.class, sp.params.toArray());
        return cnt == null ? 0L : cnt;
    }

    public List<String> findDistinctActions(String source) {
        String src = (source == null) ? "LOG" : source.trim();

        String sql;
        if ("EVENT".equalsIgnoreCase(src)) {
            sql = "select distinct action from freq_integrity_event where action is not null and action <> '' order by action asc";
        } else {
            sql = "select distinct action from freq_integrity_log where action is not null and action <> '' order by action asc";
        }

        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * Последняя запись по freqId из выбранного источника (LOG/EVENT).
     */
    public IntegrityLogEntry findLatestForFreq(String source, long freqId) {
        String src = (source == null) ? "LOG" : source.trim();

        String sql;
        if ("EVENT".equalsIgnoreCase(src)) {
            sql = "select e.id, e.event_ms, e.actor_username, e.action, e.freq_id, e.data_hash, " +
                "null as prev_hash, null as chain_hash, COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_event e " +
                "left join freq f on f.ID = e.freq_id " +
                "left join site s on s.ID = f.IDsite " +
                "where e.freq_id=? order by e.id desc limit 1";
        } else {
            sql = "select l.id, l.event_ms, l.actor_username, l.action, l.freq_id, l.data_hash, l.prev_hash, l.chain_hash, " +
                "COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_log l " +
                "left join freq f on f.ID = l.freq_id " +
                "left join site s on s.ID = f.IDsite " +
                "where l.freq_id=? order by l.id desc limit 1";
        }

        List<IntegrityLogEntry> rows = jdbcTemplate.query(sql, mapper, freqId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Предыдущая запись по freqId из выбранного источника (LOG/EVENT).
     * То есть "вторая" по свежести запись (нужна для отображения: было prevLogId -> стало lastLogId).
     */
    public IntegrityLogEntry findPreviousForFreq(String source, long freqId) {
        String src = (source == null) ? "LOG" : source.trim();

        String sql;
        if ("EVENT".equalsIgnoreCase(src)) {
            sql = "select e.id, e.event_ms, e.actor_username, e.action, e.freq_id, e.data_hash, " +
                "null as prev_hash, null as chain_hash, COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_event e " +
                "left join freq f on f.ID = e.freq_id " +
                "left join site s on s.ID = f.IDsite " +
                "where e.freq_id=? order by e.id desc limit 1 offset 1";
        } else {
            sql = "select l.id, l.event_ms, l.actor_username, l.action, l.freq_id, l.data_hash, l.prev_hash, l.chain_hash, " +
                "COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_log l " +
                "left join freq f on f.ID = l.freq_id " +
                "left join site s on s.ID = f.IDsite " +
                "where l.freq_id=? order by l.id desc limit 1 offset 1";
        }

        List<IntegrityLogEntry> rows = jdbcTemplate.query(sql, mapper, freqId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private SqlWithParams buildBaseSql(String source,
                                       String actorUsername,
                                       String action,
                                       Long ownerId,
                                       Long freqId,
                                       Long fromMs,
                                       Long toMs,
                                       boolean countOnly) {

        String src = (source == null) ? "LOG" : source.trim();

        String select;
        if (countOnly) {
            select = "EVENT".equalsIgnoreCase(src)
                ? "select count(*) from freq_integrity_event"
                : "select count(*) from freq_integrity_log";
        } else {
            select = "EVENT".equalsIgnoreCase(src)
                ? "select e.id, e.event_ms, e.actor_username, e.action, e.freq_id, e.data_hash, " +
                "null as prev_hash, null as chain_hash, COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_event e " +
                "left join freq f on f.ID = e.freq_id " +
                "left join site s on s.ID = f.IDsite"
                : "select l.id, l.event_ms, l.actor_username, l.action, l.freq_id, l.data_hash, l.prev_hash, l.chain_hash, " +
                "COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq_integrity_log l " +
                "left join freq f on f.ID = l.freq_id " +
                "left join site s on s.ID = f.IDsite";
        }

        StringBuilder sql = new StringBuilder(select);
        List<Object> params = new ArrayList<>();

        sql.append(" where 1=1");

        if (actorUsername != null && !actorUsername.isBlank()) {
            sql.append(" and actor_username = ?");
            params.add(actorUsername.trim());
        }

        if (action != null && !action.isBlank()) {
            sql.append(" and action = ?");
            params.add(action.trim());
        }

        // Поиск по ID владельца (owner.ID) — ВАЖНО: работать и при IDsite=0
        if (ownerId != null) {
            sql.append(" and freq_id in (")
                .append("select f2.ID ")
                .append("from freq f2 ")
                .append("left join site s2 on s2.ID = f2.IDsite ")
                .append("where (f2.IDowner = ? or s2.IDowner = ?)")
                .append(")");
            params.add(ownerId);
            params.add(ownerId);
        }

        if (freqId != null) {
            sql.append(" and freq_id = ?");
            params.add(freqId);
        }

        if (fromMs != null) {
            sql.append(" and event_ms >= ?");
            params.add(fromMs);
        }

        if (toMs != null) {
            sql.append(" and event_ms <= ?");
            params.add(toMs);
        }

        return new SqlWithParams(sql.toString(), params);
    }

    private static final class SqlWithParams {
        final String sql;
        final List<Object> params;

        private SqlWithParams(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}

package kg.gov.nas.licensedb.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Опциональная авто-инициализация индексов для журналов.
 *
 * Включается параметром: db.indexes.autocreate=true
 *
 * Важно: если у пользователя БД нет прав на CREATE INDEX,
 * приложение продолжит работу (ошибка будет только в логах).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DbIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${db.indexes.autocreate:false}")
    private boolean autoCreate;

    @PostConstruct
    public void init() {
        if (!autoCreate) return;

        try {
            ensureIndexIfTableExists(
                    "freq_integrity_log",
                    "idx_fil_actor_event",
                    "create index idx_fil_actor_event on freq_integrity_log (actor_username, event_ms)"
            );
            ensureIndexIfTableExists(
                    "freq_integrity_log",
                    "idx_fil_event",
                    "create index idx_fil_event on freq_integrity_log (event_ms)"
            );
            ensureIndexIfTableExists(
                    "freq_integrity_log",
                    "idx_fil_freq_event",
                    "create index idx_fil_freq_event on freq_integrity_log (freq_id, event_ms)"
            );

            ensureIndexIfTableExists(
                    "freq_integrity_event",
                    "idx_fie_actor_event",
                    "create index idx_fie_actor_event on freq_integrity_event (actor_username, event_ms)"
            );
            ensureIndexIfTableExists(
                    "freq_integrity_event",
                    "idx_fie_event",
                    "create index idx_fie_event on freq_integrity_event (event_ms)"
            );
            ensureIndexIfTableExists(
                    "freq_integrity_event",
                    "idx_fie_freq_event",
                    "create index idx_fie_freq_event on freq_integrity_event (freq_id, event_ms)"
            );

            log.info("DB index initializer finished.");
        } catch (Exception ex) {
            log.warn("DB index initializer failed: {}", ex.getMessage());
        }
    }

    private void ensureIndexIfTableExists(String table, String indexName, String createSql) {
        if (!tableExists(table)) {
            log.info("Table '{}' not found, skip index {}", table, indexName);
            return;
        }
        if (indexExists(table, indexName)) {
            return;
        }
        try {
            jdbcTemplate.execute(createSql);
            log.info("Created index {} on {}", indexName, table);
        } catch (Exception ex) {
            log.warn("Cannot create index {} on {}: {}", indexName, table, ex.getMessage());
        }
    }

    private boolean tableExists(String table) {
        Integer c = jdbcTemplate.queryForObject(
                "select count(1) from information_schema.tables where table_schema = database() and table_name = ?",
                Integer.class,
                table
        );
        return c != null && c > 0;
    }

    private boolean indexExists(String table, String indexName) {
        Integer c = jdbcTemplate.queryForObject(
                "select count(1) from information_schema.statistics where table_schema = database() and table_name = ? and index_name = ?",
                Integer.class,
                table,
                indexName
        );
        return c != null && c > 0;
    }
}

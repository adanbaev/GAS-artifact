package kg.gov.nas.licensedb.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Маленький DAO для сопоставления owner.ID <-> freq.ID.
 * Нужен для сценариев, когда пользователи работают по привычному ID владельца.
 */
@Repository
@RequiredArgsConstructor
public class OwnerLookupDao {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Получить список ID записей (freq.ID) по ID владельца (owner.ID).
     */
    public List<Long> findFreqIdsByOwnerId(long ownerId) {
        return jdbcTemplate.queryForList(
            "select f.ID from freq f left join site s on s.ID = f.IDsite where (f.IDowner = ? or s.IDowner = ?) order by f.ID asc",
            Long.class,
            ownerId,
            ownerId
        );
    }

    /**
     * Получить ID владельца (owner.ID) по ID записи (freq.ID).
     */
    public Long findOwnerIdByFreqId(long freqId) {
        List<Long> rows = jdbcTemplate.queryForList(
            "select COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) as owner_id " +
                "from freq f left join site s on s.ID = f.IDsite where f.ID = ? limit 1",
            Long.class,
            freqId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }
}

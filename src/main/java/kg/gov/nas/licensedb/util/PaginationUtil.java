package kg.gov.nas.licensedb.util;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

public class PaginationUtil<E> {
    public Page<E> fetchPage(
            final JdbcTemplate jt,
            final String sqlFetchRows,
            final PageRequest pageable,
            final RowMapper<E> rowMapper) {

        List<E> result = jt.query(sqlFetchRows, rowMapper);

        Long start = pageable.getOffset();
        Long end = Math.min((start + pageable.getPageSize()), result.size());
        return new PageImpl<>(result.subList(start.intValue(), end.intValue()), pageable, result.size());
    }
}

package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dto.FreqPattern;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FreqSearchServiceQueryBuilderTest {

    @Test
    void buildQuery_doesNotInlineUserInput_inSqlString() throws Exception {
        JdbcTemplate jt = mock(JdbcTemplate.class);
        FreqSearchService service = new FreqSearchService(jt);

        FreqPattern p = new FreqPattern();
        String injectedName = "%' OR 1=1 --";
        String injectedNumber = "1 OR 1=1";
        p.setName(injectedName);
        p.setNumber(injectedNumber);

        String select = "select * from owner ow left join site s on s.IDowner=ow.ID where true";

        Object qp = invokeBuildQuery(service, p, select, "");

        String sql = (String) readField(qp, "sql");
        Object[] params = (Object[]) readField(qp, "params");

        // 1) в SQL должны быть плейсхолдеры
        assertTrue(sql.contains("ow.Name like ?"), "SQL должен использовать параметр для ow.Name");
        assertTrue(sql.contains("ow.schet = ?"), "SQL должен использовать параметр для ow.schet");

        // 2) SQL НЕ должен содержать инъекционный ввод напрямую
        assertFalse(sql.contains(injectedName), "SQL не должен содержать пользовательский ввод name напрямую");
        assertFalse(sql.contains(injectedNumber), "SQL не должен содержать пользовательский ввод number напрямую");
        assertFalse(sql.contains("OR 1=1"), "SQL не должен содержать OR 1=1 как часть строки запроса");

        // 3) а параметры должны содержать ввод (это нормально и безопасно)
        assertTrue(Arrays.asList(params).contains("%" + injectedName + "%"), "Параметры должны содержать name как значение");
        assertTrue(Arrays.asList(params).contains(injectedNumber), "Параметры должны содержать number как значение");
    }

    private static Object invokeBuildQuery(FreqSearchService service,
                                          FreqPattern pattern,
                                          String select,
                                          String sortAndLimit) throws Exception {
        Method m = FreqSearchService.class.getDeclaredMethod("buildQuery", FreqPattern.class, String.class, String.class);
        m.setAccessible(true);
        return m.invoke(service, pattern, select, sortAndLimit);
    }

    private static Object readField(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    }
}

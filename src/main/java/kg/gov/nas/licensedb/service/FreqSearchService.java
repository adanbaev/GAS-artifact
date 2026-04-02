package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dto.FreqExportModel;
import kg.gov.nas.licensedb.dto.FreqPattern;
import kg.gov.nas.licensedb.dto.FreqResult;
import kg.gov.nas.licensedb.dto.FreqView;
import kg.gov.nas.licensedb.enums.*;
import kg.gov.nas.licensedb.util.FreqUtil;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor

public class FreqSearchService {
    private final JdbcTemplate jdbcTemplate;

    public List<FreqResult> get(@NonNull FreqPattern pattern){
        String select =
                "select ow.Name, ow.schet, s.SiteName, f.nominal, f.band, f.ID, ow.ID ownerId, ow.type ownerType\n" +
                        "from owner ow\n" +
                        "left join site s on s.IDowner = ow.ID\n" +
                        ( pattern.isByOne()
                                ? "LEFT JOIN freq f ON f.ID = (\n" +
                                "    SELECT ID\n" +
                                "    FROM freq fa \n" +
                                "    WHERE fa.IDsite = s.id\n" +
                                "    LIMIT 1\n" +
                                ")\n"
                                : "left join freq f on f.IDsite = s.ID\n") +
                        "where \n" +
                        "true \n";
        QueryAndParams qp = buildQuery(pattern, select, "");
        List<FreqResult> result = jdbcTemplate.query(qp.sql, qp.params, getRowMapper());


        return result;
    }

    public List<FreqExportModel> getAll(@NonNull FreqPattern pattern){
        String select =
                "select ow.Name, ow.schet, s.SiteName, f.nominal, f.band, f.ID, ow.ID ownerId, ow.type ownerType,\n" +
                        " ow.LICNUMnum, ow.telefon, ow.fax, ow.passport, ow.area, ow.town, ow.street, ow.house, \n" +
                        " ow.flat,ow.date_v0,ow.date_v1,ow.date_v2,ow.date_ok0, ow.date_ok1, ow.date_ok2, ow.date_p0, ow.date_p1,ow.date_p2," +
                        " ow.date_pr0, ow.date_pr1, ow.date_pr2, ow.state, ow.dopolnit, ow.TypeOfUsing, s.longtitude0,s.longtitude1,s.longtitude2," +
                        " s.latitude0,s.latitude1,s.latitude2,s.CALLsign,s.h_umora,s.TransType,s.RECV_Sencitivity," +
                        " s.TransNum, s.AntName, s.AntType,s.AntKU,s.AntKUrecv,s.ISZ,s.dolg_orbit,s.beamwidth,s.highlight,s.freqStable," +
                        " s.RecvrType, s.polar,s.RECV_AntType,s.RECV_highlight,f.type,f.mode,f.Obozn,f.mob_stan," +
                        " f.SatRadius, f.deviation, f.channel,f.SNCH \n" +
                        "from owner ow\n" +
                        "left join site s on s.IDowner = ow.ID\n" +
                        ( pattern.isByOne()
                                ? "LEFT JOIN freq f ON f.ID = (\n" +
                                "    SELECT ID\n" +
                                "    FROM freq fa \n" +
                                "    WHERE fa.IDsite = s.id\n" +
                                "    LIMIT 1\n" +
                                ")\n"
                                : "left join freq f on f.IDsite = s.ID\n") +
                        "where \n" +
                        "true \n";

        QueryAndParams qp = buildQuery(pattern, select, "");
        List<FreqExportModel> result = jdbcTemplate.query(qp.sql, qp.params, getExportRowMapper());

        return result;
    }

    public Page<FreqResult> get(@NonNull FreqPattern pattern, PageRequest pageRequest){
        String select =
                "select ow.Name, ow.schet, s.SiteName, f.nominal, f.band, f.ID, ow.ID ownerId, ow.type ownerType \n" +
                        "from owner ow\n" +
                        "left join site s on s.IDowner = ow.ID\n" +
                        ( pattern.isByOne()
                                ? "LEFT JOIN freq f ON f.ID = (\n" +
                                "    SELECT ID\n" +
                                "    FROM freq fa \n" +
                                "    WHERE fa.IDsite = s.id\n" +
                                "    LIMIT 1\n" +
                                ")"
                                : "left join freq f on f.IDsite = s.ID\n") +
                        "where \n" +
                        "true \n";

        String sortAndLimit = "\n" + " order by ow.ID desc" +
                "\n" + " limit " + pageRequest.getOffset() + "," + pageRequest.getPageSize();

        QueryAndParams qp = buildQuery(pattern, select, sortAndLimit);
        List<FreqResult> result = jdbcTemplate.query(qp.sql, qp.params, getRowMapper());
        return new PageImpl<>(result, pageRequest, count(pattern));
    }

    private static final class QueryAndParams {
        private final String sql;
        private final Object[] params;

        private QueryAndParams(String sql, java.util.List<Object> params) {
            this.sql = sql;
            this.params = params.toArray();
        }
    }

    private int count(FreqPattern pattern) {
        String select =
                "select count(*)\n" +
                        "from owner ow\n" +
                        "left join site s on s.IDowner = ow.ID\n" +
                        ( pattern.isByOne() ? "\n" : "left join freq f on f.IDsite = s.ID\n") +
                        "where \n" +
                        "true \n";
        QueryAndParams qp = buildQuery(pattern, select, "");
        Integer c = jdbcTemplate.queryForObject(qp.sql, qp.params, Integer.class);
        return c == null ? 0 : c;

    }

    private QueryAndParams buildQuery(FreqPattern pattern, String select, String sortAndLimit) {
        StringBuilder query = new StringBuilder(select);
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (pattern.getOwnerId() != null) {
            query.append(" and ow.ID = ?");
            params.add(pattern.getOwnerId());
        }

        if (pattern.getName() != null && !pattern.getName().isBlank()) {
            query.append(" and ow.Name like ?");
            params.add("%" + pattern.getName().trim() + "%");
        }

        if (pattern.getNumber() != null && !pattern.getNumber().isBlank()) {
            // Раньше было: ow.schet = <сырой ввод>
            // Теперь безопасно. Если нужно строго число — можно парсить.
            String number = pattern.getNumber().trim();
            query.append(" and ow.schet = ?");
            params.add(number);
        }

        if (pattern.getIssueDate() != null) {
            java.time.LocalDate d = pattern.getIssueDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();

            query.append(" and ow.date_v0 = ? and ow.date_v1 = ? and ow.date_v2 = ?");
            params.add(d.getDayOfMonth());
            params.add(d.getMonthValue());
            params.add(d.getYear());
        }

        if (pattern.getExpireDate() != null) {
            java.time.LocalDate d = pattern.getExpireDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();

            query.append(" and ow.date_ok0 = ? and ow.date_ok1 = ? and ow.date_ok2 = ?");
            params.add(d.getDayOfMonth());
            params.add(d.getMonthValue());
            params.add(d.getYear());
        }

        if (pattern.getRegDate() != null) {
            java.time.LocalDate d = pattern.getRegDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();

            query.append(" and ow.date_p0 = ? and ow.date_p1 = ? and ow.date_p2 = ?");
            params.add(d.getDayOfMonth());
            params.add(d.getMonthValue());
            params.add(d.getYear());
        }

        if (pattern.getInvoiceDate() != null) {
            java.time.LocalDate d = pattern.getInvoiceDate().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();

            query.append(" and ow.date_pr0 = ? and ow.date_pr1 = ? and ow.date_pr2 = ?");
            params.add(d.getDayOfMonth());
            params.add(d.getMonthValue());
            params.add(d.getYear());
        }

        if (pattern.getState() != null) {
            query.append(" and ow.state = ?");
            params.add(pattern.getState().getCode());
        }

        if (pattern.getRegion() != null) {
            query.append(" and ow.area = ?");
            params.add(pattern.getRegion().getCode());
        }

        if (pattern.getType() != null) {
            query.append(" and ow.type = ?");
            params.add(pattern.getType().getCode());
        }

        if (pattern.getLicNumber() != null) {
            query.append(" and ow.LICNUMnum = ?");
            params.add(pattern.getLicNumber());
        }

        if (pattern.getPoint() != null && !pattern.getPoint().isBlank()) {
            query.append(" and s.SiteName like ?");
            params.add("%" + pattern.getPoint().trim() + "%");
        }

        if (pattern.getLatitude0() != null) { query.append(" and s.latitude0 = ?"); params.add(pattern.getLatitude0()); }
        if (pattern.getLatitude1() != null) { query.append(" and s.latitude1 = ?"); params.add(pattern.getLatitude1()); }
        if (pattern.getLatitude2() != null) { query.append(" and s.latitude2 = ?"); params.add(pattern.getLatitude2()); }

        // В БД колонка написана "longtitude*" (как в твоём коде) — оставляем как есть
        if (pattern.getLongitude0() != null) { query.append(" and s.longtitude0 = ?"); params.add(pattern.getLongitude0()); }
        if (pattern.getLongitude1() != null) { query.append(" and s.longtitude1 = ?"); params.add(pattern.getLongitude1()); }
        if (pattern.getLongitude2() != null) { query.append(" and s.longtitude2 = ?"); params.add(pattern.getLongitude2()); }

        if (pattern.getNominal() != null) {
            query.append(" and f.nominal = ?");
            params.add(kg.gov.nas.licensedb.util.FreqUtil.mHzToKHz(pattern.getNominal()));
        }

        if (pattern.getBand() != null) {
            query.append(" and f.band = ?");
            params.add(pattern.getBand());
        }

        if (sortAndLimit != null && !sortAndLimit.isBlank()) {
            query.append(sortAndLimit);
        }

        return new QueryAndParams(query.toString(), params);
    }


    private RowMapper<FreqResult> getRowMapper(){
        return (rs, i) -> FreqResult.builder()
                .ownerId(rs.getLong("ownerId"))
                .ownerName(rs.getString("Name"))
                .ownerType(OwnerType.fromCode(rs.getInt("ownerType")))
                .number(rs.getString("schet"))
                .point(rs.getString("SiteName"))
                .nominal(FreqUtil.kHzToMHz(rs.getDouble("nominal")))
                .band(rs.getDouble("band"))
                .freqId(rs.getLong("ID"))
                .build();
    }

    private RowMapper<FreqExportModel> getExportRowMapper(){

        return (rs, i) -> FreqExportModel.builder()
                .ownerId(rs.getLong("ownerId"))
                .ownerType(OwnerType.fromCode(rs.getInt("ownerType")))
                .licNumber(rs.getInt("ow.LICNUMnum"))
                .ownerName(rs.getString("Name"))
                .phone(rs.getString("telefon"))
                .fax(rs.getString("telefon"))
                .number(rs.getString("schet"))
                .passport(rs.getString("passport"))
                .region(Region.fromCode(rs.getInt("area")))
                .town(rs.getString("town"))
                .street(rs.getString("street"))
                .house(rs.getString("house"))
                .flat(rs.getString("flat"))
                .issueDate(getIssueDate(rs))
                .expireDate(getExpireDate(rs))
                .regDate(getRegDate(rs))
                .invoiceDate(getInvoiceDate(rs))
                .state(State.fromCode(rs.getInt("state")))
                .desc(rs.getString("dopolnit"))
                .typeOfUsing(rs.getString("TypeOfUsing"))
                .point(rs.getString("SiteName"))
                .vd(getVD(rs))
                .sh(getSH(rs))
                .callSign(rs.getString("CALLsign"))
                .absEarth(rs.getDouble("h_umora"))
                .transType(rs.getString("TransType"))
                .receiverSensitivity(rs.getDouble("RECV_Sencitivity"))
                .transNumber(rs.getString("TransNum"))
                .antName(rs.getString("AntName"))
                .antType(rs.getString("AntType"))
                .antKU(rs.getDouble("AntKU"))
                .antKUrecv(rs.getDouble("AntKUrecv"))
                .isz(rs.getString("ISZ"))
                .dolgOrbit(rs.getDouble("dolg_orbit"))
                .beamWidth(rs.getDouble("beamwidth"))
                .highlight(rs.getDouble("highlight"))
                .freqStable(rs.getDouble("freqStable"))
                .recvrType(rs.getString("RecvrType"))
                .polar(getFreqPolar(rs))
                .receiverAntType(rs.getString("RECV_AntType"))
                .receiverHighlight(rs.getDouble("RECV_highlight"))
                .nominal(FreqUtil.kHzToMHz(rs.getDouble("nominal")))
                .type(getFreqType(rs))
                .band(rs.getDouble("band"))
                .mode(FreqMode.fromCode(rs.getInt("mode")))
                .meaning(rs.getString("Obozn"))
                .mobStan(rs.getInt("mob_stan"))
                .satRadius(rs.getDouble("SatRadius"))
                .deviation(rs.getDouble("deviation"))
                .channel(rs.getInt("channel"))
                .snch(rs.getDouble("SNCH"))
                .freqId(rs.getLong("ID"))
                .build();
    }

    private FreqType getFreqType(ResultSet rs){
        try {
            Long l = rs.getLong("type");
            if(l < 6 && l >=0 ){
                return FreqType.fromCode(l.intValue());
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private Polar getFreqPolar(ResultSet rs){
        try {
            Long l = rs.getLong("polar");
            if(l < 10 && l >=0 ){
                return Polar.fromCode(l.intValue());
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private LocalDate getIssueDate(ResultSet rs){

        try {
            int issueYear = rs.getInt("date_v2");
            int issueMonth = rs.getInt("date_v1");
            int issueDay = rs.getInt("date_v0");
            if ((issueYear >0 && issueYear < 13) && issueMonth != 0 && (issueDay > 0 && issueDay < 32)) {
                if(issueMonth == 2 && issueDay > 29){
                    return null;
                }

                LocalDate issueDate = LocalDate.of(issueYear, issueMonth, issueDay);
                return issueDate;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private LocalDate getExpireDate(ResultSet rs){
        try {
            int expireYear = rs.getInt("date_ok2");
            int expireMonth = rs.getInt("date_ok1");
            int expireDay = rs.getInt("date_ok0");
            if ((expireYear >0 && expireYear < 13) && expireMonth != 0 && (expireDay > 0 && expireDay < 32)) {
                LocalDate expireDate = LocalDate.of(expireYear, expireMonth, expireDay);
                return expireDate;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private LocalDate getRegDate(ResultSet rs){
        try {
            int regYear = rs.getInt("date_p2");
            int regMonth = rs.getInt("date_p1");
            int regDay = rs.getInt("date_p0");
            if ((regYear >0 && regYear < 13) && regMonth != 0 && (regDay > 0 && regDay < 32)) {
                LocalDate regDate = LocalDate.of(regYear, regMonth, regDay);
                return regDate;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private LocalDate getInvoiceDate(ResultSet rs){
        try {
            int invoiceYear = rs.getInt("date_pr2");
            int invoiceMonth = rs.getInt("date_pr1");
            int invoiceDay = rs.getInt("date_pr0");
            if (invoiceYear != 0 && invoiceMonth != 0 && invoiceDay != 0) {
                LocalDate invoiceDate = LocalDate.of(invoiceYear, invoiceMonth, invoiceDay);
                return invoiceDate;
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private String getVD(ResultSet rs){
        try {
            return rs.getInt("longtitude0") + " " + rs.getInt("longtitude1")
                    + " " + rs.getInt("longtitude2");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

    private String getSH(ResultSet rs){
        try {
            return rs.getInt("latitude0") + " " + rs.getInt("latitude1")
                    + " " + rs.getInt("latitude2");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        return null;
    }

}

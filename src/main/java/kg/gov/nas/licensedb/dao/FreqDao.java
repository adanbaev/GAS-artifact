package kg.gov.nas.licensedb.dao;

import kg.gov.nas.licensedb.dto.FreqModel;
import kg.gov.nas.licensedb.dto.FreqView;
import kg.gov.nas.licensedb.dto.OwnerModel;
import kg.gov.nas.licensedb.dto.SiteModel;
import kg.gov.nas.licensedb.enums.*;
import kg.gov.nas.licensedb.util.FreqUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Repository
public class FreqDao {
    private final JdbcTemplate jdbcTemplate;

    private Long getNullableLong(ResultSet rs, String col) throws SQLException {
        Object o = rs.getObject(col);
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Важно для MySQL: executeUpdate() может вернуть 0, если значения не изменились.
     * Это не ошибка, если строка существует.
     */
    private boolean existsById(Connection conn, long id) throws SQLException {
        String sql = "SELECT 1 FROM freq WHERE ID = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int getUIntAsInt(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        long v = rs.getLong(col);
        if (rs.wasNull()) return 0;

        // MySQL int unsigned: 0..4294967295
        // Java int: -2147483648..2147483647
        // Если число > Integer.MAX_VALUE, интерпретируем как signed 32-bit (wrap)
        if (v > Integer.MAX_VALUE) {
            return (int) (v - 4294967296L); // 2^32
        }
        return (int) v;
    }


        private final String selectFreqView =
            "select COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0)) ownerId, f.ID freqId, f.IDsite siteId, \n" +
            "ow.Name ownerName, ow.telefon, ow.fax, ow.passport, ow.town, ow.street, ow.house, ow.flat, ow.LICNUMnum,\n" +
            "ow.date_v0, ow.date_v1, ow.date_v2, ow.date_ok0, ow.date_ok1, ow.date_ok2, ow.date_p0, ow.date_p1, ow.date_p2,\n" +
            "ow.date_pr0, ow.date_pr1, ow.date_pr2, ow.state, ow.dopolnit, ow.TypeOfUsing, ow.dopolnit_p, ow.schet,\n" +
            "ow.HasCertificateFlag, ow.HeadFlags, ow.area, ow.type ownerType,\n" +
            "s.SiteName, s.latitude0, s.latitude1, s.latitude2, s.longtitude0, s.longtitude1, s.longtitude2,\n" +
            "s.CALLsign, s.h_umora, s.TransType, s.TransNum, s.AntName, s.AntType,\n" +
            "s.AntKU, s.AntKUrecv, s.ISZ, s.dolg_orbit, s.beamwidth, s.highlight, s.freqStable, s.RecvrType, s.polar,\n" +
            "s.RECV_AntType, s.RECV_highlight, s.RECV_Sencitivity,\n" +
            "f.deviation, f.channel, f.SNCH,\n" +
            "f.nominal, f.type, f.band, f.mode, f.Obozn, f.mob_stan, f.inco, f.SatRadius, f.signature\n" +
            "from freq f\n" +
            "left join site s on s.ID = f.IDsite\n" +
            "left join owner ow on ow.ID = COALESCE(NULLIF(f.IDowner,0), NULLIF(s.IDowner,0))\n" +
            "where f.ID = ?";

    private int getIntCompat(ResultSet rs, String col) throws SQLException {
        try {
            return rs.getInt(col);
        } catch (java.sql.SQLDataException ex) {
            // например, MySQL INT UNSIGNED = 4294967295
            long v = rs.getLong(col);
            if (rs.wasNull()) return 0;

            // перевод UINT32 в signed int (wrap)
            if (v > Integer.MAX_VALUE) {
                return (int) (v - 4294967296L); // 2^32
            }
            return (int) v;
        }
    }


    public FreqView getById(Long freqId) throws SQLException {
        boolean found = false;
        FreqView freqView = new FreqView();
        OwnerModel ownerModel = new OwnerModel();
        SiteModel siteModel = new SiteModel();
        FreqModel freqModel = new FreqModel();

        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectFreqView)) {
            stmt.setLong(1, freqId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    found = true;
                    ownerModel.setOwnerId(getNullableLong(rs, "ownerId"));
                    freqModel.setFreqId(getNullableLong(rs, "freqId"));
                    siteModel.setSiteId(getNullableLong(rs, "siteId"));
                    ownerModel.setOwnerName(rs.getString("ownerName"));
                    ownerModel.setPhone(rs.getString("telefon"));
                    ownerModel.setFax(rs.getString("fax"));
                    ownerModel.setPassport(rs.getString("passport"));
                    ownerModel.setTown(rs.getString("town"));
                    ownerModel.setStreet(rs.getString("street"));
                    ownerModel.setHouse(rs.getString("house"));
                    ownerModel.setFlat(rs.getString("flat"));

                    int issueYear = getIntCompat(rs, "date_v2");
                    int issueMonth = getIntCompat(rs, "date_v1");
                    int issueDay = getIntCompat(rs, "date_v0");


                    if (issueYear != 0 && issueMonth != 0 && issueDay != 0) {
                        try {
                            LocalDate issueDate = LocalDate.of(issueYear, issueMonth, issueDay);
                            ownerModel.setIssueDate(issueDate);
                        } catch (java.time.DateTimeException ignored) {
                            // в базе встречаются некорректные значения (например, day=99)
                            ownerModel.setIssueDate(null);
                        }
                    }


                    int expireYear = getIntCompat(rs,"date_ok2");
                    int expireMonth = getIntCompat(rs,"date_ok1");
                    int expireDay = getIntCompat(rs,"date_ok0");
                    if (expireYear != 0 && expireMonth != 0 && expireDay != 0) {
                        try {
                            LocalDate expireDate = LocalDate.of(expireYear, expireMonth, expireDay);
                            ownerModel.setExpireDate(expireDate);
                        } catch (java.time.DateTimeException ignored) {
                            ownerModel.setExpireDate(null);
                        }
                    }


                    int regYear = getIntCompat(rs,"date_p2");
                    int regMonth = getIntCompat(rs,"date_p1");
                    int regDay = getIntCompat(rs,"date_p0");
                    if (regYear != 0 && regMonth != 0 && regDay != 0) {
                        try {
                            LocalDate regDate = LocalDate.of(regYear, regMonth, regDay);
                            ownerModel.setRegDate(regDate);
                        } catch (java.time.DateTimeException ignored) {
                            ownerModel.setRegDate(null);
                        }
                    }


                    int invoiceYear = getIntCompat(rs,"date_pr2");
                    int invoiceMonth = getIntCompat(rs,"date_pr1");
                    int invoiceDay = getIntCompat(rs,"date_pr0");
                    if (invoiceYear != 0 && invoiceMonth != 0 && invoiceDay != 0) {
                        try {
                            LocalDate invoiceDate = LocalDate.of(invoiceYear, invoiceMonth, invoiceDay);
                            ownerModel.setInvoiceDate(invoiceDate);
                        } catch (java.time.DateTimeException ignored) {
                            ownerModel.setInvoiceDate(null);
                        }
                    }


                    State state = State.fromCode(getIntCompat(rs,"state"));
                    ownerModel.setState(state);

                    ownerModel.setDesc(rs.getString("dopolnit"));
                    ownerModel.setTypeOfUsing(rs.getString("TypeOfUsing"));
                    ownerModel.setDescP(rs.getString("dopolnit_p"));
                    ownerModel.setNumber(rs.getString("schet"));
                    ownerModel.setLicNumber(getIntCompat(rs, "LICNUMnum"));


                    /*ownerModel.setInn(rs.getString("inn"));

                    String basis = rs.getString("basis");
                    if(basis != null && !basis.isBlank()){
                        try {
                            ownerModel.setBasis(OwnerBasis.valueOf(rs.getString("basis")));
                        }catch (IllegalArgumentException noEnum){

                        }

                    }*/

                    String hasCertificateFlag = rs.getString("HasCertificateFlag");
                    if (hasCertificateFlag != null && !hasCertificateFlag.isBlank()) {
                        int flag = Integer.parseInt(hasCertificateFlag);
                        ownerModel.setHasCertificate(flag != 0);
                    }

                    int headFlags = getIntCompat(rs,"HeadFlags");

                    switch (headFlags) {
                        case 9 -> {
                            ownerModel.setRegStatus(RegStatus.PUSKON);
                            ownerModel.setPurpose(Purpose.COM);
                        }
                        case 24 -> {
                            ownerModel.setRegStatus(RegStatus.REGISTR);
                            ownerModel.setPurpose(Purpose.COM);
                        }
                        case 12 -> {
                            ownerModel.setRegStatus(RegStatus.PEREREGISTR);
                            ownerModel.setPurpose(Purpose.COM);
                        }
                        case 3 -> {
                            ownerModel.setRegStatus(RegStatus.PUSKON);
                            ownerModel.setPurpose(Purpose.PRO);
                        }
                        case 18 -> {
                            ownerModel.setRegStatus(RegStatus.REGISTR);
                            ownerModel.setPurpose(Purpose.PRO);
                        }
                        case 6 -> {
                            ownerModel.setRegStatus(RegStatus.PEREREGISTR);
                            ownerModel.setPurpose(Purpose.PRO);
                        }
                    }

                    int area = getIntCompat(rs,"area");
                    ownerModel.setRegion(Region.fromCode(area));

                    int ownerType = getIntCompat(rs,"ownerType");
                    ownerModel.setOwnerType(OwnerType.fromCode(ownerType));

                    siteModel.setLatitude0(getIntCompat(rs, "latitude0"));
                    siteModel.setLatitude1(getIntCompat(rs, "latitude1"));
                    siteModel.setLatitude2(getIntCompat(rs, "latitude2"));
                    siteModel.setLongitude0(getIntCompat(rs, "longtitude0"));
                    siteModel.setLongitude1(getIntCompat(rs, "longtitude1"));
                    siteModel.setLongitude2(getIntCompat(rs, "longtitude2"));


                    siteModel.setPoint(rs.getString("SiteName"));

                    siteModel.setCallSign(rs.getString("CALLsign"));

                    siteModel.setAbsEarth(rs.getDouble("h_umora"));
                    siteModel.setTransType(rs.getString("TransType"));
                    siteModel.setTransNumber(rs.getString("TransNum"));
                    siteModel.setAntName(rs.getString("AntName"));
                    siteModel.setAntType(rs.getString("AntType"));
                    siteModel.setAntKU(rs.getDouble("AntKU"));
                    siteModel.setAntKUrecv(rs.getDouble("AntKUrecv"));
                    siteModel.setIsz(rs.getString("ISZ"));
                    siteModel.setDolgOrbit(rs.getDouble("dolg_orbit"));
                    siteModel.setBeamWidth(rs.getDouble("beamwidth"));
                    siteModel.setHighlight(rs.getDouble("highlight"));
                    siteModel.setFreqStable(rs.getDouble("freqStable"));
                    siteModel.setRecvrType(rs.getString("RecvrType"));
                    int polarCode = getIntCompat(rs, "polar");
                    Polar polar = null;
                    try {
                        polar = Polar.fromCode(polarCode);
                    } catch (Exception ignored) {
                        // встречается мусорный/сентинел код (например -1)
                    }
                    siteModel.setPolar(polar);

                    siteModel.setReceiverAntType(rs.getString("RECV_AntType"));
                    siteModel.setReceiverHighlight(rs.getDouble("RECV_highlight"));
                    siteModel.setReceiverSensitivity(rs.getDouble("RECV_Sencitivity"));

                    freqModel.setNominal(FreqUtil.kHzToMHz(rs.getDouble("nominal")));

// type (INT UNSIGNED) читаем безопасно
                    int typeCode = getUIntAsInt(rs, "type");
                    FreqType type = null;
                    try {
                        type = FreqType.fromCode(typeCode);
                    } catch (Exception ignored) {
                        // встречаются записи со значениями типа 4294967295 -> -1 (UINT_MAX)
                        // чтобы не падать на старых/битых данных
                    }
                    freqModel.setType(type);


                    freqModel.setBand(rs.getDouble("band"));

// mode (INT UNSIGNED) читаем безопасно
                    int modeCode = getUIntAsInt(rs, "mode");
                    FreqMode mode = null;
                    try {
                        mode = FreqMode.fromCode(modeCode);
                    } catch (Exception ignored) {
                    }
                    freqModel.setMode(mode);


                    freqModel.setMeaning(rs.getString("Obozn"));

// mob_stan (INT UNSIGNED)
                    freqModel.setMobStan(getUIntAsInt(rs, "mob_stan"));

// inco (INT UNSIGNED)
                    int incoInt = getUIntAsInt(rs, "inco");
                    freqModel.setInco(incoInt == 41);

                    freqModel.setSatRadius(rs.getDouble("SatRadius"));
                    freqModel.setDeviation(rs.getDouble("deviation"));

// channel (INT UNSIGNED)
                    freqModel.setChannel(getUIntAsInt(rs, "channel"));

                    freqModel.setSnch(rs.getDouble("SNCH"));
                    freqModel.setSignature(rs.getString("signature"));

                } else {
                    //trhow exception
                }
            }
        }

        if (!found) {
            return null;
        }

        freqView.setFreqModel(freqModel);
        freqView.setSiteModel(siteModel);
        freqView.setOwnerModel(ownerModel);
        return freqView;
    }

    public List<Long> getIdsBySiteId(Long siteId) {
        String select = "select ID from freq where IDsite = ?";
        List<Long> ids = new ArrayList<>();
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
             PreparedStatement stmt = conn.prepareStatement(select)) {
            stmt.setLong(1, siteId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("ID"));
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL exception while FreqDao.update. Exception message: " + e.getMessage());
            e.printStackTrace();
        }

        return ids;
    }

    public boolean update(FreqModel model, String signature) {
        if (model == null || model.getFreqId() == null) return false;
        boolean result = false;
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            conn.setAutoCommit(false);
            String sql = "UPDATE freq\n" +
                    "set \n" +
                    "    nominal            = ?, \n" +
                    "    band          = ?, \n" +
                    "    deviation           = ?, \n" +
                    "    channel               = ?, \n" +
                    "    SNCH    = ?, \n" +
                    "    Obozn = ?, \n" +
                    "    mob_stan                  = ?, \n" +
                    "    type           = ?, \n" +
                    "    inco                = ?, \n" +
                    "    mode    = ?, \n" +
                    "    SatRadius                  = ?, \n" +
                    "    signature                  = ? \n" +
                    "WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setDouble(1, FreqUtil.mHzToKHz(model.getNominal())); //nominal //1
                stmt.setDouble(2, model.getBand()); //band //2
                stmt.setDouble(3, model.getDeviation()); //deviation //3
                stmt.setInt(4, model.getChannel()); //channel //4
                stmt.setDouble(5, model.getSnch()); //SNCH //5
                stmt.setString(6, model.getMeaning()); //Obozn //6
                stmt.setInt(7, model.getMobStan()); //mob_stan //7
                int typeCode = (model.getType() == null) ? 0 : model.getType().getCode();
                stmt.setInt(8, typeCode); //type //8
                stmt.setInt(9, model.isInco() ? 41 : 0); //inco //9
                int modeCode = (model.getMode() == null) ? 0 : model.getMode().getCode();
                stmt.setInt(10, modeCode); //mode //10
                stmt.setDouble(11, model.getSatRadius()); //SatRadius //11
                stmt.setString(12, signature);
                stmt.setLong(13, model.getFreqId());
                int res = stmt.executeUpdate();
                if (res > 0) {
                    conn.commit();
                    result = true;
                } else if (res == 0 && existsById(conn, model.getFreqId())) {
                    // Значения могли совпасть с текущими; считаем успехом, чтобы можно было сформировать запись журнала целостности.
                    conn.commit();
                    result = true;
                } else {
                    conn.rollback();
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL exception while FreqDao.update. Exception message: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    public boolean updateAll(FreqView freqView) {
        boolean result = false;
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            conn.setAutoCommit(false);
            String sqlFreq = "UPDATE freq\n" +
                    "set \n" +
                    "    nominal            = ?, \n" +
                    "    band          = ?, \n" +
                    "    deviation           = ?, \n" +
                    "    channel               = ?, \n" +
                    "    SNCH    = ?, \n" +
                    "    Obozn = ?, \n" +
                    "    mob_stan                  = ?, \n" +
                    "    type           = ?, \n" +
                    "    inco                = ?, \n" +
                    "    mode    = ?, \n" +
                    "    SatRadius                  = ? \n" +
                    "WHERE ID = ?";

            String sqlSite = "UPDATE site\n" +
                    "set \n" +
                    "    SiteName            = ?, \n" +
                    "    CALLsign          = ?, \n" +
                    "    longtitude0           = ?, \n" +
                    "    longtitude1               = ?, \n" +
                    "    longtitude2    = ?, \n" +
                    "    latitude0          = ?, \n" +
                    "    latitude1 = ?, \n" +
                    "    latitude2                  = ?, \n" +
                    "    h_umora           = ?, \n" +
                    "    TransType                = ?, \n" +
                    "    TransNum    = ?, \n" +
                    "    freqStable         = ?, \n" +
                    "    AntName                  = ?, \n" +
                    "    AntType          = ?, \n" +
                    "    AntKU               = ?, \n" +
                    "    AntKUrecv              = ?, \n" +
                    "    highlight              = ?, \n" +
                    "    polar        = ?, \n" +
                    "    beamwidth          = ?, \n" +
                    "    RecvrType           = ?, \n" +
                    "    ISZ   = ?, \n" +
                    "    dolg_orbit              = ?, \n" +
                    "    RECV_highlight            = ?, \n" +
                    "    RECV_AntType            = ?, \n" +
                    "    RECV_Sencitivity       = ? \n" +
                    "WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sqlFreq);
                 PreparedStatement stmtSite = conn.prepareStatement(sqlSite)) {


                //freq
                stmt.setDouble(1, FreqUtil.mHzToKHz(freqView.getFreqModel().getNominal())); //nominal //1
                stmt.setDouble(2, freqView.getFreqModel().getBand()); //band //2
                stmt.setDouble(3, freqView.getFreqModel().getDeviation()); //deviation //3
                stmt.setInt(4, freqView.getFreqModel().getChannel()); //channel //4
                stmt.setDouble(5, freqView.getFreqModel().getSnch()); //SNCH //5
                stmt.setString(6, freqView.getFreqModel().getMeaning()); //Obozn //6
                stmt.setInt(7, freqView.getFreqModel().getChannel()); //mob_stan //7
                stmt.setInt(8, freqView.getFreqModel().getType().getCode()); //type //8
                stmt.setInt(9, freqView.getFreqModel().isInco() ? 41 : 0); //inco //9
                stmt.setInt(10, freqView.getFreqModel().getMode().getCode()); //mode //10
                stmt.setDouble(11, freqView.getFreqModel().getSatRadius()); //SatRadius //11
                stmt.setLong(12, freqView.getFreqModel().getFreqId());
                int res = stmt.executeUpdate();


                //site

                stmtSite.setString(1, freqView.getSiteModel().getPoint()); //SiteName //1
                stmtSite.setString(2, freqView.getSiteModel().getCallSign()); //CALLsign //2
                stmtSite.setInt(3, freqView.getSiteModel().getLongitude0()); //longtitude0 //3
                stmtSite.setInt(4, freqView.getSiteModel().getLongitude1());  //longtitude1 //4
                stmtSite.setInt(5, freqView.getSiteModel().getLongitude2()); //longtitude2 //5
                stmtSite.setInt(6, freqView.getSiteModel().getLatitude0()); //latitude0 //6
                stmtSite.setInt(7, freqView.getSiteModel().getLatitude1()); //latitude1 //7
                stmtSite.setInt(8, freqView.getSiteModel().getLatitude2()); //latitude0 //8


                stmtSite.setDouble(9, freqView.getSiteModel().getAbsEarth()); //h_umora //9

                stmtSite.setString(10, freqView.getSiteModel().getTransType()); //TransType //10

                stmtSite.setString(11, freqView.getSiteModel().getTransNumber()); //TransNum //11


                stmtSite.setDouble(12, freqView.getSiteModel().getFreqStable()); //freqStable //12

                stmtSite.setString(13, freqView.getSiteModel().getAntName()); //AntName //13
                stmtSite.setString(14, freqView.getSiteModel().getAntType()); //AntType //14


                stmtSite.setDouble(15, freqView.getSiteModel().getAntKU()); //AntKU //15


                stmtSite.setDouble(16, freqView.getSiteModel().getAntKUrecv()); //AntKUrecv //16


                stmtSite.setDouble(17, freqView.getSiteModel().getHighlight()); //highlight //17

                stmtSite.setInt(18, freqView.getSiteModel().getPolar().getCode()); //polar //18

                stmtSite.setDouble(19, freqView.getSiteModel().getBeamWidth()); //beamwidth //19

                stmtSite.setString(20, freqView.getSiteModel().getRecvrType()); //RecvrType //20
                stmtSite.setString(21, freqView.getSiteModel().getIsz()); //ISZ //21

                stmtSite.setDouble(22, freqView.getSiteModel().getDolgOrbit()); //dolg_orbit //22


                stmtSite.setDouble(23, freqView.getSiteModel().getReceiverHighlight()); //RECV_highlight //23

                stmtSite.setString(24, freqView.getSiteModel().getRecvrType()); //RECV_AntType //24


                stmtSite.setDouble(25, freqView.getSiteModel().getReceiverSensitivity()); //RECV_Sencitivity //25

                stmtSite.setLong(26, 565463214613215L);

                res += stmtSite.executeUpdate();

                if (res == 2) {
                    conn.commit();
                    conn.close();
                    result = true;
                } else {
                    conn.rollback();
                    conn.close();
                }


            }
        } catch (SQLException e) {
            System.out.println("SQL exception while FreqDao.update. Exception message: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
}

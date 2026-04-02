package kg.gov.nas.licensedb.dao;

import kg.gov.nas.licensedb.service.IntegrityService;
import kg.gov.nas.licensedb.dto.*;
import kg.gov.nas.licensedb.enums.*;
import kg.gov.nas.licensedb.util.FreqUtil;
import kg.gov.nas.licensedb.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Repository

public class OwnerDao {
    private final IntegrityService integrityService;
    private final JdbcTemplate jdbcTemplate;
    private final String ownerCopySql = "INSERT INTO \n" +
            "OWNER \n" +
            "(Name,addflag,date_pr0,date_pr1,date_pr2,date_p0,date_p1,date_p2,oldIDNUMBER,passport,dopolnit," +
            "HeadFlags,dopolnit_p,town,street,\n" +
            "house,flat,telefon,fax,date_v0,date_v1,date_v2,date_ok0,date_ok1,date_ok2,state,type,area," +
            "date_sch0,date_sch1,date_sch2,schet,TypeOfUsing,HasCertificateFlag,LICNUMnum) \n" +
            "SELECT\n" +
            "Name,addflag,date_pr0,date_pr1,date_pr2,date_p0,date_p1,date_p2,oldIDNUMBER,passport,dopolnit,HeadFlags," +
            "dopolnit_p,town,street,\n" +
            "house,flat,telefon,fax,date_v0,date_v1,date_v2,date_ok0,date_ok1,date_ok2,state,?," +
            "area,date_sch0,date_sch1,date_sch2,schet,TypeOfUsing,HasCertificateFlag,?\n" +
            "FROM\n" +
            "OWNER\n" +
            "WHERE ID = ?";
    private final String siteInsertSql = "INSERT INTO\n" +
            "SITE\n" +
            "(IDowner,addflag,SiteName,CALLsign,longtitude0,longtitude1,longtitude2,latitude0,latitude1,latitude2,h_umora,TransType,TransNum,TransPower,freqStable,TransPowerType,AntName,AntType,\n" +
            "AntKU,AntKUrecv,highlight,polar,beamwidth,RecvrType,ISZ,dolg_orbit,RECV_highlight,RECV_AntType,RECV_Sencitivity)\n" +
            "SELECT\n" +
            "?,addflag,SiteName,CALLsign,longtitude0,longtitude1,longtitude2,latitude0,latitude1,latitude2,h_umora,TransType,TransNum,TransPower,freqStable,TransPowerType,AntName,AntType,\n" +
            "AntKU,AntKUrecv,highlight,polar,beamwidth,RecvrType,ISZ,dolg_orbit,RECV_highlight,RECV_AntType,RECV_Sencitivity\n" +
            "FROM\n" +
            "SITE\n" +
            "WHERE ID = ?";
    private final String freqInsertSql = "INSERT INTO\n" +
            "freq\n" +
            "(IDsite,IDowner,addflag,nominal,band,deviation,channel,SNCH,Info,Obozn,mob_stan,type,inco,mode,asimut,SatRadius)\n" +
            "SELECT\n" +
            "?,?,addflag,nominal,band,deviation,channel,SNCH,Info,Obozn,mob_stan,type,inco,mode,asimut,SatRadius\n" +
            "FROM freq\n" +
            "WHERE IDsite =?";

    //private final String ownerDetailInsertSql = "INSERT INTO owner_detail (owner_id,inn,basis) VALUES(?,?,?)";

    private final String selectFull = "select ow.ID, ow.LICNUMnum,ow.type ownerType, ow.Name ownerName,ow.town,ow.street,ow.house,ow.flat,ow.date_ok0,ow.date_ok1,ow.date_ok2," +
            "ow.date_v0,ow.date_v1,ow.date_v2,ow.telefon, ow.fax, ow.HeadFlags, ow.dopolnit_p,  s.ID siteID, s.SiteName,s.longtitude0,s.longtitude1,s.longtitude2,s.TransType, s.TransPower," +
            "f.ID freqID, f.mode, f.nominal,f.band,f.Obozn,f.asimut," +
            " s.*, f.* \n" +
            "from owner ow \n" +
            "left join site s on s.IDowner = ow.ID\n" +
            "left join freq f on f.IDsite = s.ID\n" +
            //"left join owner_detail od on od.owner_id = ow.ID\n" +
            "where ow.ID = ?";

    /**
     * Важно для MySQL: executeUpdate() может вернуть 0, если значения не изменились.
     * Это не ошибка, если строка существует. Поэтому при res==0 дополнительно проверяем наличие записи.
     */
    private boolean existsById(Connection conn, String table, long id) throws SQLException {
        String sql = "SELECT 1 FROM " + table + " WHERE ID = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public OwnerModel getById(Long id) {
        OwnerModel ownerModel = new OwnerModel();
        String select = "select ow.ID, ow.type, ow.Name\n" +
                "from owner ow\n" +
                "where ow.ID = ?";

        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
             PreparedStatement stmt = conn.prepareStatement(select)) {
            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {

                    ownerModel.setOwnerId(rs.getLong("ID"));
                    ownerModel.setOwnerType(OwnerType.fromCode(rs.getInt("type")));
                    ownerModel.setOwnerName(rs.getString("Name"));
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL exception while OwnerDao.getById. Exception message: " + e.getMessage());
            e.printStackTrace();
        }

        return ownerModel;
    }

    public OwnerModel getByIdFull(Long id) {
        OwnerModel ownerModel = new OwnerModel();
        HashMap<Long, SiteModel> siteModelHashMap = new HashMap<>();
        int count = 0;
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
             PreparedStatement stmt = conn.prepareStatement(selectFull)) {
            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (count == 0) {
                        ownerModel.setOwnerId(rs.getLong("ID"));
                        ownerModel.setLicNumber(rs.getInt("LICNUMnum"));
                        ownerModel.setOwnerType(OwnerType.fromCode(rs.getInt("ownerType")));
                        ownerModel.setOwnerName(rs.getString("ownerName"));
                        //ownerModel.setInn(rs.getString("inn"));
                        ownerModel.setTown(rs.getString("town"));
                        ownerModel.setStreet(rs.getString("street"));
                        ownerModel.setHouse(rs.getString("house"));
                        ownerModel.setFlat(rs.getString("flat"));
                        ownerModel.setPhone(rs.getString("telefon"));
                        ownerModel.setFax(rs.getString("fax"));
                        ownerModel.setDescP(rs.getString("dopolnit_p"));

                        int expireYear = rs.getInt("date_ok2");
                        int expireMonth = rs.getInt("date_ok1");
                        int expireDay = rs.getInt("date_ok0");
                        if (expireYear != 0 && expireMonth != 0 && expireDay != 0) {
                            LocalDate expireDate = LocalDate.of(expireYear, expireMonth, expireDay);
                            ownerModel.setExpireDate(expireDate);
                        }

                        int issueYear = rs.getInt("date_v2");
                        int issueMonth = rs.getInt("date_v1");
                        int issueDay = rs.getInt("date_v0");

                        if (issueYear != 0 && issueMonth != 0 && issueDay != 0) {
                            LocalDate issueDate = LocalDate.of(issueYear, issueMonth, issueDay);
                            ownerModel.setIssueDate(issueDate);
                        }

                        /*String basis = rs.getString("basis");
                        if(basis != null && !basis.isBlank()){
                            try {
                                ownerModel.setBasis(OwnerBasis.valueOf(rs.getString("basis")));
                            }catch (IllegalArgumentException noEnum){

                            }
                        }*/
                        int headFlags = rs.getInt("HeadFlags");

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
                    }

                    Long siteId = rs.getLong("siteID");
                    if (!siteModelHashMap.containsKey(siteId)) {
                        SiteModel siteModel = new SiteModel();
                        siteModel.setSiteId(siteId);
                        siteModelHashMap.put(siteId, siteModel);
                    }

                    SiteModel currentSite = siteModelHashMap.get(siteId);
                    currentSite.setSiteId(siteId);
                    currentSite.setPoint(rs.getString("SiteName"));
                    currentSite.setLongitude0(rs.getInt("longtitude0"));
                    currentSite.setLongitude1(rs.getInt("longtitude1"));
                    currentSite.setLongitude2(rs.getInt("longtitude2"));
                    currentSite.setLatitude0(rs.getInt("latitude0"));
                    currentSite.setLatitude1(rs.getInt("latitude1"));
                    currentSite.setLatitude2(rs.getInt("latitude2"));
                    currentSite.setAntType(rs.getString("AntType"));
                    currentSite.setTransType(rs.getString("TransType"));
                    currentSite.setTransPower(rs.getDouble("TransPower"));
                    currentSite.setHighlight(rs.getDouble("highlight"));
                    Polar polar = Polar.fromCode(rs.getInt("polar"));
                    currentSite.setPolar(polar);
                    currentSite.setAntKU(rs.getDouble("AntKU"));
                    currentSite.setAbsEarth(rs.getDouble("h_umora"));
                    currentSite.setAsimut(rs.getDouble("asimut"));

                    FreqModel freqModel = new FreqModel();
                    freqModel.setFreqId(rs.getLong("freqID"));
                    freqModel.setMeaning(rs.getString("Obozn"));
                    freqModel.setBand(rs.getDouble("band"));
                    freqModel.setOriginNominal(rs.getDouble("nominal"));
                    freqModel.setOriginMode(rs.getInt("mode"));
                    freqModel.setSignature(rs.getString("signature"));
                    freqModel.setNominal(FreqUtil.kHzToMHz(rs.getDouble("nominal")));
                    FreqMode mode = FreqMode.fromCode(freqModel.getOriginMode());
                    double nominal = FreqUtil.kHzToMHz(freqModel.getOriginNominal());
                    switch (mode) {
                        case SIMPLEX -> {
                            freqModel.setTransfer(nominal);
                            freqModel.setReception(nominal);
                        }
                        case RECEIVER -> freqModel.setReception(nominal);
                        case BROADCAST -> freqModel.setTransfer(nominal);
                    }

                    currentSite.getFrequencies().add(freqModel);
                    //put it
                    count++;
                }
            }

            ownerModel.setSites(siteModelHashMap.values().stream().sorted(Comparator.comparing(SiteModel::getSiteId)).toList());
            if(ownerModel.getSites().size() > 1){
                ownerModel.setComplex(true);
            }

            for(SiteModel siteModel: ownerModel.getSites()){
                siteModel.setFrequencies(siteModel.getFrequencies().stream().sorted(Comparator.comparing(FreqModel::getFreqId)).toList());
            }

            if(ownerModel.getSites().size() > 1){
                String desc = "";
                String allowed = "";
                SiteModel item = ownerModel.getSites().get(1);

                int i = 0;
                for (FreqModel f: item.getFrequencies()){
                    if(i<2){
                        desc = desc + f.getDesignation() + ";";
                    }
                    i++;

                    if(f.getOriginNominal() !=0){
                        String prefix = f.getOriginMode() == 1 ? "Rx=" : "Tx=";
                        allowed = allowed + prefix + f.getOriginNominal() / 1000000 + "ГГц; ";
                    }
                }

                item.setDesc(desc);
                item.setAllowed(allowed);
            }else if(ownerModel.getSites().size() == 1){
                SiteModel item = ownerModel.getSites().get(0);
                if(item.getTransPower() != 0){
                    item.setTransPower(item.getTransPower()/1000);
                }

                if(item.getFrequencies().size() > 0){
                    FreqModel freqModel = item.getFrequencies().get(0);
                    int band = (int) freqModel.getBand();
                    item.setDesignationSimple(band + freqModel.getMeaning());
                }
            }

        } catch (SQLException e) {
            System.out.println("SQL exception while OwnerDao getByIdFull. Exception message: {}." + e.getMessage());
        }

        return ownerModel;
    }

    public boolean updateDetails(OwnerModel model) {

        boolean result = false;
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            conn.setAutoCommit(false);

            String sql = "UPDATE owner_detail\n" +
                    "set \n" +
                    "    inn            = ?, \n" +
                    "    basis            = ? \n" +
                    "WHERE owner_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, model.getInn());
                stmt.setString(2, model.getBasis().name());
                stmt.setLong(3, model.getOwnerId());

                int res = stmt.executeUpdate();
                if (res > 0) {
                    conn.commit();
                    result = true;
                } else if (res == 0 && existsById(conn, "owner", model.getOwnerId())) {
                    // Значения могли совпасть с текущими; считаем успехом, чтобы можно было создать запись журнала целостности.
                    conn.commit();
                    result = true;
                } else {
                    conn.rollback();
                }

            }
        } catch (SQLException e) {
            System.out.println("SQL exception while updating owner detail id: {}. Exception message: {}." + e.getMessage());
        }

        return result;
    }

    public boolean update(OwnerModel model) {

        if (model == null || model.getOwnerId() == null) {
            return false;
        }

        boolean result = false;
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            conn.setAutoCommit(false);

            String sql = "UPDATE owner\n" +
                    "set \n" +
                    "    date_pr0            = ?, \n" +
                    "    date_pr1          = ?, \n" +
                    "    date_pr2           = ?, \n" +
                    "    date_p0               = ?, \n" +
                    "    date_p1    = ?, \n" +
                    "    date_p2          = ?, \n" +
                    "    Name = ?, \n" +
                    "    passport                  = ?, \n" +
                    "    dopolnit           = ?, \n" +
                    "    HeadFlags                = ?, \n" +
                    "    dopolnit_p         = ?, \n" +
                    "    town                  = ?, \n" +
                    "    street          = ?, \n" +
                    "    house               = ?, \n" +
                    "    flat              = ?, \n" +
                    "    telefon              = ?, \n" +
                    "    fax        = ?, \n" +
                    "    date_v0          = ?, \n" +
                    "    date_v1           = ?, \n" +
                    "    date_v2   = ?, \n" +
                    "    date_ok0              = ?, \n" +
                    "    date_ok1            = ?, \n" +
                    "    date_ok2            = ?, \n" +
                    "    state            = ?, \n" +
                    "    area            = ?, \n" +
                    "    schet            = ?, \n" +
                    "    TypeOfUsing            = ?, \n" +
                    "    HasCertificateFlag       = ? \n" +
                    //"    inn            = ? \n" +
                    "WHERE ID = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                int invoiceYear, invoiceMonth, invoiceDay;
                invoiceYear = invoiceMonth = invoiceDay = 0;
                if (model.getIssueDate() != null) {
                    invoiceYear = model.getIssueDate().getYear();
                    invoiceMonth = model.getIssueDate().getMonthValue();
                    invoiceDay = model.getIssueDate().getDayOfMonth();
                }

                stmt.setInt(1, invoiceDay); //date_pr0 //1
                stmt.setInt(2, invoiceMonth); //date_pr1 //2
                stmt.setInt(3, invoiceYear); //date_pr1 //3

                int regYear, regMonth, regDay;
                regDay = regMonth = regYear = 0;
                if (model.getRegDate() != null) {
                    regDay = model.getRegDate().getDayOfMonth();
                    regMonth = model.getRegDate().getMonthValue();
                    regYear = model.getRegDate().getYear();
                }

                stmt.setInt(4, regDay);  //date_p0 //4
                stmt.setInt(5, regMonth); //date_p1 //5
                stmt.setInt(6, regYear); //date_p2 //6


                stmt.setString(7, model.getOwnerName()); //Name //7
                stmt.setString(8, model.getPassport()); //passport //8
                stmt.setString(9, model.getDesc()); //dopolnit //9
                stmt.setInt(10, model.getHeadFlags()); //HeadFlags //10

                stmt.setString(11, model.getDescP()); //dopolnit_p //11
                stmt.setString(12, model.getTown()); //town //12
                stmt.setString(13, model.getStreet()); //street //13
                stmt.setString(14, model.getHouse()); //house //14
                stmt.setString(15, model.getFlat()); //flat //15
                stmt.setString(16, model.getPhone()); //telefon //16
                stmt.setString(17, model.getFax()); //fax //17

                int issueYear, issueMonth, issueDay;
                issueDay = issueMonth = issueYear = 0;
                if (model.getIssueDate() != null) {
                    issueDay = model.getIssueDate().getDayOfMonth();
                    issueMonth = model.getIssueDate().getMonthValue();
                    issueYear = model.getIssueDate().getYear();
                }

                stmt.setInt(18, issueDay); //date_v0 //18
                stmt.setInt(19, issueMonth); //date_v1 //19
                stmt.setInt(20, issueYear); //date_v2 //20

                int expireYear, expireMonth, expireDay;
                expireDay = expireMonth = expireYear = 0;
                if (model.getExpireDate() != null) {
                    expireDay = model.getExpireDate().getDayOfMonth();
                    expireMonth = model.getExpireDate().getMonthValue();
                    expireYear = model.getExpireDate().getYear();
                }

                stmt.setInt(21, expireDay); //date_ok0 //21
                stmt.setInt(22, expireMonth); //date_ok1 //22
                stmt.setInt(23, expireYear); //date_ok2 //23
                int stateCode = (model.getState() == null) ? 0 : model.getState().getCode();
                stmt.setInt(24, stateCode); //state //24
                int regionCode = (model.getRegion() == null) ? 0 : model.getRegion().getCode();
                stmt.setInt(25, regionCode); //area //25
                stmt.setString(26, model.getNumber()); //schet //26
                stmt.setString(27, model.getTypeOfUsing()); //TypeOfUsing //27
                stmt.setString(28, model.isHasCertificate() ? "1" : "0"); //HasCertificateFlag
                //stmt.setString(29, model.getInn()); //inn //29

                stmt.setLong(29, model.getOwnerId());

                int res = stmt.executeUpdate();
                if (res > 0) {
                    conn.commit();
                    result = true;
                } else if (res == 0 && existsById(conn, "owner", model.getOwnerId())) {
                    // Значения могли совпасть с текущими; считаем успехом, чтобы можно было создать запись журнала целостности.
                    conn.commit();
                    result = true;
                } else {
                    conn.rollback();
                }

            }
        } catch (SQLException e) {
            System.out.println("SQL exception while OwnerDao.update. Exception message: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    public List<FreqResult> copy(OwnerView ownerView) {
        List<FreqResult> results = new ArrayList<>();
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) { //open connection
            try (PreparedStatement preparedStatement = connection.prepareStatement(ownerCopySql, Statement.RETURN_GENERATED_KEYS)) { //insert owners
                OwnerModel ownerModel = ownerView.getOwnerModel();
                Integer number = 0;
                String curNum = "select num from numtypes where type = " + ownerModel.getOwnerType().getCode();
                try (PreparedStatement numTypesStmt = connection.prepareStatement(curNum)) {
                    try (ResultSet rs = numTypesStmt.executeQuery()) {
                        if (rs.next()) {
                            number = rs.getInt("num");
                        }
                    }
                }

                for (int i = 0; i < ownerView.count; i++) {
                    number += 1;
                    preparedStatement.setInt(1, ownerModel.getOwnerType().getCode());
                    //preparedStatement.setString(2, ownerModel.getInn());
                    preparedStatement.setInt(2, number);
                    preparedStatement.setLong(3, ownerModel.getOwnerId());
                    preparedStatement.addBatch();
                }

                int[] res = preparedStatement.executeBatch();
                if (res.length == ownerView.count) {
                    String query = String.format("update numtypes set num = %s where type = %s", number, ownerModel.getOwnerType().getCode());
                    try (PreparedStatement numTypesStmt = connection.prepareStatement(query)) {
                        numTypesStmt.executeUpdate();
                    }

                    try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                        while (generatedKeys.next()) {
                            Long newRecordId = generatedKeys.getLong(1);
                            System.out.println("newRecordId=" + newRecordId); //owners

                            //insert owner_detail
                            /*try (PreparedStatement detailStmt = connection.prepareStatement(ownerDetailInsertSql)) {
                                detailStmt.setLong(1, newRecordId);
                                detailStmt.setString(2, ownerModel.getInn());
                                detailStmt.setString(3, ownerModel.getBasis().name());
                                detailStmt.executeUpdate();
                            }*/

                            try (PreparedStatement siteStmt = connection.prepareStatement(siteInsertSql, Statement.RETURN_GENERATED_KEYS)) { //insert sites
                                if (ownerView.getSiteId() != null) {
                                    ownerView.sites = new ArrayList<>();
                                    ownerView.sites.add(new SiteModel(ownerView.getSiteId()));
                                }

                                for (SiteModel site : ownerView.sites) {
                                    siteStmt.setLong(1, newRecordId);
                                    siteStmt.setLong(2, site.getSiteId());
                                    int success = siteStmt.executeUpdate();

                                    if (success > 0) {
                                        try (ResultSet generatedSiteKeys = siteStmt.getGeneratedKeys()) {
                                            while (generatedSiteKeys.next()) {
                                                Long newSiteId = generatedSiteKeys.getLong(1);
                                                System.out.println("newSiteId=" + newSiteId); //site

                                                try (PreparedStatement freqStmt = connection.prepareStatement(freqInsertSql, Statement.RETURN_GENERATED_KEYS)) { //insert frequencies
                                                    freqStmt.setLong(1, newSiteId);
                                                    freqStmt.setLong(2, newRecordId);
                                                    freqStmt.setLong(3, site.getSiteId());
                                                    freqStmt.executeUpdate();
                                                    try (ResultSet keys = freqStmt.getGeneratedKeys()) {
                                                        while (keys.next()) {
                                                            Long newFreqId = keys.getLong(1);
                                                            integrityService.logInsertById(newFreqId);
                                                            System.out.println("newFreqId=" + newFreqId);
                                                            FreqResult freqResult = FreqResult.builder()
                                                                    .ownerId(generatedKeys.getLong(1)).ownerType(ownerModel.getOwnerType()).build();
                                                            freqResult.setFreqId(newFreqId);
                                                            results.add(freqResult);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("SQL exception while copy. Exception message: {}." + e.getMessage());
        }

        return results;
    }

    public boolean insert(FreqView item) {
        boolean res = false;
        try (Connection connection = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) { //open connection
            String newOwner = "INSERT INTO \n" +
                    "OWNER \n" +
                    "(Name,date_pr0,date_pr1,date_pr2,date_p0,date_p1,date_p2,passport,dopolnit,HeadFlags,dopolnit_p,town,street,\n" +
                    "house,flat,telefon,fax,date_v0,date_v1,date_v2,date_ok0,date_ok1,date_ok2,state,area,schet,TypeOfUsing,HasCertificateFlag,  type,LICNUMnum) \n" +
                    "VALUES\n" +
                    "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement stmt = connection.prepareStatement(newOwner, Statement.RETURN_GENERATED_KEYS)) {
                OwnerModel ownerModel = item.getOwnerModel();
                stmt.setString(1, ownerModel.getOwnerName());
                int invoiceYear, invoiceMonth, invoiceDay;
                invoiceYear = invoiceMonth = invoiceDay = 0;
                if (ownerModel.getIssueDate() != null) {
                    invoiceYear = ownerModel.getIssueDate().getYear();
                    invoiceMonth = ownerModel.getIssueDate().getMonthValue();
                    invoiceDay = ownerModel.getIssueDate().getDayOfMonth();
                }

                stmt.setInt(2, invoiceDay); //date_pr0 //2
                stmt.setInt(3, invoiceMonth); //date_pr1 //3
                stmt.setInt(4, invoiceYear); //date_pr1 //4

                int regYear, regMonth, regDay;
                regDay = regMonth = regYear = 0;
                if (ownerModel.getRegDate() != null) {
                    regDay = ownerModel.getRegDate().getDayOfMonth();
                    regMonth = ownerModel.getRegDate().getMonthValue();
                    regYear = ownerModel.getRegDate().getYear();
                }

                stmt.setInt(5, regDay);  //date_p0 //5
                stmt.setInt(6, regMonth); //date_p1 //6
                stmt.setInt(7, regYear); //date_p2 //7

                stmt.setString(8, ownerModel.getPassport());
                stmt.setString(9, ownerModel.getDesc()); //dopolnit //9
                stmt.setInt(10, ownerModel.getHeadFlags()); //HeadFlags //10
                stmt.setString(11, ownerModel.getDescP()); //dopolnit_p //11
                stmt.setString(12, ownerModel.getTown()); //town //12
                stmt.setString(13, ownerModel.getStreet()); //street //13
                stmt.setString(14, ownerModel.getHouse()); //house //14
                stmt.setString(15, ownerModel.getFlat()); //flat //15
                stmt.setString(16, ownerModel.getPhone()); //telefon //16
                stmt.setString(17, ownerModel.getFax()); //fax //17

                int issueYear, issueMonth, issueDay;
                issueDay = issueMonth = issueYear = 0;
                if (ownerModel.getIssueDate() != null) {
                    issueDay = ownerModel.getIssueDate().getDayOfMonth();
                    issueMonth = ownerModel.getIssueDate().getMonthValue();
                    issueYear = ownerModel.getIssueDate().getYear();
                }

                stmt.setInt(18, issueDay); //date_v0 //18
                stmt.setInt(19, issueMonth); //date_v1 //19
                stmt.setInt(20, issueYear); //date_v2 //20

                int expireYear, expireMonth, expireDay;
                expireDay = expireMonth = expireYear = 0;
                if (ownerModel.getExpireDate() != null) {
                    expireDay = ownerModel.getExpireDate().getDayOfMonth();
                    expireMonth = ownerModel.getExpireDate().getMonthValue();
                    expireYear = ownerModel.getExpireDate().getYear();
                }

                stmt.setInt(21, expireDay); //date_ok0 //21
                stmt.setInt(22, expireMonth); //date_ok1 //22
                stmt.setInt(23, expireYear); //date_ok2 //23
                stmt.setInt(24, ownerModel.getState().getCode()); //state //24
                stmt.setInt(25, ownerModel.getRegion().getCode()); //area //25
                stmt.setString(26, ownerModel.getNumber()); //schet //26
                stmt.setString(27, ownerModel.getTypeOfUsing()); //TypeOfUsing //27
                stmt.setString(28, ownerModel.isHasCertificate() ? "1" : "0"); //HasCertificateFlag
                //stmt.setString(29, ownerModel.getInn()); //inn //29
                stmt.setInt(29, ownerModel.getOwnerType().getCode()); //type //29

                String curNum = "select num from numtypes where type = " + ownerModel.getOwnerType().getCode();
                try (PreparedStatement numTypesStmt = connection.prepareStatement(curNum)) {
                    try (ResultSet rs = numTypesStmt.executeQuery()) {
                        if (rs.next()) {
                            stmt.setInt(30, rs.getInt("num") + 1); //type //30
                        }
                    }
                }

                int result = stmt.executeUpdate();
                if (result > 0) {
                    String query = "update numtypes set num = num + 1 where type = " + ownerModel.getOwnerType().getCode();
                    try (PreparedStatement numTypesStmt = connection.prepareStatement(query)) {
                        numTypesStmt.executeUpdate();
                    }

                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        while (generatedKeys.next()) {
                            Long newRecordId = generatedKeys.getLong(1);
                            System.out.println("newRecordId=" + newRecordId);

                            //insert owner_detail
                            /*try (PreparedStatement detailStmt = connection.prepareStatement(ownerDetailInsertSql)) {
                                detailStmt.setLong(1, newRecordId);
                                detailStmt.setString(2, ownerModel.getInn());
                                detailStmt.setString(3, ownerModel.getBasis().name());
                                detailStmt.executeUpdate();
                            }*/

                            String newSite = "INSERT INTO\n" +
                                    "SITE\n" +
                                    "(IDowner,SiteName,CALLsign,longtitude0,longtitude1,longtitude2,latitude0,latitude1,latitude2,h_umora,TransType,TransNum," +
                                    "freqStable,AntName,AntType,\n" +
                                    "AntKU,AntKUrecv,highlight,polar,beamwidth,RecvrType,ISZ,dolg_orbit,RECV_highlight,RECV_AntType,RECV_Sencitivity)\n" +
                                    "VALUES\n" +
                                    "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                            try (PreparedStatement siteStmt = connection.prepareStatement(newSite, Statement.RETURN_GENERATED_KEYS)) {
                                SiteModel model = item.getSiteModel();
                                siteStmt.setLong(1, generatedKeys.getLong(1));
                                siteStmt.setString(2, model.getPoint()); //SiteName //2
                                siteStmt.setString(3, model.getCallSign()); //CALLsign //3
                                siteStmt.setInt(4, model.getLongitude0()); //longtitude0 //4
                                siteStmt.setInt(5, model.getLongitude1());  //longtitude1 //5
                                siteStmt.setInt(6, model.getLongitude2()); //longtitude2 //6
                                siteStmt.setInt(7, model.getLatitude0()); //latitude0 //7
                                siteStmt.setInt(8, model.getLatitude1()); //latitude1 //8
                                siteStmt.setInt(9, model.getLatitude2()); //latitude0 //9
                                siteStmt.setDouble(10, model.getAbsEarth()); //h_umora //10
                                siteStmt.setString(11, model.getTransType()); //TransType //11
                                siteStmt.setString(12, model.getTransNumber()); //TransNum //12
                                siteStmt.setDouble(13, model.getFreqStable()); //freqStable //13
                                siteStmt.setString(14, model.getAntName()); //AntName //14
                                siteStmt.setString(15, model.getAntType()); //AntType //15
                                siteStmt.setDouble(16, model.getAntKU()); //AntKU //16
                                siteStmt.setDouble(17, model.getAntKUrecv()); //AntKUrecv //17
                                siteStmt.setDouble(18, model.getHighlight()); //highlight //18
                                siteStmt.setInt(19, model.getPolar().getCode()); //polar //19
                                siteStmt.setDouble(20, model.getBeamWidth()); //beamwidth //19
                                siteStmt.setString(21, model.getRecvrType()); //RecvrType //20
                                siteStmt.setString(22, model.getIsz()); //ISZ //21
                                siteStmt.setDouble(23, model.getDolgOrbit()); //dolg_orbit //22
                                siteStmt.setDouble(24, model.getReceiverHighlight()); //RECV_highlight //23
                                siteStmt.setString(25, model.getRecvrType()); //RECV_AntType //24
                                siteStmt.setDouble(26, model.getReceiverSensitivity()); //RECV_Sencitivity //25
                                int success = siteStmt.executeUpdate();

                                if (success > 0) {
                                    try (ResultSet generatedSiteKeys = siteStmt.getGeneratedKeys()) {
                                        while (generatedSiteKeys.next()) {
                                            Long newSiteId = generatedSiteKeys.getLong(1);
                                            System.out.println("newSiteId=" + newSiteId); //site
                                            String newFreq = "INSERT INTO\n" +
                                                    "freq\n" +
                                                    "(IDsite,IDowner,nominal,band,deviation,channel,SNCH,Obozn,mob_stan,type,inco,mode,SatRadius,signature)\n" +
                                                    "VALUES\n" +
                                                    "(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
                                            try (PreparedStatement freqStmt = connection.prepareStatement(newFreq, Statement.RETURN_GENERATED_KEYS)) { //insert frequencies
                                                FreqModel freqModel = item.getFreqModel();
                                                freqStmt.setLong(1, newSiteId);
                                                freqStmt.setLong(2, newRecordId);
                                                freqStmt.setDouble(3, FreqUtil.mHzToKHz(freqModel.getNominal())); //nominal
                                                freqStmt.setDouble(4, freqModel.getBand()); //band
                                                freqStmt.setDouble(5, freqModel.getDeviation()); //deviation
                                                freqStmt.setInt(6, freqModel.getChannel()); //channel
                                                freqStmt.setDouble(7, freqModel.getSnch()); //SNCH
                                                freqStmt.setString(8, freqModel.getMeaning()); //Obozn
                                                freqStmt.setInt(9, freqModel.getMobStan()); //mob_stan //9
                                                freqStmt.setInt(10, freqModel.getType().getCode()); //type //10
                                                freqStmt.setInt(11, freqModel.isInco() ? 41 : 0); //inco //11
                                                freqStmt.setInt(12, freqModel.getMode().getCode()); //mode //12
                                                freqStmt.setDouble(13, freqModel.getSatRadius()); //SatRadius //13

                                                String signature = SecurityUtil.generateDigitalSignature(ownerModel.getOwnerName(),freqModel.getNominal(), ownerModel.getIssueDate());
                                                freqStmt.setString(14, signature);

                                                freqStmt.executeUpdate();

                                                try (ResultSet keys = freqStmt.getGeneratedKeys()) {
                                                    while (keys.next()) {
                                                        Long newFreqId = keys.getLong(1);
                                                        integrityService.logInsertById(newFreqId);
                                                        System.out.println("newFreqId=" + newFreqId);
                                                        res = true;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("SQL exception while inserting Owner. Exception message: " + e.getMessage());
        }

        return res;
    }
}

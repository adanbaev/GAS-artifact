package kg.gov.nas.licensedb.dao;

import kg.gov.nas.licensedb.dto.SiteModel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Repository
public class SiteDao {
    private final JdbcTemplate jdbcTemplate;

    /**
     * Важно для MySQL: executeUpdate() может вернуть 0, если значения не изменились.
     * Это не ошибка, если строка существует.
     */
    private boolean existsById(Connection conn, long id) throws SQLException {
        String sql = "SELECT 1 FROM site WHERE ID = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<SiteModel> getByOwnerId(Long ownerId){
        List<SiteModel> sites = new ArrayList<>();
        String select = "select s.ID, s.SiteName\n" +
                "from site s\n" +
                "where s.IDowner = ?";
        try(Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection();
            PreparedStatement stmt = conn.prepareStatement(select)) {
            stmt.setLong(1, ownerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SiteModel model = new SiteModel();
                    model.setSiteId(rs.getLong("ID"));
                    model.setPoint(rs.getString("SiteName"));
                    sites.add(model);
                }
            }
        } catch (SQLException e) {
            System.out.println("SQL exception while SiteDao.update. Exception message: " + e.getMessage());
            e.printStackTrace();
        }

        return sites;
    }

    public boolean update(SiteModel model) {
        if (model == null || model.getSiteId() == null) {
            return false;
        }
        boolean result = false;
        try (Connection conn = Objects.requireNonNull(jdbcTemplate.getDataSource()).getConnection()) {
            conn.setAutoCommit(false);

            String sql = "UPDATE site\n" +
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

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, model.getPoint()); //SiteName //1
                stmt.setString(2, model.getCallSign()); //CALLsign //2
                stmt.setInt(3, model.getLongitude0()); //longtitude0 //3
                stmt.setInt(4, model.getLongitude1());  //longtitude1 //4
                stmt.setInt(5, model.getLongitude2()); //longtitude2 //5
                stmt.setInt(6, model.getLatitude0()); //latitude0 //6
                stmt.setInt(7, model.getLatitude1()); //latitude1 //7
                stmt.setInt(8, model.getLatitude2()); //latitude0 //8


                stmt.setDouble(9, model.getAbsEarth()); //h_umora //9

                stmt.setString(10, model.getTransType()); //TransType //10

                stmt.setString(11, model.getTransNumber()); //TransNum //11


                stmt.setDouble(12, model.getFreqStable()); //freqStable //12

                stmt.setString(13, model.getAntName()); //AntName //13
                stmt.setString(14, model.getAntType()); //AntType //14
                stmt.setDouble(15, model.getAntKU()); //AntKU //15
                stmt.setDouble(16, model.getAntKUrecv()); //AntKUrecv //16

                stmt.setDouble(17, model.getHighlight()); //highlight //17
                int polarCode = (model.getPolar() == null) ? 0 : model.getPolar().getCode();
                stmt.setInt(18, polarCode); //polar //18
                stmt.setDouble(19, model.getBeamWidth()); //beamwidth //19
                stmt.setString(20, model.getRecvrType()); //RecvrType //20
                stmt.setString(21, model.getIsz()); //ISZ //21
                stmt.setDouble(22, model.getDolgOrbit()); //dolg_orbit //22
                stmt.setDouble(23, model.getReceiverHighlight()); //RECV_highlight //23
                stmt.setString(24, model.getReceiverAntType()); //RECV_AntType //24
                stmt.setDouble(25, model.getReceiverSensitivity()); //RECV_Sencitivity //25

                stmt.setLong(26, model.getSiteId());

                int res = stmt.executeUpdate();

                if (res > 0) {
                    conn.commit();
                    result = true;
                } else if (res == 0 && existsById(conn, model.getSiteId())) {
                    // Значения могли совпасть с текущими; считаем успехом.
                    conn.commit();
                    result = true;
                } else {
                    conn.rollback();
                }

            }
        } catch (SQLException e) {
            System.out.println("SQL exception while SiteDao.update. Exception message: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }
}

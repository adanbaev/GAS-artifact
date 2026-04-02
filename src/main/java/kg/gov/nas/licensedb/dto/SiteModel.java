package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.enums.Polar;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Data
public class SiteModel {
    private Long siteId;
    private int latitude0; //latitude0
    private int latitude1; //latitude1
    private int latitude2; //latitude1
    private int longitude0; //longtitude0
    private int longitude1; //longtitude1
    private int longitude2; //longtitude2

    private String point; //SiteName
    private String callSign; //CALLsign
    private double absEarth; //h_umora
    private String transType; //TransType

    private double transPower; //TransPower
    private String transNumber; //TransNum
    private String antName; //AntName
    private String antType; //AntType
    private double antKU; //AntKU
    private double antKUrecv; //AntKUrecv

    private String isz; //ISZ
    private double dolgOrbit; //dolg_orbit
    private double beamWidth; //beamwidth
    private double highlight; //highlight
    private double freqStable; //freqStable

    private String recvrType;//RecvrType
    private Polar polar; //polar
    private String receiverAntType; //RECV_AntType
    private double receiverHighlight; //RECV_highlight
    private double receiverSensitivity; //RECV_Sencitivity

    private List<FreqModel> frequencies = new ArrayList<>();

    private String desc;
    private String allowed;

    private String designationSimple;
    private double asimut;

    public SiteModel(){}
    public SiteModel(Long id){
        this.siteId = id;
    }
}

package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.enums.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class FreqExportModel {
    //owner
    private Long ownerId;
    private String inn;
    private Integer licNumber;
    private String number;
    private boolean hasCertificate;
    public RegStatus regStatus;
    public Purpose purpose;

    private String ownerName;
    private String phone;
    private String fax;
    private String passport;

    private String town;
    private String street;
    private String house;
    private String flat;

    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate issueDate;
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate expireDate;
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate regDate;
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate invoiceDate;

    private State state;
    private String desc;
    private String typeOfUsing;
    private String descP;

    private Region region;
    private OwnerType ownerType;

    private OwnerBasis basis;

    //site
    private Long siteId;
    private int latitude0; //latitude0
    private int latitude1; //latitude1
    private int latitude2; //latitude1
    private int longitude0; //longtitude0
    private int longitude1; //longtitude1
    private int longitude2; //longtitude2

    private String vd;
    private String sh;
    private String point; //SiteName
    private String callSign; //CALLsign
    private Double absEarth; //h_umora
    private String transType; //TransType

    private double transPower; //TransPower
    private String transNumber; //TransNum
    private String antName; //AntName
    private String antType; //AntType
    private Double antKU; //AntKU
    private Double antKUrecv; //AntKUrecv

    private String isz; //ISZ
    private Double dolgOrbit; //dolg_orbit
    private Double beamWidth; //beamwidth
    private Double highlight; //highlight
    private Double freqStable; //freqStable

    private String recvrType;//RecvrType
    private Polar polar; //polar
    private String receiverAntType; //RECV_AntType
    private Double receiverHighlight; //RECV_highlight
    private Double receiverSensitivity; //RECV_Sencitivity


    private String descSite;
    private String allowed;

    private String designationSimple;
    private double asimut;

    //freq
    private Long freqId;
    private Double nominal;
    private FreqType type;
    private Double band;
    private FreqMode mode;
    private String meaning;
    private Integer mobStan;
    private boolean inco;
    private Double satRadius;
    private Double deviation;
    private Integer channel;
    private Double snch;

    private double reception;
    private double transfer;

    @Getter(AccessLevel.NONE)
    private String designation;
    private double originNominal;
    private int originMode;

    public String getDesignation(){
        double prefix = band != 0 ? band / 1000 : 0;
        return prefix + "M0" + meaning;
    }
}

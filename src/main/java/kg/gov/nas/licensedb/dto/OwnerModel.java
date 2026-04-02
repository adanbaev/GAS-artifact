package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.enums.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Data
public class OwnerModel {
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

    private String issueDateTxt;
    private String expireDateTxt;

    private State state;
    private String desc;
    private String typeOfUsing;
    private String descP;

    private Region region;
    private OwnerType ownerType;

    private OwnerBasis basis;

    private List<SiteModel> sites;

    private boolean complex;

    public int getHeadFlags(){
        if(regStatus == RegStatus.PUSKON && purpose == Purpose.COM) return 9;
        if(regStatus == RegStatus.REGISTR && purpose == Purpose.COM) return 24;
        if(regStatus == RegStatus.PEREREGISTR && purpose == Purpose.COM) return 12;
        if(regStatus == RegStatus.PUSKON && purpose == Purpose.PRO) return 3;
        if(regStatus == RegStatus.REGISTR && purpose == Purpose.PRO) return 18;
        if(regStatus == RegStatus.PEREREGISTR && purpose == Purpose.PRO) return 6;

        return 0;
    }

    public String getIssueDateTxt(){
        return this.issueDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    public String getExpireDateTxt(){
        if(this.expireDate != null){
            return this.expireDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));}
        else{
            return "";
        }

    }
}

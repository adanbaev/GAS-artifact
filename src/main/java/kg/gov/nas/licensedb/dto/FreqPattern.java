package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.enums.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
public class FreqPattern extends BasePattern{
    private Long ownerId;
    /*private String passport;*/
    private String number;
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private Date issueDate;
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private Date expireDate;
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private Date regDate;
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private Date invoiceDate;
    private State state;
    private Region region;
    private String point;
    private Integer latitude0;
    private Integer latitude1;
    private Integer latitude2;
    private Integer longitude0;
    private Integer longitude1;
    private Integer longitude2;
    private Double nominal;
    private Double band;
    private boolean byOne;
    private OwnerType type;
    private Integer licNumber;
}

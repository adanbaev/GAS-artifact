package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.enums.OwnerType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FreqResult {
    private Long ownerId;
    private String ownerName;
    private OwnerType ownerType;
    private String number;
    private String point;
    private Double nominal;
    private Double band;
    private Long freqId;

    //
}

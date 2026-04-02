package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.enums.OwnerBasis;
import lombok.Data;

@Data
public class PrinterView {
    public Long from;
    public Long to;
    public String ids;
    public String inn;
    public OwnerBasis basis;
}

package kg.gov.nas.licensedb.dto;

import lombok.Data;

@Data
public class FreqView {
    private FreqModel freqModel;
    private SiteModel siteModel;
    private OwnerModel ownerModel;
}

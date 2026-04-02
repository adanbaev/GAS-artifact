package kg.gov.nas.licensedb.dto;

import lombok.Data;

import java.util.List;

@Data
public class OwnerView {
    public OwnerModel ownerModel;
    public List<SiteModel> sites;
    public Long siteId;
    public int count;
}

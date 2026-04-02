package kg.gov.nas.licensedb.dto;

import lombok.Data;

@Data
public class UserPattern extends BasePattern{
    private String username;
    private Long roleId;
}

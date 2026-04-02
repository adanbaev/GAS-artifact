package kg.gov.nas.licensedb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Форма редактирования TRBAC в UI.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrbacSettingsForm {
    private Boolean enabled;
    private String workStart;
    private String workEnd;
    private String timezone;
    private String offHoursRole;
}
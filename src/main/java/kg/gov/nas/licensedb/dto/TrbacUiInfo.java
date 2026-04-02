package kg.gov.nas.licensedb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Данные для UI-баннера TRBAC.
 *
 * Баннер нужен, чтобы пользователи понимали, почему пропали пункты меню/доступы.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrbacUiInfo {

    /** Показывать ли предупреждающий баннер на странице. */
    private boolean showBanner;

    /** Включён ли TRBAC в целом. */
    private boolean enabled;

    /** Сейчас внутри рабочего окна (true) или вне (false). */
    private boolean within;

    /** Текущее время (в указанной таймзоне) в виде строки. */
    private String nowText;

    /** Рабочее окно: "09:00–18:00" или "24/7". */
    private String windowText;

    /** Таймзона (например Asia/Bishkek). */
    private String timezone;

    /** Роль, действующая вне рабочего окна (обычно VIEWER). */
    private String offHoursRole;
}
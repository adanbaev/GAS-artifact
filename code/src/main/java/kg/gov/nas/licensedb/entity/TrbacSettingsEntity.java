package kg.gov.nas.licensedb.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

/**
 * Настройки TRBAC (временные ограничения на привилегированные действия).
 *
 * Важно:
 * - Это НЕ секреты. Здесь только рабочее время/таймзона/роль вне часов.
 * - Запись одна (id = 1), поэтому администратор может менять через UI без участия разработчиков.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "security_trbac_settings")
public class TrbacSettingsEntity {

    @Id
    @Column(name = "id")
    private Long id;

    /** Включено ли TRBAC. Если false — роли не ограничиваются по времени. */
    @Column(name = "enabled")
    private Boolean enabled;

    /** Начало рабочего окна в формате HH:mm, например 09:00 */
    @Column(name = "work_start", length = 5)
    private String workStart;

    /** Конец рабочего окна в формате HH:mm, например 18:00 */
    @Column(name = "work_end", length = 5)
    private String workEnd;

    /** Таймзона, например Asia/Bishkek */
    @Column(name = "timezone", length = 64)
    private String timezone;

    /** Роль, которая действует вне рабочего окна (обычно VIEWER). */
    @Column(name = "offhours_role", length = 32)
    private String offHoursRole;

    /** Когда изменения были сохранены (epoch millis). */
    @Column(name = "updated_at_ms")
    private Long updatedAtMs;

    /** Кто изменил (username). */
    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}
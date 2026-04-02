package kg.gov.nas.licensedb.entity;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Журнал событий безопасности.
 *
 * ВАЖНО: сюда НЕ пишем пароли и любые секреты.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
    name = "app_security_event",
    indexes = {
        @Index(name = "idx_sec_event_ms", columnList = "event_ms"),
        @Index(name = "idx_sec_event_action", columnList = "action"),
        @Index(name = "idx_sec_event_actor", columnList = "actor_username"),
        @Index(name = "idx_sec_event_subject", columnList = "subject_username")
    }
)
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Epoch millis (UTC). */
    @Column(name = "event_ms", nullable = false)
    private Long eventMs;

    /** Кто выполнил действие (например, admin). */
    @Column(name = "actor_username", length = 64)
    private String actorUsername;

    /** Над кем выполнено действие (например, оператору сбросили пароль). */
    @Column(name = "subject_username", length = 64)
    private String subjectUsername;

    /** Тип события (PASSWORD_RESET / PASSWORD_CHANGED / ...). */
    @Column(name = "action", nullable = false, length = 64)
    private String action;

    /** IP-адрес (IPv4/IPv6). */
    @Column(name = "ip", length = 45)
    private String ip;

    /** User-Agent (обрезаем). */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** Доп. детали (обрезаем). */
    @Column(name = "details", length = 512)
    private String details;

    /**
     * Текст даты/времени для UI.
     *
     * ВАЖНО: поле не хранится в БД.
     *
     * Почему так:
     * - в Thymeleaf/SpEL легко получить исключение на конструкторе Date/форматировании,
     *   что приводит к 500 и "пустой" странице.
     * - безопаснее дать готовую строку.
     */
    @Transient
    public String getEventTimeText() {
        if (eventMs == null) return "";
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
        return Instant.ofEpochMilli(eventMs)
            .atZone(ZoneId.systemDefault())
            .format(f);
    }
}

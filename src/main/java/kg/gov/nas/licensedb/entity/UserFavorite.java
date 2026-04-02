package kg.gov.nas.licensedb.entity;

import lombok.*;

import javax.persistence.*;

/**
 * Избранные (частые) запросы пользователя для главной страницы.
 *
 * Храним только параметры "быстрого поиска" (вкладка "Главная"):
 * - ownerId
 * - licNumber
 * - nominal
 *
 * Таблица создаётся автоматически через spring.jpa.hibernate.ddl-auto=update.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_favorite")
public class UserFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Название (как видит пользователь). */
    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "lic_number")
    private Integer licNumber;

    @Column(name = "nominal", length = 64)
    private String nominal;

    /** Время создания (мс). */
    @Column(name = "created_at_ms", nullable = false)
    private Long createdAtMs;

    @PrePersist
    public void prePersist() {
        if (createdAtMs == null || createdAtMs == 0L) {
            createdAtMs = System.currentTimeMillis();
        }
        if (title != null) {
            title = title.trim();
        }
        if (nominal != null) {
            nominal = nominal.trim();
        }
    }
}
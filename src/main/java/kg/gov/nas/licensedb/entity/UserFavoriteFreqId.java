package kg.gov.nas.licensedb.entity;

import lombok.*;

import javax.persistence.*;

/**
 * Избранные Freq ID пользователя.
 *
 * Используется для быстрых действий (в первую очередь печать):
 * пользователь может сохранить часто используемые номера freq.ID.
 *
 * Таблица создаётся автоматически через spring.jpa.hibernate.ddl-auto=update.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_favorite_freq")
public class UserFavoriteFreqId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Freq.ID (основной ключ таблицы freq). */
    @Column(name = "freq_id", nullable = false)
    private Long freqId;

    /** Название/пометка (как видит пользователь). */
    @Column(name = "title", nullable = false, length = 140)
    private String title;

    /** Время создания/обновления (мс). Используем для сортировки по "свежести". */
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
    }
}

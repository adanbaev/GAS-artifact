package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.entity.UserFavorite;
import kg.gov.nas.licensedb.repository.UserFavoriteRepository;
import kg.gov.nas.licensedb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Objects;

/**
 * Избранные (частые) запросы пользователя.
 */
@Service
@RequiredArgsConstructor
public class UserFavoriteService {

    private final UserFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;

    public List<UserFavorite> getTop10ForCurrentUser() {
        User u = getCurrentUserEntity();
        return favoriteRepository.findTop10ByUser_IdOrderByCreatedAtMsDesc(u.getId());
    }

    /**
     * Добавить "быстрый поиск" в избранное.
     * Если такой набор параметров уже есть — обновим заголовок и вернём существующую запись.
     */
    @Transactional
    public UserFavorite addForCurrentUser(String title, Long ownerId, Integer licNumber, String nominal) {
        User u = getCurrentUserEntity();

        String normalizedTitle = normalizeTitle(title, ownerId, licNumber, nominal);
        String normalizedNominal = normalizeNominal(nominal);

        // Проверка на дубликаты — не через SQL (из-за NULL), а простым сравнением в Java.
        List<UserFavorite> all = favoriteRepository.findByUser_IdOrderByCreatedAtMsDesc(u.getId());
        for (UserFavorite f : all) {
            if (same(ownerId, f.getOwnerId())
                && same(licNumber, f.getLicNumber())
                && same(normalizedNominal, normalizeNominal(f.getNominal()))) {

                f.setTitle(normalizedTitle);
                return favoriteRepository.save(f);
            }
        }

        UserFavorite fav = UserFavorite.builder()
            .user(u)
            .title(normalizedTitle)
            .ownerId(ownerId)
            .licNumber(licNumber)
            .nominal(normalizedNominal)
            .createdAtMs(System.currentTimeMillis())
            .build();

        return favoriteRepository.save(fav);
    }

    @Transactional
    public void deleteForCurrentUser(Long favoriteId) {
        if (favoriteId == null) return;

        User u = getCurrentUserEntity();
        favoriteRepository.findByIdAndUser_Id(favoriteId, u.getId())
            .ifPresent(favoriteRepository::delete);
    }

    private User getCurrentUserEntity() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String username = (a == null) ? null : a.getName();
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Не удалось определить текущего пользователя");
        }

        User u = userRepository.findByUsername(username);
        if (u == null) {
            throw new IllegalStateException("Пользователь не найден: " + username);
        }
        return u;
    }

    private String normalizeNominal(String nominal) {
        if (nominal == null) return null;
        String t = nominal.trim();
        return t.isEmpty() ? null : t;
    }

    private String normalizeTitle(String title, Long ownerId, Integer licNumber, String nominal) {
        String t = (title == null) ? "" : title.trim();
        if (!t.isEmpty()) return t;

        // Автозаголовок
        StringBuilder sb = new StringBuilder("Запрос");
        boolean hasAny = false;

        if (ownerId != null) {
            sb.append(hasAny ? ", " : ": ");
            sb.append("ID ").append(ownerId);
            hasAny = true;
        }
        if (licNumber != null) {
            sb.append(hasAny ? ", " : ": ");
            sb.append("№ ").append(licNumber);
            hasAny = true;
        }
        String n = normalizeNominal(nominal);
        if (n != null) {
            sb.append(hasAny ? ", " : ": ");
            sb.append("Частота ").append(n);
            hasAny = true;
        }

        if (!hasAny) {
            return "Пустой запрос";
        }
        return sb.toString();
    }

    private boolean same(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
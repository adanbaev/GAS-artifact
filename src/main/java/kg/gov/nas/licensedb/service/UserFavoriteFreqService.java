package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.entity.UserFavoriteFreqId;
import kg.gov.nas.licensedb.repository.UserFavoriteFreqRepository;
import kg.gov.nas.licensedb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

/**
 * Избранные Freq ID пользователя.
 */
@Service
@RequiredArgsConstructor
public class UserFavoriteFreqService {

    private final UserFavoriteFreqRepository favoriteFreqRepository;
    private final UserRepository userRepository;

    public List<UserFavoriteFreqId> getTop20ForCurrentUser() {
        User u = getCurrentUserEntity();
        return favoriteFreqRepository.findTop20ByUser_IdOrderByCreatedAtMsDesc(u.getId());
    }

    /**
     * Добавить Freq ID в избранное.
     * Если такой freqId уже сохранён — обновим title и "поднимем" вверх (createdAtMs = now).
     */
    @Transactional
    public UserFavoriteFreqId addForCurrentUser(Long freqId, String title) {
        if (freqId == null) {
            throw new IllegalArgumentException("freqId не задан");
        }

        User u = getCurrentUserEntity();

        String normalizedTitle = normalizeTitle(title, freqId);

        List<UserFavoriteFreqId> all = favoriteFreqRepository.findByUser_IdOrderByCreatedAtMsDesc(u.getId());
        for (UserFavoriteFreqId f : all) {
            if (freqId.equals(f.getFreqId())) {
                f.setTitle(normalizedTitle);
                f.setCreatedAtMs(System.currentTimeMillis());
                return favoriteFreqRepository.save(f);
            }
        }

        UserFavoriteFreqId fav = UserFavoriteFreqId.builder()
            .user(u)
            .freqId(freqId)
            .title(normalizedTitle)
            .createdAtMs(System.currentTimeMillis())
            .build();

        return favoriteFreqRepository.save(fav);
    }

    @Transactional
    public void deleteForCurrentUser(Long favoriteId) {
        if (favoriteId == null) return;

        User u = getCurrentUserEntity();
        favoriteFreqRepository.findByIdAndUser_Id(favoriteId, u.getId())
            .ifPresent(favoriteFreqRepository::delete);
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

    private String normalizeTitle(String title, Long freqId) {
        String t = (title == null) ? "" : title.trim();
        if (!t.isEmpty()) return t;
        return "Freq ID " + freqId;
    }
}

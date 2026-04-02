package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.entity.Role;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.repository.UserRepository;
import kg.gov.nas.licensedb.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.*;

/**
 * Загрузка пользователей для Spring Security.
 *
 * Важно:
 * - Этот сервис возвращает "назначенные" роли пользователя из БД (базовые полномочия).
 * - TRBAC (ограничение привилегий по времени) НЕ применяется здесь.
 *   TRBAC применяется на каждый запрос в фильтре kg.gov.nas.licensedb.security.TrbacPerRequestFilter,
 *   чтобы изменения действовали без перелогина и автоматически "включались/выключались" по времени.
 */
@Service
@RequiredArgsConstructor
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Роль по умолчанию, если у пользователя в БД по какой-то причине не задано ни одной роли.
     * По умолчанию: VIEWER.
     *
     * Примечание: используем тот же ключ, что и раньше (security.trbac.offhours-role),
     * чтобы не требовать изменений конфигурации.
     */
    @Value("${security.trbac.offhours-role:VIEWER}")
    private String defaultRole;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        Set<Role> roles = (user.getRoles() == null) ? Collections.emptySet() : user.getRoles();
        List<GrantedAuthority> authorities = rolesToAuthorities(roles);

        boolean enabled = Boolean.TRUE.equals(user.getActive());
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());

        return new AppUserDetails(
            user.getId(),
            user.getUsername(),
            user.getPassword(),
            enabled,
            authorities,
            mustChange
        );
    }

    private List<GrantedAuthority> rolesToAuthorities(Set<Role> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + normalizeRole(defaultRole)));
        }

        List<GrantedAuthority> out = new ArrayList<>();
        for (Role r : userRoles) {
            if (r == null || r.getName() == null) continue;
            out.add(new SimpleGrantedAuthority("ROLE_" + normalizeRole(r.getName())));
        }

        if (out.isEmpty()) {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + normalizeRole(defaultRole)));
        }
        return out;
    }

    private String normalizeRole(String role) {
        String r = (role == null) ? "" : role.trim().toUpperCase(Locale.ROOT);
        return r.isBlank() ? "VIEWER" : r;
    }
}

package kg.gov.nas.licensedb.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Расширенный UserDetails.
 *
 * Нужен для сценария "обязательная смена пароля после сброса":
 * флаг mustChangePassword хранится в principal и доступен фильтрам/хендлерам,
 * без лишних запросов в БД на каждый HTTP-запрос.
 */
public class AppUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean mustChangePassword;

    public AppUserDetails(Long id,
                          String username,
                          String password,
                          boolean enabled,
                          Collection<? extends GrantedAuthority> authorities,
                          boolean mustChangePassword) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
        this.mustChangePassword = mustChangePassword;
    }

    public Long getId() {
        return id;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

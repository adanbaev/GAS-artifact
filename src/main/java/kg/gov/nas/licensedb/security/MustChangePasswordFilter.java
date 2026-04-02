package kg.gov.nas.licensedb.security;

import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.service.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Принудительная отправка пользователя на страницу смены пароля,
 * если для него установлен флаг must_change_password.
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private final UserService userService;

    public MustChangePasswordFilter(UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Разрешаем саму страницу смены пароля и выход.
        if (uri.startsWith("/user/change-password") || uri.startsWith("/logout")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        String username = auth.getName();
        User user = userService.findUserByUserName(username);
        boolean mustChange = (user != null) && Boolean.TRUE.equals(user.getMustChangePassword());

        if (mustChange) {
            response.sendRedirect("/user/change-password");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

package kg.gov.nas.licensedb.security;

import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * После успешного входа:
 * - если пользователь обязан сменить пароль -> отправляем на /user/change-password
 * - иначе используем SavedRequest (обычное поведение).
 */
@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserService userService;

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        String username = authentication.getName();
        User user = userService.findUserByUserName(username);
        boolean mustChange = (user != null) && Boolean.TRUE.equals(user.getMustChangePassword());

        if (mustChange) {
            // Игнорируем SavedRequest и отправляем на смену пароля.
            getRedirectStrategy().sendRedirect(request, response, "/user/change-password");
            return;
        }

        super.onAuthenticationSuccess(request, response, authentication);
    }
}

package kg.gov.nas.licensedb.security;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Хэндлер неудачной аутентификации.
 *
 * Зачем нужен:
 * 1) После ошибки входа возвращаем пользователя на /login?error=true, но дополнительно
 *    прокидываем введённый логин в параметр u, чтобы форма показывала именно его.
 * 2) Это уменьшает путаницу, когда браузер автозаполняет поле "admin".
 */
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("user_name");
        if (username == null) {
            username = "";
        }

        String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        response.sendRedirect(ctx + "/login?error=true&u=" + encoded);
    }
}

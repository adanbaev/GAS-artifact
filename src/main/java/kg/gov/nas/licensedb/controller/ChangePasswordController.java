package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.service.SecurityEventService;
import kg.gov.nas.licensedb.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Смена пароля пользователем.
 *
 * Используется в сценарии «Сброс пароля администратором»: после входа по временному паролю
 * пользователь обязан сменить пароль.
 */
@Controller
@RequiredArgsConstructor
public class ChangePasswordController {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecurityEventService securityEventService;

    @GetMapping("/user/change-password")
    public String changePasswordPage(Model model) {
        model.addAttribute("mustChange", true);
        return "user/changePassword";
    }

    @PostMapping("/user/change-password")
    public String changePasswordDo(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        String username = auth.getName();
        User user = userService.findUserByUserName(username);
        if (user == null) {
            return "redirect:/login";
        }

        // 1) Проверка текущего пароля
        if (currentPassword == null || currentPassword.isBlank() ||
            !passwordEncoder.matches(currentPassword, user.getPassword())) {
            model.addAttribute("error", "Текущий пароль указан неверно");
            return "user/changePassword";
        }

        // 2) Проверка нового пароля
        if (newPassword == null || newPassword.isBlank()) {
            model.addAttribute("error", "Новый пароль не должен быть пустым");
            return "user/changePassword";
        }
        if (newPassword.length() < 8) {
            model.addAttribute("error", "Новый пароль должен быть не короче 8 символов");
            return "user/changePassword";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Пароли не совпадают");
            return "user/changePassword";
        }

        // 3) Сохраняем
        boolean forced = Boolean.TRUE.equals(user.getMustChangePassword());
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userService.saveUser(user);

        // Логируем (без пароля)
        securityEventService.logPasswordChanged(username, request, forced);

        redirectAttributes.addFlashAttribute("success", "Пароль успешно изменён");
        return "redirect:/";
    }
}

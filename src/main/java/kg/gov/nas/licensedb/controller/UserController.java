package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.constant.PaginationConstant;
import kg.gov.nas.licensedb.dto.Pager;
import kg.gov.nas.licensedb.dto.UserPattern;
import kg.gov.nas.licensedb.dto.UserSpecification;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.service.SecurityEventService;
import kg.gov.nas.licensedb.service.RoleService;
import kg.gov.nas.licensedb.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserService userService;
    private final RoleService roleService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final SecurityEventService securityEventService;

    // Без «0/O» и «1/l» — чтобы пользователю было проще переписать временный пароль.
    private static final String TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;
    private final SecureRandom secureRandom = new SecureRandom();

    @RequestMapping(value = "/index/")
    public ModelAndView index() {
        return this.getModelView(new UserPattern(), "admin/user/userList");
    }

    /**
     * Поиск пользователей.
     *
     * Важно: в интерфейсе поиск делается AJAX-ом (см. app.js -> search()).
     * Поэтому для AJAX мы возвращаем частичный шаблон userResult.
     *
     * Но если по какой-то причине JS не сработал и форма ушла обычным POST-ом,
     * то нельзя возвращать "голый" partial, иначе будет "белая" страница без layout/CSS.
     * В этом случае возвращаем полноценную страницу userList.
     */
    @RequestMapping(value = "/search/", method = RequestMethod.POST)
    public ModelAndView search(@ModelAttribute UserPattern pattern, HttpServletRequest request) {
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        return this.getModelView(pattern, isAjax ? "admin/user/userResult" : "admin/user/userList");
    }

    @RequestMapping(value = "/create/", method = RequestMethod.GET)
    public ModelAndView create(Model model) {
        ModelAndView modelAndView = new ModelAndView();
        User user = new User();
        modelAndView.addObject("item", user);
        modelAndView.addObject("availableRoles", roleService.getAll());
        modelAndView.setViewName("admin/user/user");
        return modelAndView;
    }

    @RequestMapping(value = "/edit/{id}", method = RequestMethod.GET)
    public ModelAndView edit(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        ModelAndView modelAndView = new ModelAndView("admin/user/user");
        try {
            User user = userService.getById(id);
            modelAndView.addObject("item", user);
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
            return new ModelAndView("redirect:/admin/user/index/");
        }

        modelAndView.addObject("availableRoles", roleService.getAll());

        return modelAndView;
    }

    @RequestMapping(value = "/save/", method = RequestMethod.POST)
    public String save(@ModelAttribute User item, Model model) {
        if (item.getId() != null) {
            User oldUser = userService.getById(item.getId());

            // mustChangePassword при редактировании сохраняем как было (не сбрасываем автоматически)
            if (item.getMustChangePassword() == null) {
                item.setMustChangePassword(oldUser.getMustChangePassword());
            }

            if (item.getPassword().isBlank()) {
                item.setPassword(oldUser.getPassword());
            } else {
                item.setPassword(bCryptPasswordEncoder.encode(item.getPassword()));
            }

            userService.saveUser(item);
        } else {
            User userExists = userService.findUserByUserName(item.getUsername());
            if (userExists != null) {
                model.addAttribute("item", item);
                model.addAttribute("availableRoles", roleService.getAll());
                model.addAttribute("error", "Запись с таким логином уже существует!");
                return "admin/user/user";
            }

            item.setPassword(bCryptPasswordEncoder.encode(item.getPassword()));
            item.setActive(true);
            item.setMustChangePassword(false);
            userService.saveUser(item);
        }

        return "redirect:/admin/user/index/";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        userService.delete(user);
        return "redirect:/admin/user/index/";
    }

    /**
     * Сброс пароля пользователя (вариант A): администратор задаёт новый временный пароль.
     * Временный пароль показывается администратору один раз через flash-уведомление.
     *
     * Дополнительно: пользователь обязан сменить пароль при первом входе.
     */
    @PostMapping("/reset-password/{id}")
    public String resetPassword(@PathVariable("id") Long id,
                                RedirectAttributes redirectAttributes,
                                HttpServletRequest request) {
        try {
            User user = userService.getById(id);
            String tempPassword = generateTempPassword();
            user.setPassword(bCryptPasswordEncoder.encode(tempPassword));
            user.setMustChangePassword(true);
            userService.saveUser(user);

            // Логируем событие (без сохранения пароля!)
            String actor = (request != null && request.getUserPrincipal() != null)
                ? request.getUserPrincipal().getName()
                : null;
            securityEventService.logPasswordReset(actor, user.getUsername(), request);

            redirectAttributes.addFlashAttribute("resetPasswordUser", user.getUsername());
            redirectAttributes.addFlashAttribute("resetPasswordTempPassword", tempPassword);
        } catch (NoSuchElementException e) {
            redirectAttributes.addFlashAttribute("error", "Пользователь не найден");
        }

        return "redirect:/admin/user/index/";
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            int idx = secureRandom.nextInt(TEMP_PASSWORD_ALPHABET.length());
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    private ModelAndView getModelView(UserPattern pattern, String view) {
        ModelAndView modelAndView = new ModelAndView(view);

        int evalPageSize = pattern.getPageSize() == null ? PaginationConstant.INITIAL_PAGE_SIZE : pattern.getPageSize();
        int evalPage = (pattern.getPage() == null || pattern.getPage() < 1) ? PaginationConstant.INITIAL_PAGE : pattern.getPage() - 1;

        Specification<User> specification = new UserSpecification(pattern);

        Page<User> items = userService.get(specification, PageRequest.of(evalPage, evalPageSize, Sort.by(Sort.Direction.ASC, "username")));
        Pager pager = new Pager(items.getTotalPages(), items.getNumber(), PaginationConstant.BUTTONS_TO_SHOW);

        modelAndView.addObject("items", items);
        modelAndView.addObject("allRoles", roleService.getAll());
        modelAndView.addObject("selectedPageSize", evalPageSize);
        modelAndView.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
        modelAndView.addObject("pager", pager);
        modelAndView.addObject("pattern", pattern);

        return modelAndView;
    }
}

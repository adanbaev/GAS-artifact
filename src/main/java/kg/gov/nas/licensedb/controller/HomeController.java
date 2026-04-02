package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.dto.HomeDashboardView;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.service.HomeDashboardService;
import kg.gov.nas.licensedb.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HomeController {
    @Autowired
    private UserService userService;

    @Autowired
    private HomeDashboardService dashboardService;

    /** Главная страница (рабочая панель). */
    @GetMapping(value = "/")
    public ModelAndView index() {
        ModelAndView mv = new ModelAndView("index");
        HomeDashboardView dashboard = dashboardService.build();
        mv.addObject("dashboard", dashboard);
        mv.addObject("quickPattern", dashboard.getQuickPattern());
        return mv;
    }

    @GetMapping(value= "/login")
    public ModelAndView login(){
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("login");
        return modelAndView;
    }

    /**
     * Страница-подсказка "Забыли пароль?".
     *
     * Выбранный сценарий: сброс пароля через администратора (раздел "Пользователи").
     * Самостоятельный сброс через e-mail/AD можно добавить позже при необходимости.
     */
    @GetMapping(value = "/forgot-password")
    public ModelAndView forgotPassword() {
        return new ModelAndView("forgotPassword");
    }

    @GetMapping(value="/registration")
    public ModelAndView registration(){
        ModelAndView modelAndView = new ModelAndView();
        User user = new User();
        modelAndView.addObject("user", user);
        modelAndView.setViewName("registration");
        return modelAndView;
    }

    @PostMapping(value = "/registration")
    public ModelAndView createNewUser(User user, BindingResult bindingResult) {
        ModelAndView modelAndView = new ModelAndView();
        User userExists = userService.findUserByUserName(user.getUsername());
        if (userExists != null) {
            bindingResult
                .rejectValue("username", "error.user",
                    "There is already a user registered with the user name provided");
        }
        if (bindingResult.hasErrors()) {
            modelAndView.setViewName("registration");
        } else {
            userService.saveUser2(user);
            modelAndView.addObject("successMessage", "User has been registered successfully");
            modelAndView.addObject("user", new User());
            modelAndView.setViewName("registration");

        }
        return modelAndView;
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "403";
    }
}

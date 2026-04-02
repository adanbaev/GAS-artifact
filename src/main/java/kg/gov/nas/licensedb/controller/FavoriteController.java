package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.service.UserFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Управление избранными запросами пользователя ("Главная").
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/favorites")
public class FavoriteController {

    private final UserFavoriteService favoriteService;

    @PostMapping("/add")
    public String add(@RequestParam(required = false) String title,
                      @RequestParam(required = false) Long ownerId,
                      @RequestParam(required = false) Integer licNumber,
                      @RequestParam(required = false) String nominal,
                      RedirectAttributes redirectAttributes) {

        boolean emptyNominal = (nominal == null || nominal.trim().isEmpty());
        if (ownerId == null && licNumber == null && emptyNominal) {
            redirectAttributes.addFlashAttribute("error", "Нельзя добавить в избранное пустой запрос. Заполните хотя бы одно поле.");
            return "redirect:/";
        }

        favoriteService.addForCurrentUser(title, ownerId, licNumber, nominal);
        redirectAttributes.addFlashAttribute("success", "Запрос добавлен в избранное.");
        return "redirect:/";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        favoriteService.deleteForCurrentUser(id);
        redirectAttributes.addFlashAttribute("success", "Запрос удалён из избранного.");
        return "redirect:/";
    }
}
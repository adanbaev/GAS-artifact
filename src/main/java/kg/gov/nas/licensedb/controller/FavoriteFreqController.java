package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.constant.PaginationConstant;
import kg.gov.nas.licensedb.dto.FreqPattern;
import kg.gov.nas.licensedb.dto.FreqResult;
import kg.gov.nas.licensedb.dto.Pager;
import kg.gov.nas.licensedb.service.FreqSearchService;
import kg.gov.nas.licensedb.service.UserFavoriteFreqService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/**
 * Управление избранными Freq ID (частые номера для печати).
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/favorite-freq")
public class FavoriteFreqController {

    private final UserFavoriteFreqService favoriteFreqService;
    private final FreqSearchService freqSearchService;

    /**
     * Добавить Freq ID в избранное.
     *
     * returnTo:
     * - redirect (по умолчанию): редиректим назад (удобно для /freq/edit/{id} и для Главной)
     * - result: вернуть страницу результатов поиска (freq/freqResult) без редиректа
     */
    @PostMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','PRINTER')")
    public Object add(@RequestParam("freqId") Long freqId,
                      @RequestParam(value = "title", required = false) String title,
                      @RequestParam(value = "returnTo", required = false, defaultValue = "redirect") String returnTo,
                      @ModelAttribute("pattern") FreqPattern pattern,
                      @RequestHeader(value = "Referer", required = false) String referer,
                      RedirectAttributes redirectAttributes,
                      Model model) {

        if (freqId == null) {
            if ("result".equalsIgnoreCase(returnTo)) {
                return buildFreqResult(pattern, null, "Не указан Freq ID");
            }
            redirectAttributes.addFlashAttribute("error", "Не указан Freq ID");
            return "redirect:" + safeReturnUrl(referer, "/");
        }

        favoriteFreqService.addForCurrentUser(freqId, title);

        if ("result".equalsIgnoreCase(returnTo)) {
            return buildFreqResult(pattern, "Freq ID добавлен в избранные.", null);
        }

        redirectAttributes.addFlashAttribute("success", "Freq ID добавлен в избранные.");
        return "redirect:" + safeReturnUrl(referer, "/");
    }

    @PostMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','PRINTER')")
    public String delete(@PathVariable("id") Long id,
                         @RequestHeader(value = "Referer", required = false) String referer,
                         RedirectAttributes redirectAttributes) {

        favoriteFreqService.deleteForCurrentUser(id);
        redirectAttributes.addFlashAttribute("success", "Freq ID удалён из избранного.");
        return "redirect:" + safeReturnUrl(referer, "/");
    }

    /**
     * Повторяем логику FreqController.getModelView(...), чтобы после добавления
     * из результатов поиска пользователь оставался на экране результата.
     */
    private ModelAndView buildFreqResult(FreqPattern pattern, String success, String error) {
        if (pattern == null) {
            pattern = new FreqPattern();
        }

        ModelAndView modelAndView = new ModelAndView("freq/freqResult");
        int evalPageSize = (pattern.getPageSize() == null)
            ? PaginationConstant.INITIAL_PAGE_SIZE
            : pattern.getPageSize();
        int evalPage = (pattern.getPage() == null || pattern.getPage() < 1)
            ? PaginationConstant.INITIAL_PAGE
            : pattern.getPage() - 1;

        Page<FreqResult> page = freqSearchService.get(pattern, PageRequest.of(evalPage, evalPageSize));
        Pager pager = new Pager(page.getTotalPages(), page.getNumber(), PaginationConstant.BUTTONS_TO_SHOW);

        modelAndView.addObject("items", page);
        modelAndView.addObject("pattern", pattern);
        modelAndView.addObject("selectedPageSize", evalPageSize);
        modelAndView.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
        modelAndView.addObject("pager", pager);

        if (success != null && !success.isBlank()) {
            modelAndView.addObject("success", success);
        }
        if (error != null && !error.isBlank()) {
            modelAndView.addObject("error", error);
        }

        return modelAndView;
    }

    private String safeReturnUrl(String referer, String defaultPath) {
        if (referer == null || referer.isBlank()) return defaultPath;
        try {
            URI u = URI.create(referer);
            String path = u.getPath();
            if (path == null || path.isBlank()) return defaultPath;
            if (!path.startsWith("/")) return defaultPath;
            if (path.startsWith("//")) return defaultPath;

            String query = u.getQuery();
            if (query != null && !query.isBlank()) {
                return path + "?" + query;
            }
            return path;
        } catch (Exception ex) {
            return defaultPath;
        }
    }
}

package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.dto.TrbacSettingsForm;
import kg.gov.nas.licensedb.service.RoleService;
import kg.gov.nas.licensedb.service.TrbacSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Controller
@RequestMapping("/admin/trbac")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TrbacAdminController {

    private final TrbacSettingsService trbacSettingsService;
    private final RoleService roleService;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    @GetMapping("/index/")
    public ModelAndView index() {
        return buildModelAndView(trbacSettingsService.getForm(), null);
    }

    @PostMapping("/save/")
    public ModelAndView save(@ModelAttribute("form") TrbacSettingsForm form,
                             RedirectAttributes redirectAttributes) {
        String actor = currentUsername();
        try {
            trbacSettingsService.saveFromForm(form, actor);
            redirectAttributes.addFlashAttribute("success", "TRBAC-настройки сохранены. Изменения применяются сразу (обычно в течение пары секунд)." );
            return new ModelAndView("redirect:/admin/trbac/index/");
        } catch (IllegalArgumentException e) {
            // Ошибки валидации показываем на той же странице
            return buildModelAndView(form, e.getMessage());
        } catch (Exception e) {
            return buildModelAndView(form, "Не удалось сохранить TRBAC-настройки: " + e.getMessage());
        }
    }

    private ModelAndView buildModelAndView(TrbacSettingsForm form, String errorText) {
        ModelAndView mv = new ModelAndView("admin/trbac/trbac");

        TrbacSettingsService.SettingsSnapshot snap = trbacSettingsService.getSnapshot();

        mv.addObject("form", form);
        mv.addObject("roles", roleService.getAll());
        mv.addObject("statusText", trbacSettingsService.buildStatusText());
        mv.addObject("withinNow", trbacSettingsService.isWithinNow());

        // Метаданные
        mv.addObject("updatedBy", snap.updatedBy);
        mv.addObject("updatedAtText", formatUpdatedAt(snap.updatedAtMs, snap.timezone));

        if (errorText != null && !errorText.isBlank()) {
            mv.addObject("error", errorText);
        }
        return mv;
    }

    private String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null || a.getName().isBlank()) return "SYSTEM";
        return a.getName();
    }

    private String formatUpdatedAt(Long updatedAtMs, String tz) {
        if (updatedAtMs == null) return "—";
        ZoneId zone;
        try {
            zone = ZoneId.of(tz == null ? "Asia/Bishkek" : tz);
        } catch (Exception e) {
            zone = ZoneId.of("Asia/Bishkek");
        }
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedAtMs), zone);
        return DT_FMT.format(dt) + " (" + zone + ")";
    }
}

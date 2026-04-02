package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.dto.TrbacUiInfo;
import kg.gov.nas.licensedb.service.TrbacUiInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Глобальные атрибуты для шаблонов (layout.html).
 *
 * Здесь добавляем данные для TRBAC-баннера, чтобы он отображался на всех страницах.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalUiModelAdvice {

    private final TrbacUiInfoService trbacUiInfoService;

    @ModelAttribute("trbacUi")
    public TrbacUiInfo trbacUi() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
        return trbacUiInfoService.build(authenticated);
    }
}
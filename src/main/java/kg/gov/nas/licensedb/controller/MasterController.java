package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.dto.*;
import kg.gov.nas.licensedb.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

@Controller
@RequestMapping("/master")
@RequiredArgsConstructor
public class MasterController {
    private final OwnerService ownerService;

    @RequestMapping(value = "/copy/{id}", method = RequestMethod.GET)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ModelAndView copy(@PathVariable("id") Long id) {
        OwnerView view = ownerService.getById(id);

        ModelAndView modelAndView = new ModelAndView("master/copy");
        modelAndView.addObject("item", view);
        return modelAndView;
    }

    @RequestMapping(value = "/save/", method = RequestMethod.POST)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ModelAndView save(@ModelAttribute OwnerView item, Model model) {
        ModelAndView modelAndView = new ModelAndView("master/result");
        List<FreqResult> results = ownerService.copy(item);
        modelAndView.addObject("items", results);
        return modelAndView;
    }
}

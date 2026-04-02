package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.dto.*;
import kg.gov.nas.licensedb.service.IntegrityIncidentService;
import kg.gov.nas.licensedb.service.OwnerService;
import kg.gov.nas.licensedb.service.PrinterPdfService;
import kg.gov.nas.licensedb.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Controller
@RequestMapping("/printer")
@RequiredArgsConstructor
public class PrinterController {
    private final OwnerService ownerService;
    private final IntegrityIncidentService incidentService;
    private final PrinterPdfService printerPdfService;

    private final Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");

    @RequestMapping(value = "/index/")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'PRINTER')")
    public ModelAndView index() {
        ModelAndView mv = new ModelAndView("printer/print");
        mv.addObject("item", new PrinterView());
        return mv;
    }

    @RequestMapping(value = "/print/", method = RequestMethod.POST)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'PRINTER')")
    public ModelAndView print(@ModelAttribute PrinterView item, Model model) {
        PreparedPrintData prepared = preparePrintData(item);
        if (prepared.errorView != null) {
            return prepared.errorView;
        }

        ModelAndView modelAndView = new ModelAndView("printer/result");
        modelAndView.addObject("items", prepared.items);
        return modelAndView;
    }

    @RequestMapping(value = "/pdf/", method = RequestMethod.POST)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'PRINTER')")
    public Object pdf(@ModelAttribute PrinterView item) {
        PreparedPrintData prepared = preparePrintData(item);
        if (prepared.errorView != null) {
            return prepared.errorView;
        }

        String generatedBy = getCurrentUsername();
        LocalDateTime generatedAt = LocalDateTime.now();

        byte[] pdfBytes = printerPdfService.generatePdf(prepared.items, generatedBy, generatedAt);

        String ts = generatedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "print_" + ts + ".pdf";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes);
    }

    private PreparedPrintData preparePrintData(PrinterView item) {
        // 0) Валидация ввода: ID обязателен
        String rangeError = validateRange(item);
        if (rangeError != null) {
            return PreparedPrintData.error(errorBackToForm(item, rangeError));
        }

        List<Long> allIds = collectIds(item);
        if (allIds.isEmpty()) {
            return PreparedPrintData.error(errorBackToForm(item, "Укажите ID (диапазон или список через запятую)."));
        }

        List<OwnerModel> items = ownerService.getByIdsFull(allIds, item.getInn(), item.getBasis());

        // 1) Если по записи есть OPEN-инцидент — не-ADMIN печать запрещена
        boolean isAdmin = incidentService.isCurrentUserAdmin();
        if (!isAdmin) {
            for (OwnerModel owner : items) {
                if (owner == null || owner.getSites() == null) continue;
                for (SiteModel site : owner.getSites()) {
                    if (site == null || site.getFrequencies() == null) continue;
                    for (FreqModel freq : site.getFrequencies()) {
                        if (freq == null || freq.getFreqId() == null) continue;

                        long freqId = freq.getFreqId();
                        if (incidentService.hasOpenIncidentForFreq(freqId)) {
                            ModelAndView denied = new ModelAndView("403");
                            denied.addObject("reason",
                                "Печать запрещена: по записи открыт инцидент целостности (Freq ID=" + freqId + "). Обратитесь к администратору.");
                            return PreparedPrintData.error(denied);
                        }
                    }
                }
            }
        }

        // 2) Проверка подписи
        for (OwnerModel owner : items) {
            if (owner == null || owner.getSites() == null) continue;
            if (owner.getOwnerName() != null && owner.getIssueDate() != null) {
                for (SiteModel site : owner.getSites()) {
                    if (site == null || site.getFrequencies() == null) continue;

                    for (FreqModel freq : site.getFrequencies()) {
                        if (freq == null) continue;

                        if (freq.getSignature() != null) {
                            String expected = SecurityUtil.generateDigitalSignature(
                                owner.getOwnerName(),
                                freq.getNominal(),
                                owner.getIssueDate()
                            );
                            String actual = freq.getSignature();

                            if (expected != null && !expected.equals(actual)) {
                                long freqId = (freq.getFreqId() == null) ? -1L : freq.getFreqId();

                                long incidentId = incidentService.openSignatureMismatchIncident(
                                    freqId, expected, actual, "Ошибка подписи обнаружена при печати"
                                );

                                ModelAndView err = new ModelAndView("printer/signatureError");
                                err.addObject("freqId", freqId);
                                err.addObject("incidentId", incidentId);
                                err.addObject("expected", expected);
                                err.addObject("actual", actual);
                                return PreparedPrintData.error(err);
                            }
                        }
                    }
                }
            }
        }

        // 3) Канонический хэш данных по каждой частоте (для PDF/печати)
        for (OwnerModel owner : items) {
            if (owner == null || owner.getSites() == null) continue;
            for (SiteModel site : owner.getSites()) {
                if (site == null || site.getFrequencies() == null) continue;
                for (FreqModel freq : site.getFrequencies()) {
                    if (freq == null) continue;
                    freq.setDataHash(SecurityUtil.freqDataHash(owner, site, freq));
                }
            }
        }

        return PreparedPrintData.ok(items);
    }

    private String validateRange(PrinterView item) {
        Long from = item.getFrom();
        Long to = item.getTo();

        // Если пользователь начал заполнять диапазон — требуем оба поля
        if (from != null || to != null) {
            if (from == null || to == null) {
                return "Для диапазона ID заполните оба поля: «С» и «По».";
            }
            if (from > to) {
                return "Диапазон ID задан неверно: «С» больше чем «По».";
            }
        }
        return null;
    }

    private ModelAndView errorBackToForm(PrinterView item, String error) {
        ModelAndView mv = new ModelAndView("printer/print");
        mv.addObject("item", item);
        mv.addObject("error", error);
        return mv;
    }

    private List<Long> collectIds(PrinterView item) {
        List<Long> allIds = new ArrayList<>();

        if (item.getFrom() != null && item.getTo() != null) {
            allIds.addAll(LongStream.rangeClosed(item.getFrom(), item.getTo()).boxed().collect(Collectors.toList()));
        }

        String idsText = item.getIds();
        if (idsText != null && !idsText.isBlank()) {
            String[] arrSplit = idsText.split(",");
            for (String s : arrSplit) {
                String str = s.trim();
                if (isNumeric(str)) {
                    allIds.add(Long.parseLong(str));
                }
            }
        }

        return allIds;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "unknown";
        String name = auth.getName();
        return (name == null || name.isBlank()) ? "unknown" : name;
    }

    public boolean isNumeric(String strNum) {
        if (strNum == null) return false;
        return pattern.matcher(strNum).matches();
    }

    private static class PreparedPrintData {
        private final List<OwnerModel> items;
        private final ModelAndView errorView;

        private PreparedPrintData(List<OwnerModel> items, ModelAndView errorView) {
            this.items = items;
            this.errorView = errorView;
        }

        static PreparedPrintData ok(List<OwnerModel> items) {
            return new PreparedPrintData(items, null);
        }

        static PreparedPrintData error(ModelAndView errorView) {
            return new PreparedPrintData(null, errorView);
        }
    }
}

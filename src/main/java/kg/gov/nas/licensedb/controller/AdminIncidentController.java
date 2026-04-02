package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.constant.PaginationConstant;
import kg.gov.nas.licensedb.dao.IntegrityIncidentDao;
import kg.gov.nas.licensedb.dto.IntegrityIncident;
import kg.gov.nas.licensedb.dto.IntegrityIncidentPattern;
import kg.gov.nas.licensedb.dto.Pager;
import kg.gov.nas.licensedb.service.IntegrityIncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/incidents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class AdminIncidentController {

    private final IntegrityIncidentDao incidentDao;
    private final IntegrityIncidentService incidentService;

    /**
     * Алиасы, чтобы НЕ было путаницы с /admin/incidents vs /admin/incidents/index/
     */
    @GetMapping({"", "/"})
    public String rootRedirect() {
        return "redirect:/admin/incidents/index/";
    }

    @GetMapping({"/index", "/index/"})
    public ModelAndView index() {
        IntegrityIncidentPattern pattern = new IntegrityIncidentPattern();
        pattern.setStatus("OPEN");
        return getModelView(pattern, "admin/incidents/incidentList");
    }

    /**
     * ВАЖНО:
     * - AJAX (search() из app.js) -> возвращаем только фрагмент (incidentResult)
     * - обычный POST -> возвращаем страницу с layout (incidentList)
     */
    @RequestMapping(value = {"/search/", "/search"}, method = RequestMethod.POST)
    public ModelAndView search(@ModelAttribute IntegrityIncidentPattern pattern, HttpServletRequest request) {
        boolean isAjax = request != null
            && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        return getModelView(pattern, isAjax ? "admin/incidents/incidentResult" : "admin/incidents/incidentList");
    }

    /**
     * Вызов из вкладки "Логи": зафиксировать инцидент по freqId (AJAX).
     * Принимаем и GET, и POST — так UI не сломается при разных версиях шаблонов.
     */
    @RequestMapping(value = {"/open", "/open/"}, method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public Map<String, Object> open(@RequestParam long freqId,
                                    @RequestParam(required = false) String comment) {
        Map<String, Object> r = new HashMap<>();
        try {
            long id = incidentService.openDataMismatchIncident(freqId, comment);
            r.put("ok", true);
            r.put("incidentId", id);
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", (e.getMessage() == null || e.getMessage().isBlank())
                ? "Ошибка открытия инцидента"
                : e.getMessage());
        }
        return r;
    }

    /**
     * "Закрыть" инцидент = пометить как RESOLVED.
     *
     * Логика строгая:
     * - DATA_MISMATCH / NO_LOG_FOR_FREQ закрываются ТОЛЬКО если целостность уже ОК (после исправления данных).
     * - SIGNATURE_MISMATCH закрывается вручную (как исключение).
     *
     * Принимаем И POST, И GET, и /resolve и /resolve/ — чтобы не ловить 405.
     */
    @RequestMapping(value = {"/resolve", "/resolve/"}, method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public Map<String, Object> resolve(@RequestParam long id,
                                       @RequestParam(required = false) String comment) {
        Map<String, Object> r = new HashMap<>();
        try {
            incidentService.resolve(id, comment);
            r.put("ok", true);
        } catch (Exception e) {
            r.put("ok", false);
            r.put("error", (e.getMessage() == null || e.getMessage().isBlank())
                ? "Ошибка закрытия инцидента"
                : e.getMessage());
        }
        return r;
    }

    private ModelAndView getModelView(IntegrityIncidentPattern pattern, String view) {
        ModelAndView mv = new ModelAndView(view);

        int evalPageSize = pattern.getPageSize() == null ? PaginationConstant.INITIAL_PAGE_SIZE : pattern.getPageSize();
        int evalPage = (pattern.getPage() == null || pattern.getPage() < 1)
            ? PaginationConstant.INITIAL_PAGE
            : pattern.getPage() - 1;

        String status = blankToNull(pattern.getStatus());
        String type = blankToNull(pattern.getIncidentType());

        // ВАЖНО: ownerId = их реестровый ID (owner.ID)
        Long ownerId = pattern.getOwnerId();

        // Технический ID записи (freq.ID)
        Long freqId = pattern.getFreqId();

        Long fromMs = parseFromDateTime(pattern.getFromDateTime());
        Long toMs = parseToDateTime(pattern.getToDateTime());

        int offset = evalPage * evalPageSize;

        long total = incidentDao.count(status, type, ownerId, freqId, fromMs, toMs);
        List<IntegrityIncident> rows = incidentDao.search(status, type, ownerId, freqId, fromMs, toMs, evalPageSize, offset);

        Page<IntegrityIncident> items = new PageImpl<>(rows, PageRequest.of(evalPage, evalPageSize), total);
        Pager pager = new Pager(items.getTotalPages(), items.getNumber(), PaginationConstant.BUTTONS_TO_SHOW);

        mv.addObject("items", items);
        mv.addObject("selectedPageSize", evalPageSize);
        mv.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
        mv.addObject("pager", pager);
        mv.addObject("pattern", pattern);

        return mv;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Long parseFromDateTime(String s) {
        LocalDateTime dt = parseLocalDateTime(s);
        if (dt == null) return null;
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long parseToDateTime(String s) {
        LocalDateTime dt = parseLocalDateTime(s);
        if (dt == null) return null;
        long start = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return start + (60_000L - 1L);
    }

    private LocalDateTime parseLocalDateTime(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        try {
            return LocalDateTime.parse(t, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                return LocalDateTime.parse(t, f);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}

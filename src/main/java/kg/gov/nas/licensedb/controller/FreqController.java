package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.constant.PaginationConstant;
import kg.gov.nas.licensedb.dto.*;
import kg.gov.nas.licensedb.service.FreqCrudService;
import kg.gov.nas.licensedb.service.FreqSearchService;
import kg.gov.nas.licensedb.service.IntegrityIncidentService;
import kg.gov.nas.licensedb.util.ExportViewHelper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@Controller
@RequestMapping("/freq")
@RequiredArgsConstructor
public class FreqController {
    private final FreqSearchService freqSearchService;
    private final FreqCrudService freqCrudService;
    private final IntegrityIncidentService incidentService;

    @RequestMapping(value = "/index/")
    public ModelAndView index() {
        return this.getModelView(new FreqPattern(), "freq/freqList");
    }

    /**
     * Переход из "Инцидентов": показать результаты по ID владельца в нормальном (layout) виде.
     * Это заменяет вложенную форму внутри таблицы инцидентов.
     */
    @GetMapping("/by-owner/{ownerId}")
    public ModelAndView byOwner(@PathVariable("ownerId") Long ownerId) {
        FreqPattern p = new FreqPattern();
        p.setOwnerId(ownerId);
        p.setPage(1);
        return getModelView(p, "freq/freqList");
    }

    /**
     * Поиск:
     * - AJAX (search() из app.js) -> возвращаем только фрагмент таблицы (freqResult)
     * - обычный POST -> возвращаем страницу с layout (freqList)
     */
    @RequestMapping(value = {"/search", "/search/"}, method = RequestMethod.POST)
    public ModelAndView search(@ModelAttribute FreqPattern pattern, HttpServletRequest request) {
        boolean isAjax = request != null
            && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
        return getModelView(pattern, isAjax ? "freq/freqResult" : "freq/freqList");
    }

    /**
     * Быстрый поиск с главной страницы (AJAX).
     * Возвращает компактный фрагмент таблицы: templates/freq/quickResult.html
     */
    @RequestMapping(value = {"/quick-search", "/quick-search/"}, method = RequestMethod.POST)
    public ModelAndView quickSearch(@ModelAttribute("quickPattern") FreqPattern pattern, HttpServletRequest request) {

        // Защита от случайного «пустого» запроса (иначе можно вытащить весь реестр на главной странице)
        boolean noCriteria = pattern.getOwnerId() == null
            && pattern.getLicNumber() == null
            && pattern.getNominal() == null;

        boolean isAjax = request != null
            && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        if (noCriteria && isAjax) {
            // Вернём пустой результат без обращения к БД
            ModelAndView mv = new ModelAndView("freq/quickResult");
            int evalPageSize = pattern.getPageSize() == null ? PaginationConstant.INITIAL_PAGE_SIZE : pattern.getPageSize();
            int evalPage = (pattern.getPage() == null || pattern.getPage() < 1)
                ? PaginationConstant.INITIAL_PAGE
                : pattern.getPage() - 1;

            Page<FreqResult> emptyPage = Page.empty(PageRequest.of(evalPage, evalPageSize));
            Pager pager = new Pager(emptyPage.getTotalPages(), emptyPage.getNumber(), PaginationConstant.BUTTONS_TO_SHOW);

            mv.addObject("items", emptyPage);
            mv.addObject("pattern", pattern);
            mv.addObject("selectedPageSize", evalPageSize);
            mv.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
            mv.addObject("pager", pager);
            return mv;
        }

        // AJAX -> возвращаем только компактный фрагмент, обычный POST -> можно вернуть страницу поиска
        return getModelView(pattern, isAjax ? "freq/quickResult" : "freq/freqList");
    }

    @PostMapping(value = {"/export", "/export/"})
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','PRINTER','AUDITOR')")
    public void export(@ModelAttribute FreqPattern pattern, HttpServletResponse response) {
        List<FreqExportModel> items = freqSearchService.getAll(pattern);
        SXSSFWorkbook wb = (new FreqExport()).exportExcel(new String[]{"ID", "Тип", "Номер", "Владелец", "Телефон"
            , "Факс", "Счет", "Паспорт", "Область", "Нас.пункт", "Улица", "Дом", "Кв.", "Дата выдачи",
            "Дата оконч-я", "Дата регистр-и", "Дата выписки", "Статус", "Доп.информация", "Назначение", "Доп.инфо.печать", "Пункт уст.",
            "В.Д.", "С.Ш.", "Позывной", "Абс от. земли(М)", "Тип передатчика", "Чувствительность", "Номер передатчика", "Название антенны", "Тип антенны",
            "КУ(дБ)", "КУ прием(дБ)", "Спутник", "Д спутника", "Шир.луча", "Выс.ант(м)", "Стабил-ть частоты", "Тип приемника",
            "Поляриз-я", "Тип антенны", "Высота ант(м)", "Частота", "Тип частоты", "Полоса", "Режим", "Обозн. излучения",
            "Кол.моб.стан.", "Радиус зоны сп", "Девиация", "Канал", "СНЧ"}, items);
        try {
            ExportViewHelper helper = new ExportViewHelper();
            helper.writeData(wb, response, "freq_");
        } catch (Exception e) {
            System.out.println("Load error: " + e.getMessage());
        }
    }

    @RequestMapping(value = "/create/", method = RequestMethod.GET)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ModelAndView create(Model model) {
        ModelAndView modelAndView = new ModelAndView("freq/freq");

        // ВАЖНО: чтобы шаблон th:field (ownerModel/siteModel/freqModel) не падал на null,
        // и чтобы форма корректно отправляла вложенные поля.
        FreqView freq = new FreqView();
        freq.setOwnerModel(new OwnerModel());
        freq.setSiteModel(new SiteModel());
        freq.setFreqModel(new FreqModel());

        modelAndView.addObject("item", freq);
        return modelAndView;
    }


@RequestMapping(value = "/edit/{id}", method = RequestMethod.GET)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ModelAndView edit(@PathVariable("id") Long id, RedirectAttributes redirectAttributes){

        // Жёсткая изоляция: если инцидент открыт — не-ADMIN редактировать не может
        if (!incidentService.isCurrentUserAdmin() && incidentService.hasOpenIncidentForFreq(id)) {
            ModelAndView denied = new ModelAndView("403");
            denied.addObject("reason",
                "Запись заблокирована: по ней открыт инцидент целостности (Freq ID=" + id + "). Обратитесь к администратору.");
            return denied;
        }

        ModelAndView modelAndView = new ModelAndView("freq/freq");
        FreqView freq = freqCrudService.getById(id);

        // Если запись не найдена или по ней нарушены связи owner/site — покажем понятную ошибку,
        // а не пустую форму (пустая форма приводит к случайному INSERT вместо UPDATE).
        if (freq == null || freq.getFreqModel() == null || freq.getFreqModel().getFreqId() == null) {
            ModelAndView denied = new ModelAndView("403");
            denied.addObject("reason",
                "Запись не найдена или данные повреждены (Freq ID=" + id + "). Проверьте связи freq→site→owner в БД.");
            return denied;
        }

        modelAndView.addObject("item", freq);
        return modelAndView;
    }

    @RequestMapping(value = "/save/", method = RequestMethod.POST)
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public String save(@ModelAttribute FreqView item, Model model) {

        Long freqId = (item.getFreqModel() == null) ? null : item.getFreqModel().getFreqId();

        // Жёсткая изоляция: если инцидент открыт — не-ADMIN сохранять не может
        if (freqId != null && !incidentService.isCurrentUserAdmin() && incidentService.hasOpenIncidentForFreq(freqId)) {
            model.addAttribute("reason",
                "Сохранение запрещено: по записи открыт инцидент целостности (Freq ID=" + freqId + "). Обратитесь к администратору.");
            return "403";
        }

        // Ключевая логика:
        // - UPDATE/INSERT определяем по freqId.
        // - Если freqId != null, это редактирование существующей записи -> UPDATE.
        //   Раньше здесь была проверка ownerId, из-за чего при битых связях / пустом ownerId происходил INSERT,
        //   создавалась новая запись и инцидент по старому freqId НЕ закрывался.
        if (freqId != null) {
            FreqCrudService.UpdateOutcome out = freqCrudService.updateWithOutcome(item);
            if (!out.isOk()) {
                Long ownerId = (item.getOwnerModel() == null) ? null : item.getOwnerModel().getOwnerId();
                Long siteId = (item.getSiteModel() == null) ? null : item.getSiteModel().getSiteId();
                model.addAttribute("reason", out.toHumanMessage(freqId, ownerId, siteId));
                return "403";
            }

            // "Как раньше": админ исправил запись -> если целостность стала ОК, закрываем инциденты автоматически
            if (incidentService.isCurrentUserAdmin()) {
                incidentService.autoResolveOpenIncidentsIfOk(freqId);
            }

        } else {
            boolean ok = freqCrudService.insert(item);
            if (!ok) {
                model.addAttribute("reason", "Не удалось создать запись. Проверьте заполнение обязательных полей.");
                return "403";
            }
        }

        return "redirect:/freq/index/";
    }

    @GetMapping("/delete/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public String delete(@PathVariable("id") Long id) {
        return "redirect:/freq/index/";
    }

    private ModelAndView getModelView(FreqPattern pattern, String view) {
        ModelAndView modelAndView = new ModelAndView(view);
        int evalPageSize = pattern.getPageSize() == null ? PaginationConstant.INITIAL_PAGE_SIZE : pattern.getPageSize();
        int evalPage = (pattern.getPage() == null || pattern.getPage() < 1) ? PaginationConstant.INITIAL_PAGE : pattern.getPage() - 1;

        Page<FreqResult> page = freqSearchService.get(pattern, PageRequest.of(evalPage, evalPageSize));
        Pager pager = new Pager(page.getTotalPages(), page.getNumber(), PaginationConstant.BUTTONS_TO_SHOW);

        modelAndView.addObject("items", page);
        modelAndView.addObject("pattern", pattern);
        modelAndView.addObject("selectedPageSize", evalPageSize);
        modelAndView.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
        modelAndView.addObject("pager", pager);

        return modelAndView;
    }
}

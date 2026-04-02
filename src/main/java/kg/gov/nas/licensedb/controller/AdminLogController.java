package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.constant.PaginationConstant;
import kg.gov.nas.licensedb.dao.IntegrityLogDao;
import kg.gov.nas.licensedb.dto.IntegrityLogEntry;
import kg.gov.nas.licensedb.dto.IntegrityLogPattern;
import kg.gov.nas.licensedb.dto.Pager;
import kg.gov.nas.licensedb.entity.User;
import kg.gov.nas.licensedb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class AdminLogController {

    private final IntegrityLogDao integrityLogDao;
    private final UserRepository userRepository;

    @RequestMapping(value = "/index/")
    public ModelAndView index() {
        IntegrityLogPattern pattern = new IntegrityLogPattern();
        pattern.setSource("LOG"); // по умолчанию показываем цепочку (STRICT)
        return getModelView(pattern, "admin/logs/logList");
    }

    /**
     * ВАЖНО:
     * - AJAX (search() из app.js) -> возвращаем только фрагмент (logResult), который вставляется в #searchResults
     * - обычный POST (например, если браузер отправил форму “целиком”) -> возвращаем страницу с layout (logList)
     */
    @RequestMapping(value = {"/search/", "/search"}, method = RequestMethod.POST)
    public ModelAndView search(@ModelAttribute IntegrityLogPattern pattern, HttpServletRequest request) {
        boolean isAjax = request != null
            && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));

        return getModelView(pattern, isAjax ? "admin/logs/logResult" : "admin/logs/logList");
    }

    private ModelAndView getModelView(IntegrityLogPattern pattern, String view) {
        ModelAndView modelAndView = new ModelAndView(view);

        int evalPageSize = pattern.getPageSize() == null ? PaginationConstant.INITIAL_PAGE_SIZE : pattern.getPageSize();
        int evalPage = (pattern.getPage() == null || pattern.getPage() < 1) ? PaginationConstant.INITIAL_PAGE : pattern.getPage() - 1;

        String source = (pattern.getSource() == null || pattern.getSource().isBlank()) ? "LOG" : pattern.getSource().trim();

        String actor = blankToNull(pattern.getActorUsername());
        String action = blankToNull(pattern.getAction());
        Long ownerId = pattern.getOwnerId();
        Long freqId = pattern.getFreqId();

        Long fromMs = parseFromDateTime(pattern.getFromDateTime());
        Long toMs = parseToDateTime(pattern.getToDateTime());

        int offset = evalPage * evalPageSize;

        long total = integrityLogDao.countLogs(source, actor, action, ownerId, freqId, fromMs, toMs);
        List<IntegrityLogEntry> rows = integrityLogDao.searchLogs(source, actor, action, ownerId, freqId, fromMs, toMs, evalPageSize, offset);

        Page<IntegrityLogEntry> items = new PageImpl<>(rows, PageRequest.of(evalPage, evalPageSize), total);
        Pager pager = new Pager(items.getTotalPages(), items.getNumber(), PaginationConstant.BUTTONS_TO_SHOW);

        List<String> users = userRepository.findAll(Sort.by(Sort.Direction.ASC, "username")).stream()
            .filter(Objects::nonNull)
            .filter(u -> Boolean.TRUE.equals(u.getActive()))
            .map(User::getUsername)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        List<String> actions = integrityLogDao.findDistinctActions(source);

        modelAndView.addObject("items", items);
        modelAndView.addObject("selectedPageSize", evalPageSize);
        modelAndView.addObject("pageSizes", PaginationConstant.PAGE_SIZES);
        modelAndView.addObject("pager", pager);
        modelAndView.addObject("pattern", pattern);
        modelAndView.addObject("users", users);
        modelAndView.addObject("actions", actions);

        return modelAndView;
    }

    private String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Long parseFromDateTime(String s) {
        LocalDateTime dt = parseLocalDateTime(s);
        if (dt == null) return null;

        ZoneId zone = ZoneId.systemDefault();
        return dt.atZone(zone).toInstant().toEpochMilli();
    }

    private Long parseToDateTime(String s) {
        LocalDateTime dt = parseLocalDateTime(s);
        if (dt == null) return null;

        ZoneId zone = ZoneId.systemDefault();
        long startOfMinute = dt.atZone(zone).toInstant().toEpochMilli();
        return startOfMinute + (60_000L - 1L);
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

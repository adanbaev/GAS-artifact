package kg.gov.nas.licensedb.controller;

import kg.gov.nas.licensedb.dao.OwnerLookupDao;
import kg.gov.nas.licensedb.dto.IntegrityCheckReport;
import kg.gov.nas.licensedb.dto.IntegrityHashDebugReport;
import kg.gov.nas.licensedb.service.IntegrityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/integrity")
@PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
public class IntegrityController {

    private final IntegrityService integrityService;
    private final OwnerLookupDao ownerLookupDao;

    @GetMapping("/check")
    public IntegrityCheckReport check(
        @RequestParam(defaultValue = "true") boolean verifyData,
        @RequestParam(defaultValue = "2000") int limit
    ) {
        return integrityService.check(verifyData, limit);
    }

    /**
     * Проверка по "ID" (для пользователей):
     * 1) сначала пробуем трактовать как owner.ID (если у владельца есть записи),
     * 2) если по owner.ID не найдено ни одной записи — трактуем как freq.ID.
     */
    @GetMapping("/checkId")
    public IntegrityCheckReport checkId(@RequestParam long id) {
        List<Long> freqIds = ownerLookupDao.findFreqIdsByOwnerId(id);

        // 1) owner.ID
        if (freqIds != null && !freqIds.isEmpty()) {
            List<String> issues = new ArrayList<>();

            Long firstBadFreqId = null;
            IntegrityCheckReport firstBad = null;

            int dataIssues = 0;

            for (Long fid : freqIds) {
                if (fid == null) continue;
                IntegrityCheckReport r = integrityService.checkFreq(fid);
                if (r.getIssues() != null && !r.getIssues().isEmpty()) {
                    issues.addAll(r.getIssues());
                }
                if (!r.isOk()) {
                    dataIssues += Math.max(1, r.getDataIssues());
                    if (firstBadFreqId == null) {
                        firstBadFreqId = fid;
                        firstBad = r;
                    }
                }
            }

            boolean ok = issues.isEmpty();

            IntegrityCheckReport out;
            if (firstBad != null) {
                // Возьмём детали первой проблемной записи, чтобы можно было зафиксировать инцидент.
                out = IntegrityCheckReport.builder()
                    .ok(ok)
                    .checkedAtMs(firstBad.getCheckedAtMs())
                    .ownerId(id)
                    .freqId(firstBadFreqId)
                    .source(firstBad.getSource())
                    .lastLogId(firstBad.getLastLogId())
                    .prevLogId(firstBad.getPrevLogId())
                    .expectedHash(firstBad.getExpectedHash())
                    .actualHash(firstBad.getActualHash())
                    .logEntriesChecked(freqIds.size())
                    .chainIssues(0)
                    .dataIssues(ok ? 0 : Math.max(1, dataIssues))
                    .issues(issues)
                    .build();
            } else {
                // Всё ОК
                out = IntegrityCheckReport.builder()
                    .ok(true)
                    .checkedAtMs(System.currentTimeMillis())
                    .ownerId(id)
                    .logEntriesChecked(freqIds.size())
                    .chainIssues(0)
                    .dataIssues(0)
                    .issues(issues)
                    .build();
            }

            return out;
        }

        // 2) freq.ID
        IntegrityCheckReport r = integrityService.checkFreq(id);
        r.setOwnerId(ownerLookupDao.findOwnerIdByFreqId(id));
        return r;
    }

    /**
     * Отладка хэша по freqId: canonicalString + разложение по полям.
     */
    @GetMapping("/debugFreq")
    public IntegrityHashDebugReport debugFreq(@RequestParam long freqId) {
        return integrityService.debugFreq(freqId);
    }

    /**
     * Технический эндпоинт (оставлен для совместимости).
     */
    @GetMapping("/checkFreq")
    public IntegrityCheckReport checkFreq(@RequestParam long freqId) {
        return integrityService.checkFreq(freqId);
    }
}

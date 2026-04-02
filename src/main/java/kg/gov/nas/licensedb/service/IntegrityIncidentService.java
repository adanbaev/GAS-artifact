package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.IntegrityIncidentDao;
import kg.gov.nas.licensedb.dto.IntegrityCheckReport;
import kg.gov.nas.licensedb.dto.IntegrityIncident;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IntegrityIncidentService {

    private final IntegrityIncidentDao incidentDao;
    private final IntegrityService integrityService;

    public boolean hasOpenIncidentForFreq(long freqId) {
        return incidentDao.existsOpenForFreq(freqId);
    }

    public boolean isCurrentUserAdmin() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return false;

        for (GrantedAuthority ga : a.getAuthorities()) {
            if (ga == null) continue;
            String auth = ga.getAuthority();
            if ("ROLE_ADMIN".equals(auth) || "ADMIN".equals(auth)) {
                return true;
            }
        }
        return false;
    }

    public long openDataMismatchIncident(long freqId, String comment) {
        IntegrityCheckReport r = integrityService.checkFreq(freqId);

        long now = System.currentTimeMillis();
        String user = currentUsername();

        String type = "DATA_MISMATCH";
        if (r.getIssues() != null) {
            for (String s : r.getIssues()) {
                if (s != null && s.startsWith("NO_LOG_FOR_FREQ")) {
                    type = "NO_LOG_FOR_FREQ";
                    break;
                }
            }
        }

        Long existingId = incidentDao.findOpenIncidentId(freqId, type);
        if (existingId == null) {
            return incidentDao.insertOpenIncident(
                now, user, type, freqId,
                r.getLastLogId(), r.getExpectedHash(), r.getActualHash(),
                comment
            );
        } else {
            incidentDao.updateOpenIncident(
                existingId, now, user,
                r.getLastLogId(), r.getExpectedHash(), r.getActualHash(),
                comment
            );
            return existingId;
        }
    }

    public long openSignatureMismatchIncident(long freqId, String expectedSignature, String actualSignature, String comment) {
        long now = System.currentTimeMillis();
        String user = currentUsername();

        String type = "SIGNATURE_MISMATCH";

        Long existingId = incidentDao.findOpenIncidentId(freqId, type);
        if (existingId == null) {
            return incidentDao.insertOpenIncident(
                now, user, type, freqId,
                null, expectedSignature, actualSignature,
                comment
            );
        } else {
            incidentDao.updateOpenIncident(
                existingId, now, user,
                null, expectedSignature, actualSignature,
                comment
            );
            return existingId;
        }
    }

    /**
     * Строгое закрытие:
     * - DATA_MISMATCH / NO_LOG_FOR_FREQ нельзя закрыть вручную, пока checkFreq(freqId) не станет OK.
     * - SIGNATURE_MISMATCH закрывается вручную (как исключение).
     */
    public void resolve(long id, String comment) {
        IntegrityIncident inc = incidentDao.findById(id);
        if (inc == null) {
            throw new IllegalStateException("Инцидент не найден");
        }

        String type = inc.getIncidentType();
        long freqId = inc.getFreqId();

        if ("DATA_MISMATCH".equals(type) || "NO_LOG_FOR_FREQ".equals(type)) {
            IntegrityCheckReport r = integrityService.checkFreq(freqId);
            if (r == null || !r.isOk()) {
                throw new IllegalStateException(
                    "Нельзя закрыть инцидент: целостность записи всё ещё нарушена. " +
                        "Откройте запись, исправьте данные и сохраните — инцидент закроется автоматически."
                );
            }
        }

        long now = System.currentTimeMillis();
        String user = currentUsername();
        int updated = incidentDao.resolve(id, now, user, comment);
        if (updated <= 0) {
            throw new IllegalStateException("Инцидент не найден или уже закрыт");
        }
    }

    /**
     * "Как раньше": админ исправил запись -> если checkFreq(freqId) стало OK, закрываем открытые инциденты
     * DATA_MISMATCH / NO_LOG_FOR_FREQ автоматически.
     */
    public boolean autoResolveOpenIncidentsIfOk(long freqId) {
        IntegrityCheckReport r = integrityService.checkFreq(freqId);
        if (r == null || !r.isOk()) {
            return false;
        }

        long now = System.currentTimeMillis();
        String user = currentUsername();
        int updated = incidentDao.resolveOpenForFreq(
            freqId,
            now,
            user,
            "Автоматически закрыто после корректировки записи администратором"
        );
        return updated > 0;
    }

    private String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return "SYSTEM";
        if (!a.isAuthenticated()) return "SYSTEM";
        return a.getName() == null ? "SYSTEM" : a.getName();
    }
}

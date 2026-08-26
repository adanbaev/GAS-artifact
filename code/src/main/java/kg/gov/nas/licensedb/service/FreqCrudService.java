package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dao.OwnerDao;
import kg.gov.nas.licensedb.dao.SiteDao;
import kg.gov.nas.licensedb.dto.FreqView;
import kg.gov.nas.licensedb.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class FreqCrudService {
    private final FreqDao freqDao;
    private final SiteDao siteDao;
    private final OwnerDao ownerDao;
    private final IntegrityService integrityService;

    @org.springframework.beans.factory.annotation.Value("${security.signature.enabled:true}")
    private boolean signatureEnabled;

    public void setSignatureEnabled(boolean enabled) {
        this.signatureEnabled = enabled;
    }

    public FreqView getById(Long freqId) {
        try {
            return freqDao.getById(freqId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static class UpdateOutcome {
        private final boolean ok;
        private final String stage;
        private final String details;

        public UpdateOutcome(boolean ok, String stage, String details) {
            this.ok = ok;
            this.stage = stage;
            this.details = details;
        }

        public boolean isOk() {
            return ok;
        }

        public String getStage() {
            return stage;
        }

        public String getDetails() {
            return details;
        }

        public String toHumanMessage(Long freqId, Long ownerId, Long siteId) {
            StringBuilder sb = new StringBuilder();
            sb.append("Не удалось сохранить изменения");
            if (freqId != null) sb.append(" (Freq ID=").append(freqId).append(")");
            sb.append(". Этап: ").append(stage).append(". ");
            if (ownerId != null) sb.append("ownerId=").append(ownerId).append(". ");
            if (siteId != null) sb.append("siteId=").append(siteId).append(". ");
            if (details != null && !details.isBlank()) sb.append(details);
            sb.append(" Проверьте консоль приложения: при SQL-ошибке DAO выводит причину.");
            return sb.toString();
        }
    }

    /**
     * Обновление с диагностикой: возвращает причину, на каком этапе не удалось сохранить.
     */
    public UpdateOutcome updateWithOutcome(FreqView view) {
        if (view == null) {
            return new UpdateOutcome(false, "INPUT_NULL", "Объект формы (FreqView) не получен.");
        }

        Long freqId = (view.getFreqModel() == null) ? null : view.getFreqModel().getFreqId();
        Long ownerId = (view.getOwnerModel() == null) ? null : view.getOwnerModel().getOwnerId();
        Long siteId = (view.getSiteModel() == null) ? null : view.getSiteModel().getSiteId();

        if (freqId == null) {
            return new UpdateOutcome(false, "FREQ_ID_EMPTY", "Отсутствует freqId (скрытое поле формы).");
        }
        if (ownerId == null) {
            return new UpdateOutcome(false, "OWNER_ID_EMPTY", "Отсутствует ownerId (поле ID владельца пустое).");
        }
        // siteId в legacy-данных может быть 0 (DEFAULT 0 в таблице freq).
        // В таком случае записи в таблице site может не быть, и попытка UPDATE site по ID=0 всегда вернёт 0.
        // Это НЕ должно блокировать сохранение owner/freq.
        if (siteId == null) {
            return new UpdateOutcome(false, "SITE_ID_EMPTY", "Отсутствует siteId (скрытое поле siteID).");
        }

        try {
            boolean ok;

            ok = ownerDao.update(view.getOwnerModel());
            if (!ok) return new UpdateOutcome(false, "OWNER_UPDATE_FAILED", "Не удалось обновить owner (UPDATE вернул 0 и запись не найдена, либо SQL-ошибка).");

            // site обновляем только если siteId > 0.
            // При siteId=0 (нет привязки к пункту установки) пропускаем обновление site,
            // чтобы не падать на UPDATE site WHERE ID=0.
            if (siteId != null && siteId > 0) {
                ok = siteDao.update(view.getSiteModel());
                if (!ok) return new UpdateOutcome(false, "SITE_UPDATE_FAILED", "Не удалось обновить site (UPDATE вернул 0 и запись не найдена, либо SQL-ошибка). Возможно, siteId некорректный.");
            }

            // Подпись считаем только если включено
            String signature = null;
            if (signatureEnabled) {
                signature = SecurityUtil.generateDigitalSignature(
                    view.getOwnerModel().getOwnerName(),
                    view.getFreqModel().getNominal(),
                    view.getOwnerModel().getIssueDate()
                );
            }

            ok = freqDao.update(view.getFreqModel(), signature);
            if (!ok) return new UpdateOutcome(false, "FREQ_UPDATE_FAILED", "Не удалось обновить freq (UPDATE вернул 0 и запись не найдена, либо SQL-ошибка).");

            // Логируем по фактическому состоянию БД (а не по данным формы), чтобы хэши всегда соответствовали сохранённым данным.
            integrityService.logUpdateById(freqId);
            return new UpdateOutcome(true, "OK", "");

        } catch (RuntimeException ex) {
            // На случай непойманных ошибок (NPE и т.п.)
            return new UpdateOutcome(false, "RUNTIME_EXCEPTION", (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    public boolean update(FreqView view) {
        return updateWithOutcome(view).isOk();
    }

    public boolean updateFreqOnly(FreqView view) {
        // Подпись считаем только если включено
        String signature = null;
        if (signatureEnabled) {
            signature = SecurityUtil.generateDigitalSignature(
                view.getOwnerModel() == null ? null : view.getOwnerModel().getOwnerName(),
                view.getFreqModel() == null ? null : view.getFreqModel().getNominal(),
                view.getOwnerModel() == null ? null : view.getOwnerModel().getIssueDate()
            );
        }

        boolean result = freqDao.update(view.getFreqModel(), signature);

        if (result) {
            Long freqId = (view == null || view.getFreqModel() == null) ? null : view.getFreqModel().getFreqId();
            integrityService.logUpdateById(freqId);
        }
        return result;
    }


    public boolean insert(FreqView view) {
        return ownerDao.insert(view);
    }
}

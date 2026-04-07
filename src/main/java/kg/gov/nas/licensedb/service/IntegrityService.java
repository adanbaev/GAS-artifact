package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dao.IntegrityLogDao;
import kg.gov.nas.licensedb.dto.*;
import kg.gov.nas.licensedb.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IntegrityService {

    @org.springframework.beans.factory.annotation.Value("${security.integrity.enabled:true}")
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private final IntegrityLogDao integrityLogDao;
    private final FreqDao freqDao;

    private final Object checkpointLock = new Object();
    private int pendingCount = 0;
    private String pendingRoot = "BATCH_GENESIS";
    private long pendingStartEventId = -1;

    // буфер строк событий для batch insert
    private final java.util.ArrayList<IntegrityLogDao.EventRow> pendingEvents = new java.util.ArrayList<>(200);

    public enum Strategy {
        STRICT,      // prev_hash/chain_hash на каждую операцию
        CHECKPOINT   // события на каждую операцию, цепочка каждые K событий
    }

    private volatile Strategy strategy = Strategy.STRICT;
    private volatile int checkpointBatchSize = 100; // K по умолчанию

    public void setStrategy(Strategy strategy) {
        this.strategy = (strategy == null) ? Strategy.STRICT : strategy;
    }

    public void setCheckpointBatchSize(int k) {
        if (k < 1) k = 1;
        this.checkpointBatchSize = k;
    }

    @Transactional
    public void logUpdate(FreqView view) {
        append("UPDATE", view);
    }

    /**
     * Логирование UPDATE по ID записи: перечитываем данные из БД и логируем именно фактическое состояние.
     * Это важно, когда часть связанных сущностей (например, site) может не обновляться (IDsite=0) или
     * когда форма могла прислать значения, которые не были сохранены.
     */
    @Transactional
    public void logUpdateById(Long freqId) {
        if (freqId == null) return;
        try {
            FreqView view = freqDao.getById(freqId);
            if (view == null || view.getFreqModel() == null || view.getFreqModel().getFreqId() == null) {
                return;
            }
            append("UPDATE", view);
        } catch (SQLException e) {
            System.out.println("Integrity logUpdateById error: " + e.getMessage());
        }
    }

    @Transactional
    public void logInsertById(Long freqId) {
        try {
            FreqView view = freqDao.getById(freqId);
            if (view == null || view.getFreqModel() == null || view.getFreqModel().getFreqId() == null) {
                // нечего логировать (freq не найден/битые связи)
                return;
            }
            append("INSERT", view);
        } catch (SQLException e) {
            System.out.println("Integrity logInsertById error: " + e.getMessage());
        }
    }

    @Transactional
    protected void append(String action, FreqView view) {
        if (!enabled) return;

        if (strategy == Strategy.CHECKPOINT) {
            appendCheckpoint(action, view);
            return;
        }

        // STRICT
        long eventMs = System.currentTimeMillis();
        String actor = currentUsername();

        OwnerModel owner = view == null ? null : view.getOwnerModel();
        SiteModel site   = view == null ? null : view.getSiteModel();
        FreqModel freq   = view == null ? null : view.getFreqModel();
        Long freqId      = (freq == null) ? null : freq.getFreqId();

        String dataHash = SecurityUtil.freqDataHash(owner, site, freq);

        String prevHash = integrityLogDao.lockAndGetLastHash();
        if (prevHash == null || prevHash.isBlank()) prevHash = "GENESIS";

        String material = prevHash + "|" + eventMs + "|" + actor + "|" + action + "|" +
            (freqId == null ? "" : freqId) + "|" + dataHash + "|" + SecurityUtil.getChainSecret();
        String chainHash = SecurityUtil.sha256Hex(material);

        integrityLogDao.insertLog(eventMs, actor, action, freqId, dataHash, prevHash, chainHash);
        integrityLogDao.updateLastHash(chainHash);
    }

    @Transactional
    protected void appendCheckpoint(String action, FreqView view) {
        long eventMs = System.currentTimeMillis();
        String actor = currentUsername();

        OwnerModel owner = view == null ? null : view.getOwnerModel();
        SiteModel site   = view == null ? null : view.getSiteModel();
        FreqModel freq   = view == null ? null : view.getFreqModel();
        Long freqId      = (freq == null) ? null : freq.getFreqId();

        String dataHash = SecurityUtil.freqDataHash(owner, site, freq);

        IntegrityLogDao.EventRow row = new IntegrityLogDao.EventRow(eventMs, actor, action, freqId, dataHash);

        synchronized (checkpointLock) {
            String material = eventMs + "|" + actor + "|" + action + "|" + (freqId == null ? "" : freqId) + "|" + dataHash;
            pendingRoot = SecurityUtil.sha256Hex(pendingRoot + "|" + material + "|" + SecurityUtil.getChainSecret());

            if (pendingCount == 0) {
                pendingStartEventId = -1;
            }

            pendingEvents.add(row);
            pendingCount++;

            if (pendingCount >= checkpointBatchSize) {
                integrityLogDao.insertEventsBatch(pendingEvents);

                Long maxId = integrityLogDao.getMaxEventId();
                long endId = (maxId == null) ? 0L : maxId;
                long startId = endId - pendingEvents.size() + 1;

                IntegrityLogDao.CheckpointState st = integrityLogDao.lockCheckpointState();
                String prev = (st.lastCheckpointHash == null || st.lastCheckpointHash.isBlank()) ? "GENESIS" : st.lastCheckpointHash;
                long batchNo = st.nextBatchNo;

                String checkpointMaterial = prev + "|" + batchNo + "|" + startId + "|" + endId + "|" +
                    pendingCount + "|" + pendingRoot + "|" + SecurityUtil.getChainSecret();
                String checkpointHash = SecurityUtil.sha256Hex(checkpointMaterial);

                integrityLogDao.insertCheckpoint(batchNo, startId, endId, pendingCount,
                    pendingRoot, prev, checkpointHash);

                integrityLogDao.updateCheckpointState(checkpointHash, batchNo + 1);

                pendingEvents.clear();
                pendingCount = 0;
                pendingRoot = "BATCH_GENESIS";
                pendingStartEventId = -1;
            }
        }
    }

    /**
     * Общая проверка: цепочка + (опционально) сверка текущих данных с последним залогированным.
     */
    public IntegrityCheckReport check(boolean verifyData, int limit) {
        long checkedAt = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        // 1) проверка целостности цепочки STRICT-логов
        List<IntegrityLogEntry> all = integrityLogDao.findAllOrdered();
        String expectedPrev = "GENESIS";

        for (IntegrityLogEntry e : all) {
            String ownerPart = (e.getOwnerId() == null) ? "ownerId=—" : ("ownerId=" + e.getOwnerId());
            String freqPart  = (e.getFreqId() == null) ? "freqId=—"  : ("freqId=" + e.getFreqId());

            if (e.getPrevHash() == null || !e.getPrevHash().equals(expectedPrev)) {
                issues.add("CHAIN_BROKEN at logEntryId=" + e.getId() +
                    " (" + ownerPart + ", " + freqPart + ")" +
                    " prevHash=" + e.getPrevHash() + " expectedPrev=" + expectedPrev);
            }

            String material = e.getPrevHash() + "|" + e.getEventMs() + "|" + e.getActorUsername() + "|" +
                e.getAction() + "|" + (e.getFreqId() == null ? "" : e.getFreqId()) + "|" +
                e.getDataHash() + "|" + SecurityUtil.getChainSecret();
            String expectedHash = SecurityUtil.sha256Hex(material);

            if (!expectedHash.equals(e.getChainHash())) {
                issues.add("HASH_MISMATCH at logEntryId=" + e.getId() +
                    " (" + ownerPart + ", " + freqPart + ")");
            }

            expectedPrev = e.getChainHash();
        }

        int chainIssues = issues.size();

        // 2) проверка “текущие данные не отличаются от последних залогированных”
        int dataIssues = 0;
        if (verifyData) {
            List<IntegrityLogEntry> latest = integrityLogDao.findLatestPerFreq(limit);
            for (IntegrityLogEntry e : latest) {
                String ownerPart = (e.getOwnerId() == null) ? "ownerId=—" : ("ownerId=" + e.getOwnerId());
                String freqPart  = (e.getFreqId() == null) ? "freqId=—"  : ("freqId=" + e.getFreqId());

                try {
                    if (e.getFreqId() == null) continue;
                    FreqView current = freqDao.getById(e.getFreqId());
                    String currentHash = SecurityUtil.freqDataHash(
                        current.getOwnerModel(), current.getSiteModel(), current.getFreqModel()
                    );
                    if (!currentHash.equals(e.getDataHash())) {
                        dataIssues++;
                        issues.add("DATA_MISMATCH (" + ownerPart + ", " + freqPart + ") lastLogEntryId=" + e.getId());
                    }
                } catch (Exception ex) {
                    dataIssues++;
                    issues.add("DATA_CHECK_ERROR (" + ownerPart + ", " + freqPart + ") err=" + ex.getMessage());
                }
            }
        }

        boolean ok = issues.isEmpty();

        return IntegrityCheckReport.builder()
            .ok(ok)
            .checkedAtMs(checkedAt)
            .logEntriesChecked(all.size())
            .chainIssues(chainIssues)
            .dataIssues(dataIssues)
            .issues(issues)
            .build();
    }

    /**
     * Проверка одной записи (freqId): сравнить текущий хэш данных с последним залогированным.
     * Если в LOG нет записей по freqId — пробуем EVENT.
     */
    public IntegrityCheckReport checkFreq(long freqId) {
        long checkedAt = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        // 1) ищем последнюю запись в LOG
        IntegrityLogEntry last = integrityLogDao.findLatestForFreq("LOG", freqId);
        String source = "LOG";

        // 2) если LOG пуст — пробуем EVENT
        if (last == null) {
            last = integrityLogDao.findLatestForFreq("EVENT", freqId);
            source = "EVENT";
        }


        IntegrityLogEntry prev = (last == null) ? null : integrityLogDao.findPreviousForFreq(source, freqId);
        Long prevLogId = (prev == null) ? null : prev.getId();

        if (last == null) {
            issues.add("NO_LOG_FOR_FREQ freqId=" + freqId);
            return IntegrityCheckReport.builder()
                .ok(false)
                .checkedAtMs(checkedAt)
                .freqId(freqId)
                .source(null)
                .logEntriesChecked(0)
                .chainIssues(0)
                .dataIssues(1)
                .issues(issues)
                .build();
        }

        String expectedHash = last.getDataHash();
        String actualHash;

        try {
            FreqView current = freqDao.getById(freqId);
            actualHash = SecurityUtil.freqDataHash(current.getOwnerModel(), current.getSiteModel(), current.getFreqModel());
        } catch (Exception ex) {
            issues.add("DATA_CHECK_ERROR freqId=" + freqId + " err=" + ex.getMessage());
            return IntegrityCheckReport.builder()
                .ok(false)
                .checkedAtMs(checkedAt)
                .freqId(freqId)
                .source(source)
                .lastLogId(last.getId())
                .prevLogId(prevLogId)
                .expectedHash(expectedHash)
                .actualHash(null)
                .logEntriesChecked(1)
                .chainIssues(0)
                .dataIssues(1)
                .issues(issues)
                .build();
        }

        if (expectedHash == null || !expectedHash.equals(actualHash)) {
            issues.add("DATA_MISMATCH freqId=" + freqId + " lastLogId=" + last.getId() + " source=" + source);
        }

        boolean ok = issues.isEmpty();

        return IntegrityCheckReport.builder()
            .ok(ok)
            .checkedAtMs(checkedAt)
            .freqId(freqId)
            .source(source)
            .lastLogId(last.getId())
            .prevLogId(prevLogId)
            .expectedHash(expectedHash)
            .actualHash(actualHash)
            .logEntriesChecked(1)
            .chainIssues(0)
            .dataIssues(ok ? 0 : 1)
            .issues(issues)
            .build();
    }

    /**
     * Отладка хэша: показать каноническую строку и значения полей,
     * которые реально участвуют в вычислении хэша по freqId.
     */
    public IntegrityHashDebugReport debugFreq(long freqId) {
        long checkedAt = System.currentTimeMillis();

        IntegrityLogEntry last = integrityLogDao.findLatestForFreq("LOG", freqId);
        String source = "LOG";
        if (last == null) {
            last = integrityLogDao.findLatestForFreq("EVENT", freqId);
            source = "EVENT";
        }

        String expected = (last == null) ? null : last.getDataHash();
        Long lastLogId = (last == null) ? null : last.getId();
        IntegrityLogEntry prev = (last == null) ? null : integrityLogDao.findPreviousForFreq(source, freqId);
        Long prevLogId = (prev == null) ? null : prev.getId();

        try {
            FreqView current = freqDao.getById(freqId);
            OwnerModel owner = current.getOwnerModel();
            SiteModel site = current.getSiteModel();
            FreqModel freq = current.getFreqModel();

            LinkedHashMap<String, String> fields = SecurityUtil.canonicalFreqFields(owner, site, freq);
            String canonical = SecurityUtil.canonicalFreqString(owner, site, freq);
            String actual = SecurityUtil.freqDataHash(owner, site, freq);

            boolean ok = (expected != null && expected.equals(actual));

            String err = null;
            if (last == null) {
                err = "NO_LOG_FOR_FREQ freqId=" + freqId;
            }

            return IntegrityHashDebugReport.builder()
                .ok(ok)
                .checkedAtMs(checkedAt)
                .freqId(freqId)
                .source(last == null ? null : source)
                .lastLogId(lastLogId)
                .prevLogId(prevLogId)
                .expectedHash(expected)
                .actualHash(actual)
                .canonicalString(canonical)
                .canonicalFields(fields)
                .error(err)
                .build();

        } catch (Exception ex) {
            return IntegrityHashDebugReport.builder()
                .ok(false)
                .checkedAtMs(checkedAt)
                .freqId(freqId)
                .source(last == null ? null : source)
                .lastLogId(lastLogId)
                .prevLogId(prevLogId)
                .expectedHash(expected)
                .actualHash(null)
                .canonicalString(null)
                .canonicalFields(null)
                .error("DATA_CHECK_ERROR freqId=" + freqId + " err=" + ex.getMessage())
                .build();
        }
    }

    private String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return "SYSTEM";
        if (!a.isAuthenticated()) return "SYSTEM";
        return a.getName() == null ? "SYSTEM" : a.getName();
    }
}

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
        FINGERPRINT, // HMAC evidence per write, without hash chaining
        STRICT,      // prev_hash/chain_hash on every operation
        CHECKPOINT   // HMAC events are buffered and checkpoint-linked every K events
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
        if (!enabled || freqId == null) return;
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
        if (!enabled || freqId == null) return;
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

        if (strategy == Strategy.FINGERPRINT) {
            appendFingerprint(action, view);
            return;
        }

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

    /**
     * Persist one unchained HMAC evidence row for the current database state.
     * This is the code-faithful HMAC-only mode used by the revised benchmark.
     */
    @Transactional
    protected void appendFingerprint(String action, FreqView view) {
        long eventMs = System.currentTimeMillis();
        String actor = currentUsername();

        OwnerModel owner = view == null ? null : view.getOwnerModel();
        SiteModel site   = view == null ? null : view.getSiteModel();
        FreqModel freq   = view == null ? null : view.getFreqModel();
        Long freqId      = (freq == null) ? null : freq.getFreqId();

        String dataHash = SecurityUtil.freqDataHash(owner, site, freq);
        integrityLogDao.insertEvent(eventMs, actor, action, freqId, dataHash);
    }

    /**
     * Clears only the volatile CHECKPOINT accumulator.
     * Intended for controlled tests/benchmarks so every measured run starts from the same state.
     * Persistent integrity tables are reset separately by the benchmark runner.
     */
    public void resetCheckpointAccumulator() {
        synchronized (checkpointLock) {
            pendingEvents.clear();
            pendingCount = 0;
            pendingRoot = "BATCH_GENESIS";
            pendingStartEventId = -1;
        }
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
     * Verify the internal consistency of finalized CHECKPOINT evidence.
     *
     * The verifier:
     * 1) reads finalized checkpoints in batch order;
     * 2) reloads the exact event interval referenced by each checkpoint;
     * 3) recomputes the rolling root from persisted event fields using the same
     *    material and chain secret as appendCheckpoint();
     * 4) recomputes each checkpoint hash and validates the checkpoint links;
     * 5) checks that integrity_checkpoint_state matches the recomputed retained chain.
     *
     * IMPORTANT:
     * This establishes consistency of the retained same-database evidence only.
     * It cannot independently prove freshness/completeness after coordinated suffix
     * deletion or whole-database rollback. Those cases require an expected anchor
     * retained outside the writable database trust domain.
     */
    public CheckpointVerificationReport verifyCheckpointChain() {
        long checkedAt = System.currentTimeMillis();
        List<String> issues = new ArrayList<>();

        List<IntegrityLogDao.CheckpointRow> checkpoints =
            integrityLogDao.findAllCheckpointsOrdered();

        String expectedPrevCheckpointHash = "GENESIS";
        long expectedBatchNo = 1L;
        long eventsChecked = 0L;

        for (IntegrityLogDao.CheckpointRow cp : checkpoints) {
            if (cp.batchNo != expectedBatchNo) {
                issues.add("BATCH_SEQUENCE_MISMATCH checkpointId=" + cp.id
                    + " batchNo=" + cp.batchNo
                    + " expectedBatchNo=" + expectedBatchNo);
            }

            String storedPrev = normalizeGenesis(cp.prevCheckpointHash);
            if (!storedPrev.equals(expectedPrevCheckpointHash)) {
                issues.add("PREV_CHECKPOINT_HASH_MISMATCH checkpointId=" + cp.id
                    + " batchNo=" + cp.batchNo
                    + " storedPrev=" + storedPrev
                    + " expectedPrev=" + expectedPrevCheckpointHash);
            }

            List<IntegrityLogDao.CheckpointEventRow> events =
                integrityLogDao.findCheckpointEvents(cp.startEventId, cp.endEventId);
            eventsChecked += events.size();

            if (cp.eventCount < 1) {
                issues.add("INVALID_EVENT_COUNT checkpointId=" + cp.id
                    + " eventCount=" + cp.eventCount);
            }

            if (cp.startEventId > cp.endEventId) {
                issues.add("INVALID_EVENT_RANGE checkpointId=" + cp.id
                    + " startEventId=" + cp.startEventId
                    + " endEventId=" + cp.endEventId);
            }

            if (events.size() != cp.eventCount) {
                issues.add("EVENT_COUNT_MISMATCH checkpointId=" + cp.id
                    + " batchNo=" + cp.batchNo
                    + " storedEventCount=" + cp.eventCount
                    + " loadedEvents=" + events.size());
            }

            if (!events.isEmpty()) {
                long actualStart = events.get(0).id;
                long actualEnd = events.get(events.size() - 1).id;

                if (actualStart != cp.startEventId) {
                    issues.add("START_EVENT_ID_MISMATCH checkpointId=" + cp.id
                        + " storedStart=" + cp.startEventId
                        + " actualStart=" + actualStart);
                }
                if (actualEnd != cp.endEventId) {
                    issues.add("END_EVENT_ID_MISMATCH checkpointId=" + cp.id
                        + " storedEnd=" + cp.endEventId
                        + " actualEnd=" + actualEnd);
                }
            }

            String recomputedRoot = "BATCH_GENESIS";
            for (IntegrityLogDao.CheckpointEventRow event : events) {
                String material = event.eventMs + "|"
                    + nullToEmpty(event.actorUsername) + "|"
                    + nullToEmpty(event.action) + "|"
                    + (event.freqId == null ? "" : event.freqId) + "|"
                    + nullToEmpty(event.dataHash);

                recomputedRoot = SecurityUtil.sha256Hex(
                    recomputedRoot + "|" + material + "|" + SecurityUtil.getChainSecret()
                );
            }

            if (cp.rootHash == null || !cp.rootHash.equals(recomputedRoot)) {
                issues.add("ROOT_HASH_MISMATCH checkpointId=" + cp.id
                    + " batchNo=" + cp.batchNo);
            }

            // Recompute from the fields that are actually persisted in the checkpoint row,
            // but use the root reconstructed from the referenced events.
            String checkpointMaterial =
                storedPrev + "|" + cp.batchNo + "|" + cp.startEventId + "|"
                    + cp.endEventId + "|" + cp.eventCount + "|"
                    + recomputedRoot + "|" + SecurityUtil.getChainSecret();

            String expectedCheckpointHash =
                SecurityUtil.sha256Hex(checkpointMaterial);

            if (cp.checkpointHash == null
                    || !cp.checkpointHash.equals(expectedCheckpointHash)) {
                issues.add("CHECKPOINT_HASH_MISMATCH checkpointId=" + cp.id
                    + " batchNo=" + cp.batchNo);
            }

            // Advance using the recomputed value, not the stored checkpoint_hash.
            // This avoids trusting a possibly modified stored hash when checking
            // the next checkpoint link and the retained checkpoint state.
            expectedPrevCheckpointHash = expectedCheckpointHash;
            expectedBatchNo++;
        }

        IntegrityLogDao.CheckpointState retainedState =
            integrityLogDao.readCheckpointState();

        String retainedLast = normalizeGenesis(retainedState.lastCheckpointHash);
        if (!retainedLast.equals(expectedPrevCheckpointHash)) {
            issues.add("CHECKPOINT_STATE_HASH_MISMATCH retained="
                + retainedLast + " expected=" + expectedPrevCheckpointHash);
        }

        if (retainedState.nextBatchNo != expectedBatchNo) {
            issues.add("CHECKPOINT_STATE_BATCH_MISMATCH retainedNextBatchNo="
                + retainedState.nextBatchNo + " expectedNextBatchNo=" + expectedBatchNo);
        }

        return CheckpointVerificationReport.builder()
            .ok(issues.isEmpty())
            .checkedAtMs(checkedAt)
            .checkpointsChecked(checkpoints.size())
            .eventsChecked(eventsChecked)
            .issuesCount(issues.size())
            .retainedLastCheckpointHash(retainedLast)
            .retainedNextBatchNo(retainedState.nextBatchNo)
            .issues(issues)
            .build();
    }

    private static String normalizeGenesis(String value) {
        return (value == null || value.isBlank()) ? "GENESIS" : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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

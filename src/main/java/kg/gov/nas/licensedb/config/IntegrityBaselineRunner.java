package kg.gov.nas.licensedb.config;

import kg.gov.nas.licensedb.service.IntegrityBaselineStrictResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Автозапуск baseline (Policy A) при старте приложения.
 *
 * Включается ТОЛЬКО через application.properties (или -D / env):
 *
 * baseline.strict.reset.enabled=true
 * baseline.strict.reset.confirm=true
 *
 * Дополнительно можно ограничить диапазоном owner.ID:
 * baseline.strict.reset.ownerFrom=1
 * baseline.strict.reset.ownerTo=999999
 *
 * Размер батча (коммит пачками):
 * baseline.strict.reset.batchSize=5000
 *
 * Важно: baseline удаляет (DELETE) все записи:
 * - integrity_incident
 * - freq_integrity_log
 * - freq_integrity_event / integrity_checkpoint* (через resetCheckpointTables)
 * и сбрасывает integrity_chain_state.last_hash='GENESIS'.
 *
 * Запускайте только в окно обслуживания.
 */
@Component
@Order(10000)
@RequiredArgsConstructor
public class IntegrityBaselineRunner implements ApplicationRunner {

    private final IntegrityBaselineStrictResetService baselineService;

    @Value("${baseline.strict.reset.enabled:false}")
    private boolean enabled;

    @Value("${baseline.strict.reset.confirm:false}")
    private boolean confirm;

    @Value("${baseline.strict.reset.ownerFrom:#{null}}")
    private Long ownerFrom;

    @Value("${baseline.strict.reset.ownerTo:#{null}}")
    private Long ownerTo;

    @Value("${baseline.strict.reset.batchSize:5000}")
    private int batchSize;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;

        if (!confirm) {
            throw new IllegalStateException(
                "Baseline RESET is enabled but not confirmed. Set baseline.strict.reset.confirm=true to run.");
        }

        System.out.println("=== BASELINE RESET (STRICT) START ===");
        System.out.println("ownerFrom=" + ownerFrom + ", ownerTo=" + ownerTo + ", batchSize=" + batchSize);

        IntegrityBaselineStrictResetService.BaselineResult r =
            baselineService.resetAndBaselineStrict(ownerFrom, ownerTo, batchSize);

        System.out.println("=== BASELINE RESET (STRICT) DONE ===");
        System.out.println("processedRows=" + r.processedRows + ", insertedLogs=" + r.insertedLogs +
            ", startedAtMs=" + r.startedAtMs + ", finishedAtMs=" + r.finishedAtMs);
    }
}

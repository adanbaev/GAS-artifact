package kg.gov.nas.licensedb.service;

import kg.gov.nas.licensedb.dao.FreqDao;
import kg.gov.nas.licensedb.dao.IntegrityLogDao;
import kg.gov.nas.licensedb.dto.*;
import kg.gov.nas.licensedb.enums.FreqMode;
import kg.gov.nas.licensedb.enums.FreqType;
import kg.gov.nas.licensedb.util.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntegrityServiceTest {

    private IntegrityLogDao integrityLogDao;
    private FreqDao freqDao;
    private IntegrityService integrityService;

    @BeforeEach
    void setup() {
        integrityLogDao = Mockito.mock(IntegrityLogDao.class);
        freqDao = Mockito.mock(FreqDao.class);
        integrityService = new IntegrityService(integrityLogDao, freqDao);

        SecurityUtil.setChainSecret("test-chain-secret");
        SecurityUtil.setSignatureSecret("test-signature-secret");

        SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken("tester", "x", java.util.Collections.emptyList())
);

    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logUpdate_appendsLog_andAdvancesLastHash() {
        when(integrityLogDao.lockAndGetLastHash()).thenReturn("GENESIS");

        FreqView view = sampleView(10L, 100.0, 25.0);

        integrityService.logUpdate(view);

        ArgumentCaptor<Long> eventMs = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> freqId = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> dataHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> prevHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> chainHash = ArgumentCaptor.forClass(String.class);

        verify(integrityLogDao).insertLog(
                eventMs.capture(),
                actor.capture(),
                action.capture(),
                freqId.capture(),
                dataHash.capture(),
                prevHash.capture(),
                chainHash.capture()
        );

        assertEquals("tester", actor.getValue());
        assertEquals("UPDATE", action.getValue());
        assertEquals(10L, freqId.getValue());
        assertEquals("GENESIS", prevHash.getValue());

        // Проверяем, что chainHash соответствует материалу
        String material = prevHash.getValue() + "|" + eventMs.getValue() + "|" + actor.getValue() + "|" +
                action.getValue() + "|" + freqId.getValue() + "|" + dataHash.getValue() + "|" + SecurityUtil.getChainSecret();
        String expected = SecurityUtil.sha256Hex(material);

        assertEquals(expected, chainHash.getValue(), "chainHash должен совпадать с sha256(material)");
        verify(integrityLogDao).updateLastHash(chainHash.getValue());
    }

    @Test
    void check_detectsDataTampering_whenDbChangedOutsideApp() throws Exception {
        // 1) лог-цепочка корректная
        FreqView original = sampleView(10L, 100.0, 25.0);
        String dataHash = SecurityUtil.freqDataHash(original.getOwnerModel(), original.getSiteModel(), original.getFreqModel());

        long eventMs = 1700000000000L;
        String prev = "GENESIS";
        String actor = "tester";
        String action = "UPDATE";

        String material = prev + "|" + eventMs + "|" + actor + "|" + action + "|" + 10L + "|" + dataHash + "|" + SecurityUtil.getChainSecret();
        String chain = SecurityUtil.sha256Hex(material);

        IntegrityLogEntry entry = IntegrityLogEntry.builder()
                .id(1L)
                .eventMs(eventMs)
                .actorUsername(actor)
                .action(action)
                .freqId(10L)
                .dataHash(dataHash)
                .prevHash(prev)
                .chainHash(chain)
                .build();

        when(integrityLogDao.findAllOrdered()).thenReturn(List.of(entry));
        when(integrityLogDao.findLatestPerFreq(2000)).thenReturn(List.of(entry));

        // 2) "текущие данные" отличаются (симулируем ручной UPDATE в БД)
        FreqView tampered = sampleView(10L, 101.0, 25.0); // поменяли nominal
        when(freqDao.getById(10L)).thenReturn(tampered);

        IntegrityCheckReport report = integrityService.check(true, 2000);

        assertFalse(report.isOk(), "При подмене данных отчёт должен быть ok=false");
        assertTrue(report.getIssues().stream().anyMatch(s -> s.contains("DATA_MISMATCH") && s.contains("freqId=10")),
                "Должна быть проблема DATA_MISMATCH по freqId=10");
    }

    @Test
    void check_detectsBrokenChain_whenLogIsTampered() {
        IntegrityLogEntry e1 = IntegrityLogEntry.builder()
                .id(1L).eventMs(1L).actorUsername("a").action("UPDATE").freqId(1L)
                .dataHash("h1").prevHash("GENESIS").chainHash("c1")
                .build();

        // ломаем prevHash
        IntegrityLogEntry e2 = IntegrityLogEntry.builder()
                .id(2L).eventMs(2L).actorUsername("a").action("UPDATE").freqId(1L)
                .dataHash("h2").prevHash("WRONG_PREV").chainHash("c2")
                .build();

        when(integrityLogDao.findAllOrdered()).thenReturn(List.of(e1, e2));
        when(integrityLogDao.findLatestPerFreq(anyInt())).thenReturn(List.of());

        IntegrityCheckReport report = integrityService.check(false, 2000);

        assertFalse(report.isOk());
        assertTrue(report.getIssues().stream().anyMatch(s -> s.contains("CHAIN_BROKEN") || s.contains("HASH_MISMATCH")),
                "Должны быть CHAIN_BROKEN и/или HASH_MISMATCH");
    }

    private static FreqView sampleView(Long freqId, double nominal, double band) {
        OwnerModel ow = new OwnerModel();
        ow.setOwnerId(1L);
        ow.setOwnerName("Org");
        ow.setIssueDate(LocalDate.of(2026, 1, 1));
        ow.setExpireDate(LocalDate.of(2027, 1, 1));

        SiteModel s = new SiteModel();
        s.setSiteId(2L);
        s.setLatitude0(42);
        s.setLatitude1(0);
        s.setLatitude2(0);
        s.setLongitude0(74);
        s.setLongitude1(0);
        s.setLongitude2(0);

        FreqModel f = new FreqModel();
        f.setFreqId(freqId);
        f.setNominal(nominal);
        f.setBand(band);
        f.setMode(FreqMode.BROADCAST);
        f.setType(FreqType.ANALOGUE);
        f.setMeaning("X");

        FreqView view = new FreqView();
        view.setOwnerModel(ow);
        view.setSiteModel(s);
        view.setFreqModel(f);
        return view;
    }
	@Test
void logUpdate_chainGrowsLinearly_prevHashEqualsPreviousChainHash() {
    java.util.concurrent.atomic.AtomicReference<String> lastHash = new java.util.concurrent.atomic.AtomicReference<>("GENESIS");

    // lockAndGetLastHash всегда возвращает актуальный lastHash
    when(integrityLogDao.lockAndGetLastHash()).thenAnswer(inv -> lastHash.get());

    // updateLastHash обновляет lastHash
    doAnswer(inv -> {
        String newHash = inv.getArgument(0, String.class);
        lastHash.set(newHash);
        return null;
    }).when(integrityLogDao).updateLastHash(anyString());

    FreqView view = sampleView(10L, 100.0, 25.0);

    // два последовательных апдейта
    integrityService.logUpdate(view);
    integrityService.logUpdate(view);

    // захватываем prevHash и chainHash для обеих вставок
    org.mockito.ArgumentCaptor<String> prevCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
    org.mockito.ArgumentCaptor<String> chainCaptor = org.mockito.ArgumentCaptor.forClass(String.class);

    verify(integrityLogDao, times(2)).insertLog(
            anyLong(),          // eventMs
            anyString(),        // actor
            anyString(),        // action
            any(),              // freqId
            anyString(),        // dataHash
            prevCaptor.capture(),
            chainCaptor.capture()
    );

    java.util.List<String> prevs = prevCaptor.getAllValues();
    java.util.List<String> chains = chainCaptor.getAllValues();

    assertEquals(2, prevs.size());
    assertEquals(2, chains.size());

    // первая запись начинается от GENESIS
    assertEquals("GENESIS", prevs.get(0));

    // вторая запись должна ссылаться на chainHash первой
    assertEquals(chains.get(0), prevs.get(1), "prevHash второго лога должен равняться chainHash первого лога");
}

}

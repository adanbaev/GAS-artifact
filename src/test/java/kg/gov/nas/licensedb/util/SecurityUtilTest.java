package kg.gov.nas.licensedb.util;

import kg.gov.nas.licensedb.dto.FreqModel;
import kg.gov.nas.licensedb.dto.OwnerModel;
import kg.gov.nas.licensedb.dto.SiteModel;
import kg.gov.nas.licensedb.enums.FreqMode;
import kg.gov.nas.licensedb.enums.FreqType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilTest {

    @Test
    void generateDigitalSignature_changesWhenSecretChanges() {
        SecurityUtil.setSignatureSecret("s1");
        String a = SecurityUtil.generateDigitalSignature("Org", 100.0, LocalDate.of(2026, 1, 1));

        SecurityUtil.setSignatureSecret("s2");
        String b = SecurityUtil.generateDigitalSignature("Org", 100.0, LocalDate.of(2026, 1, 1));

        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(a, b, "Подпись должна меняться при смене секрета");
    }

    @Test
    void freqDataHash_changesWhenProtectedFieldsChange() {
        OwnerModel ow = new OwnerModel();
        ow.setOwnerId(1L);
        ow.setOwnerName("Org");
        ow.setIssueDate(LocalDate.of(2026, 1, 1));
        ow.setExpireDate(LocalDate.of(2027, 1, 1));

        SiteModel s = new SiteModel();
        s.setSiteId(2L);

        FreqModel f = new FreqModel();
        f.setFreqId(10L);
        f.setNominal(100.0);
        f.setBand(25.0);
        f.setMode(FreqMode.BROADCAST);
        f.setType(FreqType.ANALOGUE);
        f.setMeaning("X");

        String h1 = SecurityUtil.freqDataHash(ow, s, f);

        // меняем важное поле
        f.setBand(26.0);
        String h2 = SecurityUtil.freqDataHash(ow, s, f);

        assertNotEquals(h1, h2, "dataHash должен меняться при изменении защищаемых полей");
    }
}

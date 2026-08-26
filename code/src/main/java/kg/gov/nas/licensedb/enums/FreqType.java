package kg.gov.nas.licensedb.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum FreqType {
    N_VIDEO(0, "Несущая ВИДЕО"),
    N_VOICE(1, "Несущая ЗВУКА"),
    DIGITAL(2, "Цифра"),
    ANALOGUE(3, "Аналоговая"),
    UNKNOWN(4, "Неизвестна"),
    ANY(5, "Любая");

    private static final Map<Integer, FreqType> storage = new HashMap<>();

    FreqType(int code, String value){
        this.code = code;
        this.value = value;
    }

    static {
        storage.put(N_VIDEO.code, N_VIDEO);
        storage.put(N_VOICE.code, N_VOICE);
        storage.put(DIGITAL.code, DIGITAL);
        storage.put(ANALOGUE.code, ANALOGUE);
        storage.put(UNKNOWN.code, UNKNOWN);
        storage.put(ANY.code, ANY);
    }

    private int code;
    private String value;

    public String getValue() {
        return value;
    }

    public int getCode() {
        return code;
    }

    public static FreqType fromCode(int code){
        return Objects.requireNonNull(storage.get(code), "Unknown FreqType for given code: " + code + ".");
    }
}

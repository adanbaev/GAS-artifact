package kg.gov.nas.licensedb.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum FreqMode {
    BROADCAST(0, "Передача"),
    RECEIVER(1, "Прием"),
    SIMPLEX(2, "Симплекс"),
    RESERVE(3, "Резерв");

    private static final Map<Integer, FreqMode> storage = new HashMap<>();

    FreqMode(int code, String value){
        this.code = code;
        this.value = value;
    }

    static {
        storage.put(BROADCAST.code, BROADCAST);
        storage.put(RECEIVER.code, RECEIVER);
        storage.put(SIMPLEX.code, SIMPLEX);
        storage.put(RESERVE.code, RESERVE);
    }

    private int code;
    private String value;

    public String getValue() {
        return value;
    }

    public int getCode() {
        return code;
    }

    public static FreqMode fromCode(int code){
        return Objects.requireNonNull(storage.get(code), "Unknown FreqMode for given code: " + code + ".");
    }
}

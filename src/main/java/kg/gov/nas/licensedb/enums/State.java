package kg.gov.nas.licensedb.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum State {
    ACTUAL(0, "Действующее"),
    INTERNATIONAL(1, "Международное"),
    ANNUL(2, "Аннулировано"),
    RESERVE(3, "Резерв"),
    UNKNOWN(4, "Неизвестно"),
    TEMP(5, "Временное"),
    CONSIDERATION(6, "На рассмотрении"),
    EXPIRED(7, "Истек срок");

    private static final Map<Integer, State> storage = new HashMap<>();

    State(int code, String value){
        this.code = code;
        this.value = value;
    }

    static {
        storage.put(ACTUAL.code, ACTUAL);
        storage.put(INTERNATIONAL.code, INTERNATIONAL);
        storage.put(ANNUL.code, ANNUL);
        storage.put(RESERVE.code, RESERVE);
        storage.put(UNKNOWN.code, UNKNOWN);
        storage.put(TEMP.code, TEMP);
        storage.put(CONSIDERATION.code, CONSIDERATION);
        storage.put(EXPIRED.code, EXPIRED);
    }

    private int code;
    private String value;

    public String getValue() {
        return value;
    }

    public int getCode() {
        return code;
    }

    public static State fromCode(int code){
        if(code >= 0 && code < 8){
            return Objects.requireNonNull(storage.get(code), "Unknown State for given code: " + code + ".");
        }

        return null;
    }
}

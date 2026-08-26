package kg.gov.nas.licensedb.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum Region {
    CHUI(0, "Чуйская"),
    OSH(1, "Ошская"),
    TALAS(2, "Таласская"),
    JALALABAD(3, "Джалал-Абадская"),
    IK(4, "Иссык-Кульская"),
    NARYN(5, "Нарынская"),
    BATKEN(6, "Баткенская");

    private static final Map<Integer, Region> storage = new HashMap<>();

    Region(int code, String value){
        this.code = code;
        this.value = value;
    }

    static {
        storage.put(CHUI.code, CHUI);
        storage.put(OSH.code, OSH);
        storage.put(TALAS.code, TALAS);
        storage.put(JALALABAD.code, JALALABAD);
        storage.put(IK.code, IK);
        storage.put(NARYN.code, NARYN);
        storage.put(BATKEN.code, BATKEN);
    }

    private int code;
    private String value;

    public String getValue() {
        return value;
    }

    public int getCode() {
        return code;
    }

    public static Region fromCode(int code){
        return Objects.requireNonNull(storage.get(code), "Unknown Region for given code: " + code + ".");
    }
}

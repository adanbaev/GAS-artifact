package kg.gov.nas.licensedb.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum Polar {
    VERTICAL(0, "Вертикальная"),
    HORIZONTAL(1, "Горизонтальная"),
    CIRCULAR(2, "Круговая"),
    UNKNOWN(3, "Неизвестная"),
    LINEAR(4, "Линейная"),
    HORIZONTAL_AND_VERTICAL(5, "Гориз+Верт"),
    LINEAR_AND_VERTICAL(6, "Линейн+Верт"),
    LINEAR_AND_HORIZONTAL(7, "Линейн+Гориз"),
    BIPOLAR(8, "Биполярная"),
    TRIPLE(9, "Линейн.+Гориз.+Верт"),
    CROSS_POLAR(10, "Кросс-Поляризация");

    private static final Map<Integer, Polar> storage = new HashMap<>();

    Polar(int code, String value){
        this.code = code;
        this.value = value;
    }

    static {
        storage.put(VERTICAL.code, VERTICAL);
        storage.put(HORIZONTAL.code, HORIZONTAL);
        storage.put(CIRCULAR.code, CIRCULAR);
        storage.put(UNKNOWN.code, UNKNOWN);
        storage.put(LINEAR.code, LINEAR);
        storage.put(HORIZONTAL_AND_VERTICAL.code, HORIZONTAL_AND_VERTICAL);
        storage.put(LINEAR_AND_VERTICAL.code, LINEAR_AND_VERTICAL);
        storage.put(LINEAR_AND_HORIZONTAL.code, LINEAR_AND_HORIZONTAL);
        storage.put(BIPOLAR.code, BIPOLAR);
        storage.put(TRIPLE.code, TRIPLE);
        storage.put(CROSS_POLAR.code, CROSS_POLAR);
    }

    private int code;
    private String value;

    public String getValue() {
        return value;
    }

    public int getCode() {
        return code;
    }

    public static Polar fromCode(int code){
        return Objects.requireNonNull(storage.get(code), "Unknown Polar for given code: " + code + ".");
    }
}

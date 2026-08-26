package kg.gov.nas.licensedb.enums;

public enum OwnerBasis {
    PRIMARY("Первичная основа"),
    SECONDARY("Вторичная основа");

    OwnerBasis(String value){
        this.value =value;
    }

    private String value;

    public String getValue() {
        return value;
    }
}

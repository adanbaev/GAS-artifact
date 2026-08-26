package kg.gov.nas.licensedb.enums;

public enum RegStatus {
    PUSKON("пускон."),
    REGISTR("регистр."),
    PEREREGISTR("перерегистр.");

    RegStatus(String value){
        this.value =value;
    }

    private String value;

    public String getValue() {
        return value;
    }
}

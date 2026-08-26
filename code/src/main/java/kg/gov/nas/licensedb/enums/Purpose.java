package kg.gov.nas.licensedb.enums;

public enum Purpose {
    COM("коммерч."),
    PRO("произв.");

    Purpose(String value){
        this.value =value;
    }

    private String value;

    public String getValue() {
        return value;
    }
}

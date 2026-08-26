package kg.gov.nas.licensedb.dto;

import kg.gov.nas.licensedb.enums.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;

@Data
public class FreqModel {
    private Long freqId;
    private double nominal;
    private FreqType type;
    private double band;
    private FreqMode mode;
    private String meaning;
    private int mobStan;
    private boolean inco;
    private double satRadius;
    private double deviation;
    private int channel;
    private double snch;
    private String signature;

    // Служебное поле: вычисляемый хэш канонизированных данных записи.
    // Используется для вывода в PDF/печати как доказательство актуальности данных из БД.
    private String dataHash;

    private double reception;
    private double transfer;

    @Getter(AccessLevel.NONE)
    private String designation;
    private double originNominal;
    private int originMode;

    public String getDesignation(){
        double prefix = band != 0 ? band / 1000 : 0;
        return prefix + "M0" + meaning;
    }
}

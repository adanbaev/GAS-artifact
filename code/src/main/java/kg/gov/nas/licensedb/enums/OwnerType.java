package kg.gov.nas.licensedb.enums;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public enum OwnerType {
    MV(0, "МВ"),
    MV_(1, "МВ_"),
    CV(2, "СВ"),
    KV(3, "KV"),
    KV_(4, "KV_"),
    TR(5, "TR"),
    RRS(6, "RRS"),
    RV(7, "RV"),
    TV(8, "TV"),
    ES(9, "ES"),
    EST(10, "EST"),
    MD(11, "MD"),
    SEC(12, "SEC"),
    PG(13, "PG"),
    CEL(14, "CEL"),
    RAD(15, "Rad"),
    CATV(16, "CATV"),
    WLL(17, "WLL"),
    NAV(18, "NAV"),
    LK(19, "LK"),
    GBB(20, "GBB"),
    GBM(21, "GBM"),
    TLF(22, "TLF"),
    TLF_(23, "TLF_"),
    MV_KV_(24, "MВ_KV_"),
    MV_KV(25, "MВ-KV");

    private static final Map<Integer, OwnerType> storage = new HashMap<>();

    OwnerType(int code, String value){
        this.code = code;
        this.value = value;
    }

    static {
        storage.put(MV.code, MV);
        storage.put(MV_.code, MV_);
        storage.put(CV.code, CV);
        storage.put(KV.code, KV);
        storage.put(KV_.code, KV_);
        storage.put(TR.code, TR);
        storage.put(RRS.code, RRS);
        storage.put(RV.code, RV);
        storage.put(TV.code, TV);
        storage.put(ES.code, ES);
        storage.put(EST.code, EST);
        storage.put(MD.code, MD);
        storage.put(SEC.code, SEC);
        storage.put(PG.code, PG);
        storage.put(CEL.code, CEL);
        storage.put(RAD.code, RAD);
        storage.put(CATV.code, CATV);
        storage.put(WLL.code, WLL);
        storage.put(NAV.code, NAV);
        storage.put(LK.code, LK);
        storage.put(GBB.code, GBB);
        storage.put(GBM.code, GBM);
        storage.put(TLF.code, TLF);
        storage.put(TLF_.code, TLF_);
        storage.put(MV_KV_.code, MV_KV_);
        storage.put(MV_KV.code, MV_KV);
    }

    private int code;
    private String value;

    public String getValue() {
        return value;
    }

    public int getCode() {
        return code;
    }

    public static OwnerType fromCode(int code){
        return Objects.requireNonNull(storage.get(code), "Unknown OwnerType for given code: " + code + ".");
    }
}

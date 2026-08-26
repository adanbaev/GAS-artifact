package kg.gov.nas.licensedb.util;

import kg.gov.nas.licensedb.dto.FreqModel;
import kg.gov.nas.licensedb.dto.OwnerModel;
import kg.gov.nas.licensedb.dto.SiteModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Objects;

public final class SecurityUtil {

    private static volatile String signatureSecret;
    private static volatile String chainSecret;

    // Для стабильного hashing: фиксируем scale (можно менять, но важно, чтобы было одинаково всегда)
    private static final int DOUBLE_SCALE = 6;

    private SecurityUtil() {}

    public static void setSignatureSecret(String secret) {
        signatureSecret = (secret == null) ? null : secret.trim();
    }

    public static void setChainSecret(String secret) {
        chainSecret = (secret == null) ? null : secret.trim();
    }

    public static String getSignatureSecret() {
        String s = signatureSecret;
        if (s != null && !s.isBlank()) return s;

        s = System.getProperty("security.signature.secret");
        if (s == null || s.isBlank()) s = System.getenv("SECURITY_SIGNATURE_SECRET");
        if (s == null || s.isBlank()) s = "CHANGE_ME";
        return s;
    }

    public static String getChainSecret() {
        String s = chainSecret;
        if (s != null && !s.isBlank()) return s;

        s = System.getProperty("security.integrity.chain-secret");
        if (s == null || s.isBlank()) s = System.getenv("INTEGRITY_CHAIN_SECRET");
        if (s == null || s.isBlank()) s = getSignatureSecret();
        return s;
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(encoded.length * 2);
            for (byte b : encoded) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "Error";
        }
    }

    // --- Stable formatting helpers ---

    private static String normString(Object o) {
        return (o == null) ? "" : o.toString();
    }

    private static String normLong(Long v) {
        return (v == null) ? "" : Long.toString(v);
    }

    private static String normInt(Integer v) {
        return (v == null) ? "" : Integer.toString(v);
    }

    private static String normBool(Boolean v) {
        return (v == null) ? "" : Boolean.toString(v);
    }

    private static String normDouble(Double v) {
        if (v == null) return "";
        if (v.isNaN() || v.isInfinite()) return "";

        // BigDecimal.valueOf(double) берёт "человеческое" представление и стабилен для строк
        BigDecimal bd = BigDecimal.valueOf(v)
                .setScale(DOUBLE_SCALE, RoundingMode.HALF_UP)
                .stripTrailingZeros();

        // toPlainString исключает научную нотацию
        return bd.toPlainString();
    }

    private static String normDate(LocalDate d) {
        return (d == null) ? "" : d.toString();
    }

    // --- Signatures ---

    public static String generateDigitalSignature(String ownerName, Double freq, LocalDate date) {
        if (ownerName == null || freq == null || date == null) return null;
        String original = ownerName + normDouble(freq) + date + getSignatureSecret();
        return sha256Hex(original);
    }

    // перегрузка под java.util.Date (если у вас где-то Date)
    public static String generateDigitalSignature(String ownerName, Double freq, Date date) {
        if (ownerName == null || freq == null || date == null) return null;
        LocalDate ld = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return generateDigitalSignature(ownerName, freq, ld);
    }

    // --- Canonical record string for integrity hashing ---

    /**
     * Возвращает канонические значения по полям в ФИКСИРОВАННОМ порядке.
     *
     * Важно: порядок и нормализация должны оставаться неизменными, иначе изменится хэш.
     * Этот метод нужен для “Отладки хэша”: показать, какие значения реально идут в хэш.
     */
    public static LinkedHashMap<String, String> canonicalFreqFields(OwnerModel owner, SiteModel site, FreqModel freq) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();

        m.put("ownerId", (owner == null) ? "" : normLong(owner.getOwnerId()));
        m.put("ownerName", (owner == null) ? "" : normString(owner.getOwnerName()));
        m.put("issueDate", (owner == null) ? "" : normDate(owner.getIssueDate()));
        m.put("expireDate", (owner == null) ? "" : normDate(owner.getExpireDate()));

        m.put("siteId", (site == null) ? "" : normLong(site.getSiteId()));
        m.put("latitude0", (site == null) ? "" : normInt(site.getLatitude0()));
        m.put("latitude1", (site == null) ? "" : normInt(site.getLatitude1()));
        m.put("latitude2", (site == null) ? "" : normInt(site.getLatitude2()));
        m.put("longitude0", (site == null) ? "" : normInt(site.getLongitude0()));
        m.put("longitude1", (site == null) ? "" : normInt(site.getLongitude1()));
        m.put("longitude2", (site == null) ? "" : normInt(site.getLongitude2()));

        m.put("freqId", (freq == null) ? "" : normLong(freq.getFreqId()));
        m.put("nominal", (freq == null) ? "" : normDouble(freq.getNominal()));
        m.put("band", (freq == null) ? "" : normDouble(freq.getBand()));
        m.put("mode", (freq == null || freq.getMode() == null) ? "" : freq.getMode().name());
        m.put("type", (freq == null || freq.getType() == null) ? "" : freq.getType().name());

        // channel/mobStan в модели обычно int, поэтому нормализуем как число
        m.put("channel", (freq == null) ? "" : Integer.toString(freq.getChannel()));
        m.put("deviation", (freq == null) ? "" : normDouble(freq.getDeviation()));
        m.put("snch", (freq == null) ? "" : normDouble(freq.getSnch()));
        m.put("inco", (freq == null) ? "" : normBool(freq.isInco()));
        m.put("satRadius", (freq == null) ? "" : normDouble(freq.getSatRadius()));
        m.put("mobStan", (freq == null) ? "" : Integer.toString(freq.getMobStan()));
        m.put("meaning", (freq == null) ? "" : Objects.toString(freq.getMeaning(), ""));

        return m;
    }

    public static String canonicalFreqString(OwnerModel owner, SiteModel site, FreqModel freq) {
        LinkedHashMap<String, String> fields = canonicalFreqFields(owner, site, freq);
        StringBuilder sb = new StringBuilder();
        for (String value : fields.values()) {
            String v = (value == null) ? "" : value;
            sb.append(v.length()).append(':').append(v).append(';');
        }
        return sb.toString();
    }

    public static String freqDataHash(OwnerModel owner, SiteModel site, FreqModel freq) {
        try {
            String canonical = canonicalFreqString(owner, site, freq);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                getSignatureSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmac = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hmac) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }
}

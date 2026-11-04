package ma.wwtrading.orders.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Authoritative delivery fee configuration and city/area matching rules.
 *
 * <p>Since {@code user_address} stores a free-text {@code city} column without a separate
 * geographic commune/province code, "Kenitra and surrounding areas" is matched deterministically
 * via Unicode NFD diacritic/case normalization against an explicit, maintainable list of
 * Kenitra localities ({@code kenitraSurroundingAreas}) or city names containing {@code "kenitra"},
 * rather than treating the entire Rabat-Salé-Kénitra administrative region as Kenitra.
 */
@Configuration
@ConfigurationProperties(prefix = "wwtrading.delivery")
public class DeliveryFeeProperties {

    public static final BigDecimal DEFAULT_KENITRA_FEE = new BigDecimal("300.00");
    public static final BigDecimal DEFAULT_CASABLANCA_FEE = new BigDecimal("300.00");
    public static final BigDecimal DEFAULT_EXTERNAL_FEE = new BigDecimal("400.00");
    public static final BigDecimal DEFAULT_FREE_DELIVERY_THRESHOLD = new BigDecimal("20000.00");

    public static final String AREA_KENITRA = "KENITRA_AREA";
    public static final String AREA_CASABLANCA = "CASABLANCA";
    public static final String AREA_OTHER = "OTHER";
    public static final String AREA_PICKUP = "PICKUP";

    public static final String WAIVED_ADMIN_DISABLED = "ADMIN_DISABLED";
    public static final String WAIVED_FREE_THRESHOLD = "FREE_THRESHOLD";
    public static final String WAIVED_PICKUP = "PICKUP";

    private boolean feesEnabled = true;
    private BigDecimal freeDeliveryThreshold = DEFAULT_FREE_DELIVERY_THRESHOLD;
    private Map<String, BigDecimal> fees = new LinkedHashMap<>();
    private List<String> kenitraSurroundingAreas = new ArrayList<>(List.of(
            "kenitra",
            "mehdia",
            "mehdya",
            "sidi taibi",
            "sidi taybi",
            "fouarat",
            "haddada",
            "amer seflia",
            "mnasra",
            "mamora",
            "al maamoura",
            "bir rami",
            "saknia",
            "oulad oujih"
    ));

    public DeliveryFeeProperties() {
        this.fees.put("Kenitra", DEFAULT_KENITRA_FEE);
        this.fees.put("Casablanca", DEFAULT_CASABLANCA_FEE);
        this.fees.put("External", DEFAULT_EXTERNAL_FEE);
    }

    public boolean isFeesEnabled() {
        return feesEnabled;
    }

    public void setFeesEnabled(boolean feesEnabled) {
        this.feesEnabled = feesEnabled;
    }

    public BigDecimal getFreeDeliveryThreshold() {
        return freeDeliveryThreshold;
    }

    public void setFreeDeliveryThreshold(BigDecimal freeDeliveryThreshold) {
        if (freeDeliveryThreshold != null && freeDeliveryThreshold.signum() >= 0) {
            this.freeDeliveryThreshold = freeDeliveryThreshold.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public Map<String, BigDecimal> getFees() {
        return fees;
    }

    public void setFees(Map<String, BigDecimal> fees) {
        this.fees = fees == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fees);
    }

    public List<String> getKenitraSurroundingAreas() {
        return kenitraSurroundingAreas;
    }

    public void setKenitraSurroundingAreas(List<String> kenitraSurroundingAreas) {
        this.kenitraSurroundingAreas = kenitraSurroundingAreas == null
                ? new ArrayList<>()
                : new ArrayList<>(kenitraSurroundingAreas);
    }

    public String resolveArea(String city) {
        String normalizedCity = normalize(city);
        if (normalizedCity.isEmpty()) {
            return AREA_OTHER;
        }
        if (isKenitraArea(normalizedCity)) {
            return AREA_KENITRA;
        }
        if (isCasablancaArea(normalizedCity)) {
            return AREA_CASABLANCA;
        }
        return AREA_OTHER;
    }

    public BigDecimal feeForCity(String city) {
        String normalizedCity = normalize(city);
        if (normalizedCity.isEmpty()) {
            return null;
        }
        if (!feesEnabled) {
            return BigDecimal.ZERO.setScale(2);
        }
        for (Map.Entry<String, BigDecimal> entry : fees.entrySet()) {
            if (normalize(entry.getKey()).equals(normalizedCity) && isConfigured(entry.getValue())) {
                return entry.getValue().setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (isKenitraArea(normalizedCity)) {
            BigDecimal kenitraFee = findConfiguredFee("kenitra");
            if (kenitraFee != null) {
                return kenitraFee;
            }
        }
        if (isCasablancaArea(normalizedCity)) {
            BigDecimal casablancaFee = findConfiguredFee("casablanca");
            if (casablancaFee != null) {
                return casablancaFee;
            }
        }
        BigDecimal externalFee = findConfiguredFee("external");
        if (externalFee != null) {
            return externalFee;
        }
        return findConfiguredFee("other");
    }

    public DeliveryCalculation calculateForDelivery(String city, BigDecimal authoritativeSubtotal) {
        String area = resolveArea(city);
        BigDecimal baseFee = feeForCityWhenEnabled(city);
        if (baseFee == null) {
            return null;
        }
        if (!feesEnabled) {
            return new DeliveryCalculation(
                    BigDecimal.ZERO.setScale(2),
                    area,
                    WAIVED_ADMIN_DISABLED,
                    false,
                    freeDeliveryThreshold);
        }
        if (authoritativeSubtotal != null
                && freeDeliveryThreshold != null
                && authoritativeSubtotal.compareTo(freeDeliveryThreshold) > 0) {
            return new DeliveryCalculation(
                    BigDecimal.ZERO.setScale(2),
                    area,
                    WAIVED_FREE_THRESHOLD,
                    true,
                    freeDeliveryThreshold);
        }
        return new DeliveryCalculation(
                baseFee.setScale(2, RoundingMode.HALF_UP),
                area,
                null,
                true,
                freeDeliveryThreshold);
    }

    private BigDecimal feeForCityWhenEnabled(String city) {
        String normalizedCity = normalize(city);
        if (normalizedCity.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, BigDecimal> entry : fees.entrySet()) {
            if (normalize(entry.getKey()).equals(normalizedCity) && isConfigured(entry.getValue())) {
                return entry.getValue().setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (isKenitraArea(normalizedCity)) {
            BigDecimal kenitraFee = findConfiguredFee("kenitra");
            if (kenitraFee != null) {
                return kenitraFee;
            }
        }
        if (isCasablancaArea(normalizedCity)) {
            BigDecimal casablancaFee = findConfiguredFee("casablanca");
            if (casablancaFee != null) {
                return casablancaFee;
            }
        }
        BigDecimal externalFee = findConfiguredFee("external");
        if (externalFee != null) {
            return externalFee;
        }
        return findConfiguredFee("other");
    }

    private BigDecimal findConfiguredFee(String normalizedKey) {
        for (Map.Entry<String, BigDecimal> entry : fees.entrySet()) {
            if ( normalizedKey.equals(normalize(entry.getKey())) && isConfigured(entry.getValue())) {
                return entry.getValue().setScale(2, RoundingMode.HALF_UP);
            }
        }
        return null;
    }

    private boolean isKenitraArea(String normalizedCity) {
        if (normalizedCity.equals("kenitra") || normalizedCity.contains("kenitra")) {
            return true;
        }
        for (String locality : kenitraSurroundingAreas) {
            String normalizedLocality = normalize(locality);
            if (!normalizedLocality.isEmpty() && normalizedCity.equals(normalizedLocality)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCasablancaArea(String normalizedCity) {
        return normalizedCity.equals("casablanca")
                || normalizedCity.contains("casablanca")
                || normalizedCity.equals("dar el beida")
                || normalizedCity.equals("dar elbeida");
    }

    private boolean isConfigured(BigDecimal fee) {
        return fee != null && fee.signum() >= 0;
    }

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[-_'/]+", " ")
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    public record DeliveryCalculation(
            BigDecimal fee,
            String area,
            String waivedReason,
            boolean feesEnabled,
            BigDecimal freeDeliveryThreshold) {
    }
}

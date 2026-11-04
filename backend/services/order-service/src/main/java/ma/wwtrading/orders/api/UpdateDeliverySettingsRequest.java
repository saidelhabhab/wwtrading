package ma.wwtrading.orders.api;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliverySettingsRequest(
        @NotNull Boolean feesEnabled,
        @DecimalMin("0.00") BigDecimal kenitraAreaFee,
        @DecimalMin("0.00") BigDecimal casablancaFee,
        @DecimalMin("0.00") BigDecimal otherAreasFee,
        @DecimalMin("0.00") BigDecimal freeDeliveryThreshold) {

    public UpdateDeliverySettingsRequest(Boolean feesEnabled) {
        this(feesEnabled, null, null, null, null);
    }
}


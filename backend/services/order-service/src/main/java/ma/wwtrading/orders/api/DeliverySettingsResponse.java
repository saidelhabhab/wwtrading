package ma.wwtrading.orders.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DeliverySettingsResponse(
        boolean feesEnabled,
        BigDecimal kenitraAreaFee,
        BigDecimal casablancaFee,
        BigDecimal otherAreasFee,
        BigDecimal freeDeliveryThreshold,
        List<String> kenitraSurroundingAreas,
        Instant updatedAt) {
}


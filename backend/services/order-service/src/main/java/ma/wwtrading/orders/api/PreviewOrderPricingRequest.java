package ma.wwtrading.orders.api;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import ma.wwtrading.orders.domain.DeliveryMethod;

public record PreviewOrderPricingRequest(
        UUID addressId,
        @NotNull DeliveryMethod deliveryMethod,
        UUID quotationId) {
    public PreviewOrderPricingRequest(UUID addressId, DeliveryMethod deliveryMethod) {
        this(addressId, deliveryMethod, null);
    }
}

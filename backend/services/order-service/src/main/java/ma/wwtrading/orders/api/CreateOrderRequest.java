package ma.wwtrading.orders.api;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.PaymentMethod;

public record CreateOrderRequest(
    UUID addressId,
    @NotNull DeliveryMethod deliveryMethod,
    @NotNull PaymentMethod paymentMethod,
    String bankReference,
    UUID quotationId,
    @Size(max = 128) String idempotencyKey
) {
    public CreateOrderRequest(UUID addressId, DeliveryMethod deliveryMethod, PaymentMethod paymentMethod) {
        this(addressId, deliveryMethod, paymentMethod, null, null, null);
    }

    public CreateOrderRequest(UUID addressId, DeliveryMethod deliveryMethod, PaymentMethod paymentMethod,
            String bankReference) {
        this(addressId, deliveryMethod, paymentMethod, bankReference, null, null);
    }

    public CreateOrderRequest(UUID addressId, DeliveryMethod deliveryMethod, PaymentMethod paymentMethod,
            String bankReference, UUID quotationId) {
        this(addressId, deliveryMethod, paymentMethod, bankReference, quotationId, null);
    }
}

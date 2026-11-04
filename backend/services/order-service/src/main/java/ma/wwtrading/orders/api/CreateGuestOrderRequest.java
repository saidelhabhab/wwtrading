package ma.wwtrading.orders.api;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.PaymentMethod;

public record CreateGuestOrderRequest(
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 32) String phone,
    @Size(max = 200) String company,
    @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2,
    @Size(max = 120) String city,
    @Size(max = 32) String postalCode,
    @Size(max = 100) String country,
    @NotNull DeliveryMethod deliveryMethod,
    @NotNull PaymentMethod paymentMethod,
    @Size(max = 100) String bankReference,
    @NotEmpty @Valid List<GuestOrderItemRequest> items,
    @Size(max = 128) String idempotencyKey
) {
    public CreateGuestOrderRequest(
            String fullName,
            String email,
            String phone,
            String addressLine1,
            String city,
            String postalCode,
            DeliveryMethod deliveryMethod,
            PaymentMethod paymentMethod,
            String bankReference,
            List<GuestOrderItemRequest> items) {
        this(fullName, email, phone, null, addressLine1, null, city, postalCode, "Maroc",
                deliveryMethod, paymentMethod, bankReference, items, null);
    }
}


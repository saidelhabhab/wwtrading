package ma.wwtrading.orders.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;
import ma.wwtrading.orders.api.CreateOrderRequest;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.domain.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class OrderMapperValidationTests {

    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void mapsExternalCustomerReferenceAndAssignsServerManagedDraftStatus() {
        var request = new CreateOrderRequest(UUID.randomUUID(), DeliveryMethod.PICKUP, PaymentMethod.CASH_ON_DELIVERY);
        var order = mapper.toEntity("customer-123", null, request, List.of(), BigDecimal.ZERO);
        var response = mapper.toResponse(order);

        assertThat(response.customerReference()).isEqualTo("customer-123");
        assertThat(response.status()).isEqualTo(OrderStatus.DRAFT);
    }

    @Test
    void validatesRequiredOrderMetadata() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new CreateOrderRequest(null, null, null))).isNotEmpty();
            assertThat(validator.validate(new CreateOrderRequest(UUID.randomUUID(), DeliveryMethod.DELIVERY, PaymentMethod.CASH_ON_DELIVERY))).isEmpty();
        }
    }

    @Test
    void requestDtoExcludesEveryServerManagedField() {
        assertThat(CreateOrderRequest.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .containsExactly("addressId", "deliveryMethod", "paymentMethod", "bankReference", "quotationId", "idempotencyKey")
            .doesNotContain("customerReference", "id", "status", "subtotal", "deliveryFee", "total",
                    "unitPrice", "createdAt", "updatedAt");
    }
}

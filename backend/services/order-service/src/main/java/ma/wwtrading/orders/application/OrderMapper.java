package ma.wwtrading.orders.application;

import ma.wwtrading.orders.api.CreateOrderRequest;
import ma.wwtrading.orders.api.OrderResponse;
import ma.wwtrading.orders.api.ShippingAddressSnapshot;
import ma.wwtrading.orders.domain.OrderEntity;
import ma.wwtrading.orders.domain.OrderItemEntity;
import ma.wwtrading.orders.domain.OrderStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {

    @Mapping(target = "trackingToken", ignore = true)
    @Mapping(target = "trackingUrl", ignore = true)
    OrderResponse toResponse(OrderEntity order);

    OrderResponse.OrderItemResponse toItemResponse(OrderItemEntity item);

    default OrderEntity toEntity(String customerReference, ShippingAddressSnapshot shippingAddress,
                                 CreateOrderRequest request, java.util.List<OrderItemEntity> items,
                                 java.math.BigDecimal deliveryFee) {
        return toEntity(customerReference, shippingAddress, request, items, deliveryFee, null, null);
    }

    default OrderEntity toEntity(String customerReference, ShippingAddressSnapshot shippingAddress,
                                 CreateOrderRequest request, java.util.List<OrderItemEntity> items,
                                 java.math.BigDecimal deliveryFee, String deliveryArea,
                                 String deliveryFeeWaivedReason) {
        OrderEntity order = new OrderEntity(
            customerReference,
            OrderStatus.DRAFT,
            shippingAddress,
            request.deliveryMethod(),
            request.paymentMethod(),
            items,
            deliveryFee,
            null,
            java.math.BigDecimal.ZERO.setScale(2),
            deliveryArea,
            deliveryFeeWaivedReason
        );
        if (order.requiresBankTransfer() && request.bankReference() != null && !request.bankReference().isBlank()) {
            order.selectBank(request.bankReference().trim());
        }
        return order;
    }
}

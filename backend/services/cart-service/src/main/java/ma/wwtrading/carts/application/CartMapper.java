package ma.wwtrading.carts.application;

import ma.wwtrading.carts.api.AddCartItemRequest;
import ma.wwtrading.carts.api.CartItemResponse;
import ma.wwtrading.carts.api.CartResponse;
import ma.wwtrading.carts.domain.CartEntity;
import ma.wwtrading.carts.domain.CartItemEntity;
import org.mapstruct.Mapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CartMapper {

    CartResponse toResponse(CartEntity cart);

    @Mapping(target = "availableQuantity", ignore = true)
    CartItemResponse toResponse(CartItemEntity item);

    default CartItemEntity toEntity(AddCartItemRequest request, CartEntity cart) {
        return new CartItemEntity(cart, request.productReference(), request.quantity());
    }
}

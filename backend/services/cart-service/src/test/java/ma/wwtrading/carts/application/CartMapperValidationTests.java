package ma.wwtrading.carts.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import ma.wwtrading.carts.api.AddCartItemRequest;
import ma.wwtrading.carts.api.UpdateCartItemQuantityRequest;
import ma.wwtrading.carts.domain.CartEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CartMapperValidationTests {

    private final CartMapper mapper = Mappers.getMapper(CartMapper.class);

    @Test
    void mapsOnlyExternalProductReferenceAndQuantityIntoOwnedCartItem() {
        var cart = new CartEntity("customer-123");
        var item = mapper.toEntity(new AddCartItemRequest("product-456", 2), cart);
        var response = mapper.toResponse(item);

        assertThat(item.getCart()).isSameAs(cart);
        assertThat(response.productReference()).isEqualTo("product-456");
        assertThat(response.quantity()).isEqualTo(2);
    }

    @Test
    void validatesProductReferenceAndStrictlyPositiveQuantities() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new AddCartItemRequest(" ", 1))).isNotEmpty();
            assertThat(validator.validate(new AddCartItemRequest("x".repeat(65), 1))).isNotEmpty();
            assertThat(validator.validate(new AddCartItemRequest("product-456", 0))).isNotEmpty();
            assertThat(validator.validate(new UpdateCartItemQuantityRequest(-1))).isNotEmpty();
            assertThat(validator.validate(new AddCartItemRequest("product-456", 2))).isEmpty();
            assertThat(validator.validate(new UpdateCartItemQuantityRequest(3))).isEmpty();
        }
    }

    @Test
    void requestDtosExcludeOwnershipAndServerManagedFields() {
        assertThat(AddCartItemRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("productReference", "quantity")
            .doesNotContain("cartId", "cartItemId", "customerReference", "createdAt", "updatedAt");

        assertThat(UpdateCartItemQuantityRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("quantity");
    }
}

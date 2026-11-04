package ma.wwtrading.wishlists.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import ma.wwtrading.wishlists.api.AddWishlistItemRequest;
import ma.wwtrading.wishlists.domain.WishlistEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class WishlistMapperValidationTests {

    private final WishlistMapper mapper = Mappers.getMapper(WishlistMapper.class);

    @Test
    void mapsOnlyExternalProductReferenceIntoOwnedWishlistItem() {
        var wishlist = new WishlistEntity("customer-123");
        var item = mapper.toEntity(new AddWishlistItemRequest("product-456"), wishlist);
        var response = mapper.toResponse(item);

        assertThat(item.getWishlist()).isSameAs(wishlist);
        assertThat(wishlist.getItems()).containsExactly(item);
        assertThat(response.productReference()).isEqualTo("product-456");
    }

    @Test
    void validatesProductReference() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new AddWishlistItemRequest(" "))).isNotEmpty();
            assertThat(validator.validate(new AddWishlistItemRequest("x".repeat(65)))).isNotEmpty();
            assertThat(validator.validate(new AddWishlistItemRequest("product-456"))).isEmpty();
        }
    }

    @Test
    void requestDtoExcludesOwnershipAndServerManagedFields() {
        assertThat(AddWishlistItemRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("productReference")
            .doesNotContain(
                "wishlistId",
                "wishlistItemId",
                "customerReference",
                "createdAt",
                "updatedAt"
            );
    }
}

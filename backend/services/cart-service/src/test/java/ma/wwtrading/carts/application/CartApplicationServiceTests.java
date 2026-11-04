package ma.wwtrading.carts.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.carts.api.AddCartItemRequest;
import ma.wwtrading.carts.api.UpdateCartItemQuantityRequest;
import ma.wwtrading.carts.domain.CartEntity;
import ma.wwtrading.carts.domain.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CartApplicationServiceTests {

    private final CartRepository repository = mock(CartRepository.class);
    private final CartApplicationService service = new CartApplicationService(
        repository, Mappers.getMapper(CartMapper.class)
    );

    @BeforeEach
    void saveReturnsEntity() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getCreatesCartForExternalCustomerWhenMissing() {
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.empty());

        service.getOrCreate("customer-1");

        verify(repository).save(any(CartEntity.class));
    }

    @Test
    void addingExistingProductMergesQuantityWithoutDuplicate() {
        CartEntity cart = new CartEntity("customer-1");
        cart.addOrMergeItem("product-1", 2);
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.of(cart));

        service.addItem("customer-1", new AddCartItemRequest("product-1", 3));

        assertThat(cart.getItems()).hasSize(1);
        assertThat(cart.getItems().getFirst().getQuantity()).isEqualTo(5);
    }

    @Test
    void updateAndRemoveAreLimitedToOwnedCart() {
        CartEntity ownedCart = new CartEntity("customer-1");
        var item = ownedCart.addOrMergeItem("product-1", 1);
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.of(ownedCart));
        when(repository.findByCustomerReference("customer-2")).thenReturn(Optional.empty());

        service.updateQuantity("customer-1", item.getId(), new UpdateCartItemQuantityRequest(4));
        assertThat(item.getQuantity()).isEqualTo(4);

        assertThatThrownBy(() -> service.removeItem("customer-2", item.getId()))
            .isInstanceOf(CartItemNotFoundException.class);
        assertThat(ownedCart.getItems()).hasSize(1);
    }

    @Test
    void clearOnlyEmptiesRequestedCustomersCart() {
        CartEntity cart = new CartEntity("customer-1");
        cart.addOrMergeItem("product-1", 1);
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.of(cart));

        service.clear("customer-1");

        assertThat(cart.getItems()).isEmpty();
        verify(repository).save(cart);
    }
}

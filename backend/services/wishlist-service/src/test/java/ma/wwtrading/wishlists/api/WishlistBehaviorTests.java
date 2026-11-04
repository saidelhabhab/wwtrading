package ma.wwtrading.wishlists.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.wishlists.application.WishlistApplicationService;
import ma.wwtrading.wishlists.application.WishlistItemNotFoundException;
import ma.wwtrading.wishlists.application.WishlistMapper;
import ma.wwtrading.wishlists.application.WishlistNotFoundException;
import ma.wwtrading.wishlists.application.WishlistProductAlreadyExistsException;
import ma.wwtrading.wishlists.domain.WishlistEntity;
import ma.wwtrading.wishlists.domain.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WishlistBehaviorTests {

    @Test
    void getCreatesWishlistForExternalCustomerWhenMissing() {
        WishlistRepository repository = mock(WishlistRepository.class);
        WishlistApplicationService service = new WishlistApplicationService(repository, Mappers.getMapper(WishlistMapper.class));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.empty());

        service.getOrCreate("customer-1");

        verify(repository).save(any(WishlistEntity.class));
    }

    @Test
    void addingDuplicateProductThrowsConflict() {
        WishlistRepository repository = mock(WishlistRepository.class);
        WishlistApplicationService service = new WishlistApplicationService(repository, Mappers.getMapper(WishlistMapper.class));
        WishlistEntity wishlist = new WishlistEntity("customer-1");
        wishlist.addItem("product-1");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.of(wishlist));

        assertThatThrownBy(() -> service.addItem("customer-1", new AddWishlistItemRequest("product-1")))
            .isInstanceOf(WishlistProductAlreadyExistsException.class);
        assertThat(wishlist.getItems()).hasSize(1);
    }

    @Test
    void removeAndClearAreLimitedToOwnedWishlist() {
        WishlistRepository repository = mock(WishlistRepository.class);
        WishlistApplicationService service = new WishlistApplicationService(repository, Mappers.getMapper(WishlistMapper.class));
        WishlistEntity ownedWishlist = new WishlistEntity("customer-1");
        var item = ownedWishlist.addItem("product-1");
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.of(ownedWishlist));
        when(repository.findByCustomerReference("customer-2")).thenReturn(Optional.empty());

        service.removeItem("customer-1", item.getId());
        assertThat(ownedWishlist.getItems()).isEmpty();

        assertThatThrownBy(() -> service.removeItem("customer-2", UUID.randomUUID()))
            .isInstanceOf(WishlistItemNotFoundException.class);
    }

    @Test
    void getCurrentThrowsNotFoundForMissingWishlist() {
        WishlistRepository repository = mock(WishlistRepository.class);
        WishlistApplicationService service = new WishlistApplicationService(repository, Mappers.getMapper(WishlistMapper.class));
        when(repository.findByCustomerReference("customer-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent("customer-1"))
            .isInstanceOf(WishlistNotFoundException.class);
    }

    @Test
    void controllerUsesPrincipalIdentityAndRejectsInvalidInput() throws Exception {
        WishlistApplicationService service = mock(WishlistApplicationService.class);
        Principal principal = () -> "customer-1";
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WishlistController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

        when(service.getOrCreate("customer-1")).thenReturn(new WishlistResponse(UUID.randomUUID(), List.of(), null, null));
        mvc.perform(get("/api/wishlist").principal(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
        verify(service).getOrCreate("customer-1");

        when(service.addItem(eq("customer-1"), any()))
            .thenReturn(new WishlistResponse(UUID.randomUUID(), List.of(), null, null));
        mvc.perform(post("/api/wishlist/items")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productReference\":\"product-1\",\"customerReference\":\"attacker\"}"))
            .andExpect(status().isCreated());
        verify(service).addItem(eq("customer-1"), any());

        mvc.perform(post("/api/wishlist/items")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productReference\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void controllerHandlesConflictAndNotFoundSafely() throws Exception {
        WishlistApplicationService service = mock(WishlistApplicationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WishlistController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        Principal principal = () -> "customer-1";
        UUID itemId = UUID.randomUUID();

        when(service.addItem(eq("customer-1"), any()))
            .thenThrow(new WishlistProductAlreadyExistsException("product-1"));
        mvc.perform(post("/api/wishlist/items")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productReference\":\"product-1\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Product already in wishlist"));

        doThrow(new WishlistItemNotFoundException(itemId)).when(service).removeItem("customer-1", itemId);
        mvc.perform(delete("/api/wishlist/items/{itemId}", itemId).principal(principal))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Wishlist item not found"))
            .andExpect(jsonPath("$.detail").value("The wishlist item does not exist"));

        mvc.perform(delete("/api/wishlist/items").principal(principal))
            .andExpect(status().isNoContent());
        verify(service).clear("customer-1");

        mvc.perform(get("/api/wishlist"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Authentication required"));
    }
}

package ma.wwtrading.carts.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.carts.application.CartApplicationService;
import ma.wwtrading.carts.application.CartItemNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CartControllerTests {

    private final CartApplicationService service = mock(CartApplicationService.class);
    private final Principal principal = () -> "customer-1";
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CartController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void getsCartUsingAuthenticatedIdentity() throws Exception {
        when(service.getOrCreate("customer-1"))
            .thenReturn(new CartResponse(UUID.randomUUID(), List.of(), null, null));

        mvc.perform(get("/api/cart").principal(principal))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());

        verify(service).getOrCreate("customer-1");
    }

    @Test
    void addIgnoresAttemptedOwnershipAndUsesPrincipal() throws Exception {
        when(service.addItem(eq("customer-1"), any()))
            .thenReturn(new CartResponse(UUID.randomUUID(), List.of(), null, null));

        mvc.perform(post("/api/cart/items")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productReference":"product-1","quantity":2,"customerReference":"attacker"}
                    """))
            .andExpect(status().isCreated());

        verify(service).addItem(eq("customer-1"), any());
    }

    @Test
    void rejectsInvalidQuantityBeforeApplicationLayer() throws Exception {
        mvc.perform(post("/api/cart/items")
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productReference\":\"product-1\",\"quantity\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"));

        verifyNoInteractions(service);
    }

    @Test
    void updateReturnsSafeNotFoundProblem() throws Exception {
        UUID itemId = UUID.randomUUID();
        when(service.updateQuantity(eq("customer-1"), eq(itemId), any()))
            .thenThrow(new CartItemNotFoundException(itemId));

        mvc.perform(put("/api/cart/items/{itemId}", itemId)
                .principal(principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Cart item not found"))
            .andExpect(jsonPath("$.detail").value("The cart item does not exist"));
    }

    @Test
    void removeAndClearDelegateWithAuthenticatedIdentity() throws Exception {
        UUID itemId = UUID.randomUUID();

        mvc.perform(delete("/api/cart/items/{itemId}", itemId).principal(principal))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/api/cart/items").principal(principal))
            .andExpect(status().isNoContent());

        verify(service).removeItem("customer-1", itemId);
        verify(service).clear("customer-1");
    }

    @Test
    void rejectsMissingAuthenticationContext() throws Exception {
        mvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Authentication required"));

        verifyNoInteractions(service);
    }
}

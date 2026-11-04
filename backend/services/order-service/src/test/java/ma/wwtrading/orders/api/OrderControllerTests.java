package ma.wwtrading.orders.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.orders.application.OrderApplicationService;
import ma.wwtrading.orders.application.OrderNotFoundException;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.domain.PaymentMethod;
import ma.wwtrading.orders.domain.PaymentStatus;
import ma.wwtrading.orders.infrastructure.PaymentBankLogoStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerTests {

    private final OrderApplicationService service = mock(OrderApplicationService.class);
    private final PaymentBankLogoStorage logoStorage = mock(PaymentBankLogoStorage.class);
    private MockMvc mvc;

    private JwtAuthenticationToken jwtAuthenticationToken() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claims(claims -> claims.put("sub", "customer-1"))
            .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, logoStorage))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void createsOrderAndReturnsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(JwtAuthenticationToken.class), any(CreateOrderRequest.class))).thenReturn(new OrderResponse(
            id,
            "customer-1",
            null,
            DeliveryMethod.DELIVERY,
            PaymentMethod.BANK_TRANSFER,
            PaymentStatus.UNPAID,
            List.of(),
            new BigDecimal("120.00"),
            new BigDecimal("20.00"),
            OrderStatus.DRAFT,
            Instant.now(),
            Instant.now()
        ));

        mvc.perform(post("/api/orders")
                .principal(jwtAuthenticationToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + UUID.randomUUID() + "\",\"deliveryMethod\":\"DELIVERY\",\"paymentMethod\":\"BANK_TRANSFER\",\"bankReference\":\"bank-a\",\"subtotal\":1,\"deliveryFee\":0,\"total\":1,\"unitPrice\":1}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/orders/" + id))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.subtotal").value(100.0))
            .andExpect(jsonPath("$.deliveryFee").value(20.0))
            .andExpect(jsonPath("$.total").value(120.0));
    }

    @Test
    void previewsAuthoritativePricingWithoutCreatingOrder() throws Exception {
        UUID addressId = UUID.randomUUID();
        when(service.previewPricing(any(JwtAuthenticationToken.class), any(PreviewOrderPricingRequest.class)))
            .thenReturn(new OrderPricingResponse(new BigDecimal("1000.00"),
                new BigDecimal("10.00"), new BigDecimal("1010.00")));

        mvc.perform(post("/api/orders/pricing")
                .principal(jwtAuthenticationToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + addressId + "\",\"deliveryMethod\":\"DELIVERY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotal").value(1000.0))
            .andExpect(jsonPath("$.deliveryFee").value(10.0))
            .andExpect(jsonPath("$.total").value(1010.0));
        verifyNoInteractions(logoStorage);
    }

    @Test
    void rejectsInvalidCreateRequestBeforeApplicationLayer() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"));

        verifyNoInteractions(service);
    }

    @Test
    void returnsSafeNotFoundProblem() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getById(any(JwtAuthenticationToken.class), eq(id))).thenThrow(new OrderNotFoundException(id));

        mvc.perform(get("/api/orders/{id}", id).principal(jwtAuthenticationToken()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Order not found"));
    }

    @Test
    void enforcesRateLimitOnPublicGuestTrackingEndpoint() throws Exception {
        OrderController controller = new OrderController(service, logoStorage);
        controller.setRateLimiter(new ma.wwtrading.orders.security.GuestEndpointRateLimiter(2, 2));
        MockMvc rateLimitedMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

        when(service.trackOrderByToken("valid-token")).thenReturn(new PublicOrderTrackingResponse(
            "WWT-2026-0001",
            OrderStatus.DRAFT,
            PaymentStatus.UNPAID,
            PaymentMethod.BANK_TRANSFER,
            null,
            DeliveryMethod.PICKUP,
            "PICKUP",
            "PICKUP",
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            new BigDecimal("100.00"),
            "CIH Bank",
            "MA64000111",
            "WWTRADING",
            null,
            false,
            null,
            "Karim",
            Instant.now(),
            Instant.now(),
            List.of()
        ));

        rateLimitedMvc.perform(get("/api/orders/guest/track").param("token", "valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").value("WWT-2026-0001"));
        rateLimitedMvc.perform(get("/api/orders/guest/track").param("token", "valid-token"))
            .andExpect(status().isOk());
        rateLimitedMvc.perform(get("/api/orders/guest/track").param("token", "valid-token"))
            .andExpect(status().isTooManyRequests());
    }
}

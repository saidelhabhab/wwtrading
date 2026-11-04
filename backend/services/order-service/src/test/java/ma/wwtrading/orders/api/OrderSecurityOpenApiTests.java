package ma.wwtrading.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.orders.application.DeliverySettingsApplicationService;
import ma.wwtrading.orders.application.OrderApplicationService;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.domain.PaymentMethod;
import ma.wwtrading.orders.domain.PaymentStatus;
import ma.wwtrading.orders.infrastructure.PaymentBankLogoStorage;
import ma.wwtrading.orders.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

@WebMvcTest
@ContextConfiguration(classes = {
    OrderController.class,
    AdminDeliverySettingsController.class,
    ApiExceptionHandler.class,
    SecurityConfig.class,
    OpenApiConfig.class
})
@TestPropertySource(properties = {
    "wwtrading.security.issuer-uri=https://issuer.example.test/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=https://issuer.example.test/realms/wwtrading/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class OrderSecurityOpenApiTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderApplicationService service;

    @MockitoBean
    private PaymentBankLogoStorage logoStorage;

    @MockitoBean
    private DeliverySettingsApplicationService deliverySettingsService;

    @Test
    void requiresAuthenticationForOrderApi() throws Exception {
        mvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsClientToCreateAndReadOwnOrders() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(JwtAuthenticationToken.class), any(CreateOrderRequest.class))).thenReturn(new OrderResponse(
            id,
            "customer-1",
            null,
            DeliveryMethod.DELIVERY,
            PaymentMethod.BANK_TRANSFER,
            PaymentStatus.UNPAID,
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            OrderStatus.DRAFT,
            Instant.now(),
            Instant.now()
        ));
        when(service.getById(any(JwtAuthenticationToken.class), org.mockito.ArgumentMatchers.eq(id))).thenReturn(new OrderResponse(
            id,
            "customer-1",
            null,
            DeliveryMethod.DELIVERY,
            PaymentMethod.CASH_ON_DELIVERY,
            PaymentStatus.UNPAID,
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            OrderStatus.DRAFT,
            Instant.now(),
            Instant.now()
        ));

        mvc.perform(post("/api/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT")))
                .contentType("application/json")
                .content("{\"addressId\":\"" + UUID.randomUUID() + "\",\"deliveryMethod\":\"DELIVERY\",\"paymentMethod\":\"BANK_TRANSFER\",\"bankReference\":\"bank-a\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/api/orders/{id}", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isOk());
    }

    @Test
    void allowsCommercialRoleToListOrders() throws Exception {
        when(service.list(any())).thenReturn(Page.empty());

        mvc.perform(get("/api/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COMMERCIAL"))))
            .andExpect(status().isOk());
    }

    @Test
    void protectsPricingPreviewWithExistingOrderRoles() throws Exception {
        String body = "{\"deliveryMethod\":\"PICKUP\"}";
        mvc.perform(post("/api/orders/pricing").contentType("application/json").content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/pricing")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN")))
                .contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        when(service.previewPricing(any(JwtAuthenticationToken.class), any(PreviewOrderPricingRequest.class)))
            .thenReturn(new OrderPricingResponse(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN));
        for (String role : List.of("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")) {
            mvc.perform(post("/api/orders/pricing")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType("application/json").content(body))
                .andExpect(status().isOk());
        }
    }

    @Test
    void allowsClientBankTransferCreationAndProofUpload() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(JwtAuthenticationToken.class), any(CreateOrderRequest.class)))
            .thenReturn(new OrderResponse(id, "customer@example.test", null, DeliveryMethod.PICKUP,
                PaymentMethod.BANK_TRANSFER, PaymentStatus.UNPAID, List.of(), BigDecimal.ZERO,
                BigDecimal.ZERO, OrderStatus.DRAFT, Instant.now(), Instant.now()));

        mvc.perform(post("/api/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT")))
                .contentType("application/json")
                .content("{\"deliveryMethod\":\"PICKUP\",\"paymentMethod\":\"BANK_TRANSFER\",\"bankReference\":\"bank-a\"}"))
            .andExpect(status().isCreated());

        var proof = new MockMultipartFile("file", "proof.png", "image/png", new byte[] {1, 2, 3});
        mvc.perform(multipart("/api/orders/{id}/transfer-proof", id)
                .file(proof)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedAndNonClientProofUploads() throws Exception {
        UUID id = UUID.randomUUID();
        var proof = new MockMultipartFile("file", "proof.png", "image/png", new byte[] {1});
        mvc.perform(multipart("/api/orders/{id}/transfer-proof", id).file(proof))
            .andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN")) {
            mvc.perform(multipart("/api/orders/{id}/transfer-proof", id).file(proof)
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void restrictsAdminDeliverySettingsToAdminRoleOnly() throws Exception {
        String payload = "{\"feesEnabled\":false}";
        mvc.perform(get("/api/admin/delivery-settings"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/delivery-settings").contentType("application/json").content(payload))
            .andExpect(status().isUnauthorized());

        for (String role : List.of("CLIENT", "COMMERCIAL", "MANAGER", "TECHNICIAN")) {
            mvc.perform(get("/api/admin/delivery-settings")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/admin/delivery-settings")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType("application/json")
                    .content(payload))
                .andExpect(status().isForbidden());
            mvc.perform(patch("/api/admin/delivery-settings")
                    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType("application/json")
                    .content(payload))
                .andExpect(status().isForbidden());
        }

        when(deliverySettingsService.getSettings()).thenReturn(new DeliverySettingsResponse(
            true, new BigDecimal("300.00"), new BigDecimal("300.00"),
            new BigDecimal("400.00"), new BigDecimal("20000.00"), List.of("kenitra"), Instant.now()
        ));
        when(deliverySettingsService.updateSettings(any(UpdateDeliverySettingsRequest.class))).thenReturn(new DeliverySettingsResponse(
            false, new BigDecimal("300.00"), new BigDecimal("300.00"),
            new BigDecimal("400.00"), new BigDecimal("20000.00"), List.of("kenitra"), Instant.now()
        ));

        mvc.perform(get("/api/admin/delivery-settings")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk());
        mvc.perform(put("/api/admin/delivery-settings")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk());
    }

    @Test
    void declaresBearerJwtOpenApiScheme() {
        var openApi = new OpenApiConfig().orderServiceOpenApi();

        var scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity()).isNotEmpty();
    }
}

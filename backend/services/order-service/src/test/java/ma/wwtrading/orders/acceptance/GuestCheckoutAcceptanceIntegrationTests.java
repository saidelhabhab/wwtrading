package ma.wwtrading.orders.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.orders.api.CreateGuestOrderRequest;
import ma.wwtrading.orders.api.GuestOrderItemRequest;
import ma.wwtrading.orders.api.OrderResponse;
import ma.wwtrading.orders.api.PreviewGuestOrderPricingRequest;
import ma.wwtrading.orders.api.PublicOrderTrackingResponse;
import ma.wwtrading.orders.application.CartClient;
import ma.wwtrading.orders.application.DeliveryFeeProperties;
import ma.wwtrading.orders.application.OrderApplicationService;
import ma.wwtrading.orders.application.OrderMapper;
import ma.wwtrading.orders.application.PaymentBankApplicationService;
import ma.wwtrading.orders.application.ProductPricingClient;
import ma.wwtrading.orders.application.TrackingTokenService;
import ma.wwtrading.orders.application.UserAddressClient;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.OrderEntity;
import ma.wwtrading.orders.domain.OrderRepository;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.domain.PaymentBankEntity;
import ma.wwtrading.orders.domain.PaymentBankRepository;
import ma.wwtrading.orders.domain.PaymentMethod;
import ma.wwtrading.orders.domain.PaymentPlan;
import ma.wwtrading.orders.domain.PaymentStatus;
import ma.wwtrading.orders.infrastructure.TransferProofStorage;
import ma.wwtrading.orders.security.GuestEndpointRateLimiter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:orders_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
public class GuestCheckoutAcceptanceIntegrationTests {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentBankRepository paymentBankRepository;

    private OrderApplicationService orderService;
    private TrackingTokenService trackingTokenService;
    private ProductPricingClient productPricingClient;
    private DeliveryFeeProperties deliveryFeeProperties;
    private PaymentBankEntity testBank;

    @BeforeEach
    void setUp() {
        trackingTokenService = new TrackingTokenService("http://localhost:4201", 30);
        productPricingClient = mock(ProductPricingClient.class);
        deliveryFeeProperties = new DeliveryFeeProperties();

        testBank = new PaymentBankEntity(
            "CIHBANK01",
            "CIH Bank",
            "MA640001111122223333444455",
            "WWTRADING SARL",
            "https://cdn.wwtrading.ma/logos/cih.png",
            true
        );
        paymentBankRepository.saveAndFlush(testBank);

        orderService = new OrderApplicationService(
            orderRepository,
            Mappers.getMapper(OrderMapper.class),
            mock(UserAddressClient.class),
            mock(CartClient.class),
            productPricingClient,
            deliveryFeeProperties,
            paymentBankRepository,
            mock(PaymentBankApplicationService.class),
            mock(TransferProofStorage.class),
            null
        );
        orderService.setTrackingTokenService(trackingTokenService);

        // Configure product pricing mock
        when(productPricingClient.getPublicPrice("PROD-SCANNER")).thenReturn(
            new ProductPricingClient.ProductPrice("PROD-SCANNER", "Scanner Médical HD", new BigDecimal("15000.00"), 10)
        );
        when(productPricingClient.getPublicPrice("PROD-GEL")).thenReturn(
            new ProductPricingClient.ProductPrice("PROD-GEL", "Gel Échographie 5L", new BigDecimal("200.00"), 50)
        );
        when(productPricingClient.getPublicPrice("PROD-HEAVY")).thenReturn(
            new ProductPricingClient.ProductPrice("PROD-HEAVY", "Appareil IRM Pro", new BigDecimal("25000.00"), 2)
        );
    }

    @Test
    @DisplayName("Scenario 1: Complete Guest Purchase (BANK_TRANSFER 100%)")
    void scenario1_executesFullGuestPurchaseWithoutAuthentication() {
        // 1. Preview authoritative pricing
        var pricingPreview = orderService.previewGuestPricing(new PreviewGuestOrderPricingRequest(
            List.of(new GuestOrderItemRequest("PROD-SCANNER", 1)),
            DeliveryMethod.DELIVERY,
            "Casablanca"
        ));

        assertThat(pricingPreview.subtotal()).isEqualByComparingTo("15000.00");
        assertThat(pricingPreview.deliveryFee()).isEqualByComparingTo("300.00"); // Casablanca = 300 DH
        assertThat(pricingPreview.total()).isEqualByComparingTo("15300.00");
        assertThat(pricingPreview.deliveryArea()).isEqualTo("CASABLANCA");
        assertThat(pricingPreview.deliveryFeeWaivedReason()).isNull();

        // 2. Submit guest order with Idempotency-Key
        String idempotencyKey = "guest-idem-" + UUID.randomUUID();
        var guestRequest = new CreateGuestOrderRequest(
            "Youssef Amrani",
            "youssef.amrani@example.ma",
            "0661998877",
            "Cabinet Médical Amrani",
            "45 Boulevard d'Anfa",
            "3ème étage",
            "Casablanca",
            "20000",
            "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.BANK_TRANSFER,
            "CIHBANK01",
            List.of(new GuestOrderItemRequest("PROD-SCANNER", 1)),
            idempotencyKey
        );

        OrderResponse order = orderService.createGuestOrder(guestRequest, idempotencyKey);

        // 3. Verify order properties
        assertThat(order.guestOrder()).isTrue();
        assertThat(order.customerSubject()).isNull(); // No account created
        assertThat(order.orderNumber()).startsWith("WWT-");
        assertThat(order.customerReference()).isEqualTo("youssef.amrani@example.ma");
        assertThat(order.contactFullName()).isEqualTo("Youssef Amrani");
        assertThat(order.contactPhone()).isEqualTo("0661998877");
        assertThat(order.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(order.paymentPlan()).isEqualTo(PaymentPlan.FULL_BANK_TRANSFER);
        assertThat(order.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.subtotal()).isEqualByComparingTo("15000.00");
        assertThat(order.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(order.total()).isEqualByComparingTo("15300.00");
        assertThat(order.upfrontPaymentAmount()).isEqualByComparingTo("15300.00");
        assertThat(order.remainingDeliveryAmount()).isZero();
        assertThat(order.selectedBankReference()).isEqualTo("CIHBANK01");
        assertThat(order.paymentBankName()).isEqualTo("CIH Bank");
        assertThat(order.paymentBankRib()).isEqualTo("MA640001111122223333444455");

        // 4. Verify tracking token was generated and hashed
        assertThat(order.trackingToken()).isNotBlank();
        assertThat(order.trackingUrl()).contains("/suivi-commande?token=" + order.trackingToken());

        // 5. Verify database security: token is NOT plaintext in database
        OrderEntity persisted = orderRepository.findById(order.id()).orElseThrow();
        assertThat(persisted.getTrackingTokenHash()).isNotEqualTo(order.trackingToken());
        assertThat(persisted.getTrackingTokenHash()).isEqualTo(trackingTokenService.hashToken(order.trackingToken()));
        assertThat(persisted.isTrackingTokenRevoked()).isFalse();
        assertThat(persisted.getTrackingTokenExpiresAt()).isAfter(Instant.now());

        // 6. Verify idempotency: repeated request returns the same order without creating duplicate
        OrderResponse duplicateOrder = orderService.createGuestOrder(guestRequest, idempotencyKey);
        assertThat(duplicateOrder.id()).isEqualTo(order.id());
        assertThat(duplicateOrder.orderNumber()).isEqualTo(order.orderNumber());
    }

    @Test
    @DisplayName("Scenario 2a: Split Payment 50/50 for Delivery in other cities (Tanger)")
    void scenario2a_split5050CalculatesCorrectUpfrontAndRemainingAndAppliesOtherCitiesDeliveryFee() {
        var pricing = orderService.previewGuestPricing(new PreviewGuestOrderPricingRequest(
            List.of(new GuestOrderItemRequest("PROD-GEL", 5)),
            DeliveryMethod.DELIVERY,
            "Tanger"
        ));

        // Subtotal = 5 * 200 = 1000 DH; Tanger fee = 400 DH; Total = 1400 DH
        assertThat(pricing.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(pricing.deliveryFee()).isEqualByComparingTo("400.00");
        assertThat(pricing.total()).isEqualByComparingTo("1400.00");
        assertThat(pricing.upfrontPaymentAmount()).isEqualByComparingTo("700.00");
        assertThat(pricing.remainingDeliveryAmount()).isEqualByComparingTo("700.00");

        var splitOrder = orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Leila Mansouri",
            "leila@example.ma",
            "0677112233",
            null, "Avenue Mohammed VI", null, "Tanger", "90000", "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.SPLIT_50_50,
            "CIHBANK01",
            List.of(new GuestOrderItemRequest("PROD-GEL", 5)),
            null
        ), null);

        assertThat(splitOrder.paymentPlan()).isEqualTo(PaymentPlan.SPLIT_50_50);
        assertThat(splitOrder.upfrontPaymentAmount()).isEqualByComparingTo("700.00");
        assertThat(splitOrder.remainingDeliveryAmount()).isEqualByComparingTo("700.00");
        assertThat(splitOrder.deliveryFee()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Scenario 2b: Pickup order has zero delivery fee and full upfront payment")
    void scenario2b_pickupOrderHasZeroDeliveryFeeAndFullUpfrontPayment() {
        var pickupOrder = orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Tariq Alami",
            "tariq@example.ma",
            "0655443322",
            null, null, null, null, null, "Maroc",
            DeliveryMethod.PICKUP,
            PaymentMethod.BANK_TRANSFER,
            "CIHBANK01",
            List.of(new GuestOrderItemRequest("PROD-GEL", 2)),
            null
        ), null);

        assertThat(pickupOrder.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(pickupOrder.deliveryFee()).isZero();
        assertThat(pickupOrder.total()).isEqualByComparingTo("400.00");
        assertThat(pickupOrder.upfrontPaymentAmount()).isEqualByComparingTo("400.00");
        assertThat(pickupOrder.remainingDeliveryAmount()).isZero();
    }

    @Test
    @DisplayName("Scenario 2c: Business rules - supports SPLIT_50_50 and PAY_ON_PICKUP for PICKUP, rejects PAY_ON_PICKUP for DELIVERY, rejects CASH_ON_DELIVERY")
    void scenario2c_pickupAndDeliveryPaymentRules() {
        // Supports SPLIT_50_50 for PICKUP
        var pickupSplit = orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Tariq Alami", "tariq@example.ma", "0655443322",
            null, null, null, null, null, "Maroc",
            DeliveryMethod.PICKUP, PaymentMethod.SPLIT_50_50, "CIHBANK01",
            List.of(new GuestOrderItemRequest("PROD-GEL", 2)), null
        ), null);
        assertThat(pickupSplit.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(pickupSplit.paymentMethod()).isEqualTo(PaymentMethod.SPLIT_50_50);
        assertThat(pickupSplit.paymentPlan()).isEqualTo(PaymentPlan.SPLIT_50_50);
        assertThat(pickupSplit.deliveryFee()).isZero();
        assertThat(pickupSplit.total()).isEqualByComparingTo("400.00");
        assertThat(pickupSplit.upfrontPaymentAmount()).isEqualByComparingTo("200.00");
        assertThat(pickupSplit.remainingDeliveryAmount()).isEqualByComparingTo("200.00");

        // Supports PAY_ON_PICKUP for PICKUP
        var pickupPayOnSite = orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Tariq Alami", "tariq@example.ma", "0655443322",
            null, null, null, null, null, "Maroc",
            DeliveryMethod.PICKUP, PaymentMethod.PAY_ON_PICKUP, null,
            List.of(new GuestOrderItemRequest("PROD-GEL", 2)), null
        ), null);
        assertThat(pickupPayOnSite.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(pickupPayOnSite.paymentMethod()).isEqualTo(PaymentMethod.PAY_ON_PICKUP);
        assertThat(pickupPayOnSite.paymentPlan()).isEqualTo(PaymentPlan.PAY_ON_PICKUP);
        assertThat(pickupPayOnSite.deliveryFee()).isZero();
        assertThat(pickupPayOnSite.total()).isEqualByComparingTo("400.00");
        assertThat(pickupPayOnSite.upfrontPaymentAmount()).isZero();
        assertThat(pickupPayOnSite.remainingDeliveryAmount()).isEqualByComparingTo("400.00");

        // Rejects PAY_ON_PICKUP for DELIVERY
        assertThatThrownBy(() -> orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Tariq Alami", "tariq@example.ma", "0655443322",
            null, "Rue 1", null, "Casablanca", "20000", "Maroc",
            DeliveryMethod.DELIVERY, PaymentMethod.PAY_ON_PICKUP, null,
            List.of(new GuestOrderItemRequest("PROD-GEL", 2)), null
        ), null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // Rejects CASH_ON_DELIVERY for guest
        assertThatThrownBy(() -> orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Tariq Alami", "tariq@example.ma", "0655443322",
            null, "Rue 1", null, "Casablanca", "20000", "Maroc",
            DeliveryMethod.DELIVERY, PaymentMethod.CASH_ON_DELIVERY, null,
            List.of(new GuestOrderItemRequest("PROD-GEL", 2)), null
        ), null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Scenario 2d: Free delivery condition when subtotal > 20000 DH")
    void scenario2d_freeDeliveryWaivedAbove20000DH() {
        var pricing = orderService.previewGuestPricing(new PreviewGuestOrderPricingRequest(
            List.of(new GuestOrderItemRequest("PROD-HEAVY", 1)),
            DeliveryMethod.DELIVERY,
            "Marrakech"
        ));

        // Subtotal = 25000 > 20000 DH -> Free Delivery
        assertThat(pricing.subtotal()).isEqualByComparingTo("25000.00");
        assertThat(pricing.deliveryFee()).isZero();
        assertThat(pricing.total()).isEqualByComparingTo("25000.00");
        assertThat(pricing.deliveryFeeWaivedReason()).isEqualTo("FREE_THRESHOLD");
    }

    @Test
    @DisplayName("Scenario 3: Verify guest tracking, privacy sanitization, token revocation and rate limiting")
    void scenario3_trackingReturnsNonSensitiveSummaryAndRejectsInvalidOrRevokedTokens() {
        var order = orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Fatima Zahra",
            "fatima@example.ma",
            "0612345678",
            "Cabinet Zahra",
            "15 Rue Ibn Khaldoun",
            "Appt 4",
            "Kénitra",
            "14000",
            "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.BANK_TRANSFER,
            "CIHBANK01",
            List.of(new GuestOrderItemRequest("PROD-GEL", 1)),
            null
        ), null);

        String validRawToken = order.trackingToken();

        // 1. Valid token lookup returns sanitized public data
        PublicOrderTrackingResponse tracking = orderService.trackOrderByToken(validRawToken);
        assertThat(tracking.orderNumber()).isEqualTo(order.orderNumber());
        assertThat(tracking.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(tracking.recipientFirstName()).isEqualTo("Fatima");
        assertThat(tracking.shippingCity()).isEqualTo("Kénitra");
        assertThat(tracking.total()).isEqualByComparingTo("500.00"); // 200 + 300 Kenitra fee

        // 2. Sensitive street address, phone, and customer reference are NOT exposed
        // (PublicOrderTrackingResponse schema does not contain addressLine1, phone, or customerReference)

        // 3. Invalid token rejection -> 404
        assertThatThrownBy(() -> orderService.trackOrderByToken("invalid-random-token-12345678901234567890"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // 4. Revocation of token -> 404
        orderService.revokeGuestTrackingToken(order.id());
        assertThatThrownBy(() -> orderService.trackOrderByToken(validRawToken))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // 5. Regeneration generates new valid token and old token remains invalid
        String newTrackingUrl = orderService.regenerateGuestTrackingToken(order.id());
        assertThat(newTrackingUrl).isNotBlank();
        String newToken = newTrackingUrl.substring(newTrackingUrl.indexOf("token=") + 6);
        PublicOrderTrackingResponse newTracking = orderService.trackOrderByToken(newToken);
        assertThat(newTracking.orderNumber()).isEqualTo(order.orderNumber());

        // Old token is still rejected
        assertThatThrownBy(() -> orderService.trackOrderByToken(validRawToken))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // 6. Enforces rate limiter on tracking endpoint
        GuestEndpointRateLimiter rateLimiter = new GuestEndpointRateLimiter(2, 2);
        rateLimiter.checkTrackingAllowed("192.168.1.50");
        rateLimiter.checkTrackingAllowed("192.168.1.50");

        assertThatThrownBy(() -> rateLimiter.checkTrackingAllowed("192.168.1.50"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Scenario 5: Verified email claims guest order while unverified and cross-email are rejected")
    void scenario5_verifiedEmailClaimsGuestOrderWhileUnverifiedAndCrossEmailAreRejected() {
        // 1. Create a guest order for "doctor@hospital.ma"
        var guestOrder = orderService.createGuestOrder(new CreateGuestOrderRequest(
            "Dr. Alami",
            "doctor@hospital.ma",
            "0661000000",
            null, "Rue Principale", null, "Rabat", "10000", "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.BANK_TRANSFER,
            "CIHBANK01",
            List.of(new GuestOrderItemRequest("PROD-GEL", 1)),
            null
        ), null);

        // 2. Unverified email account claims -> 403 Forbidden
        Jwt unverifiedJwt = mock(Jwt.class);
        when(unverifiedJwt.getClaimAsString("email")).thenReturn("doctor@hospital.ma");
        when(unverifiedJwt.getClaimAsBoolean("email_verified")).thenReturn(false);
        when(unverifiedJwt.getSubject()).thenReturn("user-sub-unverified");
        JwtAuthenticationToken unverifiedAuth = mock(JwtAuthenticationToken.class);
        when(unverifiedAuth.getToken()).thenReturn(unverifiedJwt);

        assertThatThrownBy(() -> orderService.claimGuestOrders(unverifiedAuth))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        // 3. Different email verified account claims -> 0 orders claimed
        Jwt otherEmailJwt = mock(Jwt.class);
        when(otherEmailJwt.getClaimAsString("email")).thenReturn("intruder@hospital.ma");
        when(otherEmailJwt.getClaimAsBoolean("email_verified")).thenReturn(true);
        when(otherEmailJwt.getSubject()).thenReturn("user-sub-intruder");
        JwtAuthenticationToken otherAuth = mock(JwtAuthenticationToken.class);
        when(otherAuth.getToken()).thenReturn(otherEmailJwt);

        var otherClaim = orderService.claimGuestOrders(otherAuth);
        assertThat(otherClaim.linkedCount()).isZero();
        assertThat(otherClaim.linkedOrderNumbers()).isEmpty();

        // 4. Same email verified account claims -> 1 order linked
        Jwt verifiedJwt = mock(Jwt.class);
        when(verifiedJwt.getClaimAsString("email")).thenReturn("Doctor@Hospital.ma"); // Case-insensitive matching
        when(verifiedJwt.getClaimAsBoolean("email_verified")).thenReturn(true);
        when(verifiedJwt.getSubject()).thenReturn("user-sub-doctor");
        JwtAuthenticationToken verifiedAuth = mock(JwtAuthenticationToken.class);
        when(verifiedAuth.getToken()).thenReturn(verifiedJwt);

        var claimResult = orderService.claimGuestOrders(verifiedAuth);
        assertThat(claimResult.linkedCount()).isEqualTo(1);
        assertThat(claimResult.linkedOrderNumbers()).containsExactly(guestOrder.orderNumber());

        // 5. Order is now linked to user-sub-doctor in database
        OrderEntity updatedOrder = orderRepository.findById(guestOrder.id()).orElseThrow();
        assertThat(updatedOrder.getCustomerSubject()).isEqualTo("user-sub-doctor");
        assertThat(updatedOrder.getLinkedAt()).isNotNull();

        // 6. Repeated claim is idempotent (returns 0 newly linked)
        var repeatedClaim = orderService.claimGuestOrders(verifiedAuth);
        assertThat(repeatedClaim.linkedCount()).isZero();
    }
}

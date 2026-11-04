package ma.wwtrading.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.orders.api.CreateGuestOrderRequest;
import ma.wwtrading.orders.api.CreateOrderRequest;
import ma.wwtrading.orders.api.GuestOrderItemRequest;
import ma.wwtrading.orders.api.PreviewGuestOrderPricingRequest;
import ma.wwtrading.orders.api.PreviewOrderPricingRequest;
import ma.wwtrading.orders.api.UpdateDeliverySettingsRequest;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.DeliverySettingsEntity;
import ma.wwtrading.orders.domain.DeliverySettingsRepository;
import ma.wwtrading.orders.domain.OrderEntity;
import ma.wwtrading.orders.domain.OrderItemEntity;
import ma.wwtrading.orders.domain.OrderRepository;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.domain.PaymentBankEntity;
import ma.wwtrading.orders.domain.PaymentBankRepository;
import ma.wwtrading.orders.domain.PaymentMethod;
import ma.wwtrading.orders.domain.PaymentPlan;
import ma.wwtrading.orders.domain.PaymentStatus;
import ma.wwtrading.orders.infrastructure.TransferProofStorage;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class OrderApplicationServiceTests {

    private final OrderRepository repository = mock(OrderRepository.class);
    private final UserAddressClient userAddressClient = mock(UserAddressClient.class);
    private final CartClient cartClient = mock(CartClient.class);
    private final ProductPricingClient productPricingClient = mock(ProductPricingClient.class);
    private final DeliveryFeeProperties deliveryFeeProperties = new DeliveryFeeProperties();
    private final PaymentBankRepository paymentBankRepository = mock(PaymentBankRepository.class);
    private final PaymentBankApplicationService paymentBankApplicationService = mock(PaymentBankApplicationService.class);
    private final TransferProofStorage transferProofStorage = mock(TransferProofStorage.class);
    private final OrderApplicationService service = new OrderApplicationService(
        repository,
        Mappers.getMapper(OrderMapper.class),
        userAddressClient,
        cartClient,
        productPricingClient,
        deliveryFeeProperties,
        paymentBankRepository,
        paymentBankApplicationService,
        transferProofStorage,
        null
    );

    {
        when(paymentBankRepository.findByReferenceAndActiveTrue("bank-a")).thenReturn(Optional.of(
            new PaymentBankEntity("bank-a", "CIH Bank", "MA640001111122223333444455", "WWTRADING SARL", null, true)
        ));
    }

    @Test
    void resolvesExistingCityFeesAndConfiguredFreeDelivery() {
        deliveryFeeProperties.setFees(Map.of(
            "Kenitra", new BigDecimal("10.00"),
            "Rabat", new BigDecimal("15.00"),
            "Sale", new BigDecimal("15.00"),
            "External", new BigDecimal("20.00")));

        assertThat(deliveryFeeProperties.feeForCity("Kénitra")).isEqualByComparingTo("10.00");
        assertThat(deliveryFeeProperties.feeForCity("Rabat")).isEqualByComparingTo("15.00");
        assertThat(deliveryFeeProperties.feeForCity("Salé")).isEqualByComparingTo("15.00");
        assertThat(deliveryFeeProperties.feeForCity("Agadir")).isEqualByComparingTo("20.00");

        deliveryFeeProperties.setFees(Map.of("Kenitra", BigDecimal.ZERO));
        assertThat(deliveryFeeProperties.feeForCity("Kenitra")).isZero();
    }

    @Test
    void createsDraftOrderFromExternalCustomerReference() {
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        Jwt jwt = mock(Jwt.class);
        when(auth.getToken()).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn("customer@example.test");
        when(jwt.getSubject()).thenReturn("subject-123");

        UUID addressId = UUID.randomUUID();
        var request = new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a");
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId,
            "Jane",
            "Doe",
            null,
            "0600000000",
            "1 rue de la Paix",
            null,
            "Paris",
            "75000",
            "France"
        ));
        deliveryFeeProperties.setFees(Map.of("Paris", new BigDecimal("12.50")));
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 2)
        )));
        when(productPricingClient.getPrice(auth, "REF-001")).thenReturn(new ProductPricingClient.ProductPrice(
            "REF-001",
            new BigDecimal("10.00")
        ));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(auth, request);

        assertThat(response.customerReference()).isEqualTo("customer@example.test");
        assertThat(response.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(response.deliveryFee()).isEqualByComparingTo("12.50");
        assertThat(response.subtotal()).isEqualByComparingTo("20.00");
        assertThat(response.total()).isEqualByComparingTo("32.50");
        verify(cartClient).clearCart(auth);
    }

    @Test
    void createsPickupOrderWithoutAddress() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        var request = new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, "bank-a");
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-001")).thenReturn(new ProductPricingClient.ProductPrice(
            "REF-001", new BigDecimal("10.00"), 5
        ));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(auth, request);

        assertThat(response.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(response.deliveryFee()).isZero();
        assertThat(response.total()).isEqualByComparingTo("10.00");
        verifyNoInteractions(userAddressClient);
        verify(cartClient).clearCart(auth);
    }

    @Test
    void previewsServerPricesWithoutCreatingOrClearingAnOrder() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", null, null, "1 rue", null, "Kenitra", null, "Morocco"));
        deliveryFeeProperties.setFees(Map.of("Kenitra", new BigDecimal("10.00")));
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 2))));
        when(productPricingClient.getPrice(auth, "REF-001"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-001", new BigDecimal("500.00"), 10));

        var pricing = service.previewPricing(auth,
            new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));

        assertThat(pricing.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(pricing.deliveryFee()).isEqualByComparingTo("10.00");
        assertThat(pricing.total()).isEqualByComparingTo("1010.00");
        verifyNoInteractions(repository);
        verify(cartClient, never()).clearCart(auth);
    }

    @Test
    void acceptsConfiguredFreeDeliveryAndKeepsTotalEqualToSubtotal() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", null, null, "1 rue", null, "Kenitra", null, "Morocco"));
        deliveryFeeProperties.setFees(Map.of("Kenitra", BigDecimal.ZERO));
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 2))));
        when(productPricingClient.getPrice(auth, "REF-001"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-001", new BigDecimal("500.00"), 10));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var pricing = service.previewPricing(auth,
            new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));
        var order = service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a"));

        assertThat(pricing.deliveryFee()).isZero();
        assertThat(pricing.total()).isEqualByComparingTo("1000.00");
        assertThat(order.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(order.deliveryFee()).isZero();
        assertThat(order.total()).isEqualByComparingTo("1000.00");
    }

    @Test
    void appliesSameDeliveryFeeToBankTransfer() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", null, null, "1 rue", null, "Kenitra", null, "Morocco"));
        deliveryFeeProperties.setFees(Map.of("Kenitra", new BigDecimal("10.00")));
        when(paymentBankRepository.findByReferenceAndActiveTrue("bank-a")).thenReturn(Optional.of(
            new PaymentBankEntity("bank-a", "CIH Bank", "MA640001111122223333444455", "WWTRADING SARL", null, true)));
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 2))));
        when(productPricingClient.getPrice(auth, "REF-001"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-001", new BigDecimal("500.00"), 10));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var order = service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a"));

        assertThat(order.subtotal()).isEqualByComparingTo("1000.00");
        assertThat(order.deliveryFee()).isEqualByComparingTo("10.00");
        assertThat(order.total()).isEqualByComparingTo("1010.00");
    }

    @Test
    void requiresAndStoresConfiguredBankSnapshotForBankTransfer() {
        PaymentBankEntity bank = new PaymentBankEntity("bank-a", "CIH Bank", "MA640001111122223333444455", "WWTRADING SARL", "https://example.com/logo.png", true);
        when(paymentBankRepository.findByReferenceAndActiveTrue("bank-a")).thenReturn(Optional.of(bank));
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-001"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-001", new BigDecimal("10.00"), 5));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, "bank-a"));

        assertThat(response.selectedBankReference()).isEqualTo("bank-a");
        assertThat(response.paymentBankName()).isEqualTo("CIH Bank");
        assertThat(response.paymentBankRib()).isEqualTo("MA640001111122223333444455");
        assertThat(response.paymentBankBeneficiary()).isEqualTo("WWTRADING SARL");
        assertThat(response.paymentBankLogoUrl()).isEqualTo("https://example.com/logo.png");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.transferProofReference()).isNull();
    }

    @Test
    void rejectsBankTransferWithInactiveOrMissingBank() {
        when(paymentBankRepository.findByReferenceAndActiveTrue("bank-inactive")).thenReturn(Optional.empty());
        JwtAuthenticationToken auth = authenticated("customer@example.test");

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, "bank-inactive")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(cartClient, repository);
    }

    @Test
    void refusesTransferProofForAnotherCustomerOrder() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        var order = new OrderEntity("another@example.test", OrderStatus.DRAFT, null,
            DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, List.of(), BigDecimal.ZERO);
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.uploadTransferProof(auth, id, mock(org.springframework.web.multipart.MultipartFile.class)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(transferProofStorage);
    }

    @Test
    void uploadsTransferProofForOwningCustomer() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        var order = new OrderEntity("customer@example.test", OrderStatus.DRAFT, null,
            DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, List.of(), BigDecimal.ZERO);
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(order));
        when(transferProofStorage.store(id, file)).thenReturn("proof-123.png");

        var response = service.uploadTransferProof(auth, id, file);

        assertThat(response.transferProofReference()).isEqualTo("proof-123.png");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        verify(transferProofStorage).store(id, file);
    }

    @Test
    void rejectsTransferProofForMissingOrder() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.uploadTransferProof(authenticated("customer@example.test"), id,
            mock(org.springframework.web.multipart.MultipartFile.class)))
            .isInstanceOf(OrderNotFoundException.class);
        verifyNoInteractions(transferProofStorage);
    }

    @Test
    void uploadsTransferProofByStaffForCustomerOrder() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticatedStaff("admin@wwtrading.ma", "ADMIN");
        var order = new OrderEntity("customer@example.test", OrderStatus.DRAFT, null,
            DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, List.of(), BigDecimal.ZERO);
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(repository.findForUpdateById(id)).thenReturn(Optional.of(order));
        when(transferProofStorage.store(id, file)).thenReturn("proof-admin.png");

        var response = service.uploadTransferProof(auth, id, file);

        assertThat(response.transferProofReference()).isEqualTo("proof-admin.png");
        verify(transferProofStorage).store(id, file);
    }

    @Test
    void downloadsTransferProofForOwningCustomer() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        var order = new OrderEntity("customer@example.test", OrderStatus.DRAFT, null,
            DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, List.of(), BigDecimal.ZERO);
        order.assignCustomerSubject("test-subject");
        order.attachTransferProof("orders/" + id + "/proof.png");

        when(repository.findById(id)).thenReturn(Optional.of(order));

        org.springframework.core.io.Resource resource = mock(org.springframework.core.io.Resource.class);
        when(resource.exists()).thenReturn(true);
        when(resource.getFilename()).thenReturn("proof.png");
        when(transferProofStorage.loadAsResource("orders/" + id + "/proof.png")).thenReturn(resource);

        var responseEntity = service.getTransferProof(auth, id, false);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(responseEntity.getBody()).isSameAs(resource);
    }

    @Test
    void downloadsTransferProofForStaffRoleAdmin() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticatedStaff("manager@wwtrading.ma", "MANAGER");
        var order = new OrderEntity("customer@example.test", OrderStatus.DRAFT, null,
            DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, List.of(), BigDecimal.ZERO);
        order.attachTransferProof("orders/" + id + "/receipt.jpg");

        when(repository.findById(id)).thenReturn(Optional.of(order));

        org.springframework.core.io.Resource resource = mock(org.springframework.core.io.Resource.class);
        when(resource.exists()).thenReturn(true);
        when(resource.getFilename()).thenReturn("receipt.jpg");
        when(transferProofStorage.loadAsResource("orders/" + id + "/receipt.jpg")).thenReturn(resource);

        var responseEntity = service.getTransferProof(auth, id, true);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(responseEntity.getHeaders().getContentDisposition().isAttachment()).isTrue();
    }

    @Test
    void refusesDownloadTransferProofForAnotherCustomer() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticated("intruder@example.test");
        var order = new OrderEntity("victim@example.test", OrderStatus.DRAFT, null,
            DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, List.of(), BigDecimal.ZERO);
        order.assignCustomerSubject("victim-subject");
        order.attachTransferProof("orders/" + id + "/proof.png");

        when(repository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getTransferProof(auth, id, false))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(transferProofStorage);
    }

    @Test
    void refusesDownloadTransferProofWhenNoneAttached() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticatedStaff("admin@wwtrading.ma", "ADMIN");
        var order = new OrderEntity("customer@example.test", OrderStatus.DRAFT, null,
            DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, List.of(), BigDecimal.ZERO);

        when(repository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.getTransferProof(auth, id, false))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(transferProofStorage);
    }

    @Test
    void usesExternalFeeForAnUnlistedCity() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", null, null, "1 rue", null, "Marrakech", "40000", "Morocco"
        ));
        deliveryFeeProperties.setFees(Map.of("Kenitra", new BigDecimal("10.00"), "External", new BigDecimal("20.00")));
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-001"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-001", new BigDecimal("10.00"), 5));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a"));

        assertThat(response.deliveryFee()).isEqualByComparingTo("20.00");
        assertThat(response.total()).isEqualByComparingTo("30.00");
    }

    @Test
    void propagatesAddressNotFoundAndDoesNotClearCart() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenThrow(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(cartClient, repository);
    }

    @Test
    void rejectsEmptyCart() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of()));

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, "bank-a")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(repository, productPricingClient);
    }

    @Test
    void rejectsPickupWhenProductAvailabilityIsInsufficient() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 6)
        )));
        when(productPricingClient.getPrice(auth, "REF-001")).thenReturn(new ProductPricingClient.ProductPrice(
            "REF-001", new BigDecimal("10.00"), 5
        ));

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, "bank-a")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        verifyNoInteractions(repository);
        verify(cartClient, never()).clearCart(auth);
    }

    @Test
    void rejectsDeliveryWithoutAddress() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(userAddressClient, cartClient, repository);
    }

    @Test
    void createsGuestDeliveryOrderWithBankTransferAndHashesTrackingToken() {
        TrackingTokenService trackingTokenService = new TrackingTokenService("http://localhost:4201", 30);
        service.setTrackingTokenService(trackingTokenService);
        when(productPricingClient.getPublicPrice("REF-G1")).thenReturn(
            new ProductPricingClient.ProductPrice("REF-G1", "Échographe Portable", new BigDecimal("5000.00"), 10)
        );
        final OrderEntity[] savedEntityHolder = new OrderEntity[1];
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(inv -> {
            savedEntityHolder[0] = inv.getArgument(0);
            return savedEntityHolder[0];
        });

        var request = new CreateGuestOrderRequest(
            "Karim Bennani",
            "karim.bennani@example.ma",
            "0661234567",
            "Clinique Atlas",
            "12 Avenue Mohammed V",
            "Étage 2",
            "Kénitra",
            "14000",
            "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.BANK_TRANSFER,
            "bank-a",
            List.of(new GuestOrderItemRequest("REF-G1", 2)),
            "idem-guest-1"
        );

        var response = service.createGuestOrder(request, null);

        assertThat(response.guestOrder()).isTrue();
        assertThat(response.customerReference()).isEqualTo("karim.bennani@example.ma");
        assertThat(response.contactFullName()).isEqualTo("Karim Bennani");
        assertThat(response.contactPhone()).isEqualTo("0661234567");
        assertThat(response.orderNumber()).startsWith("WWT-");
        assertThat(response.subtotal()).isEqualByComparingTo("10000.00");
        assertThat(response.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(response.total()).isEqualByComparingTo("10300.00");
        assertThat(response.upfrontPaymentAmount()).isEqualByComparingTo("10300.00");
        assertThat(response.remainingDeliveryAmount()).isZero();
        assertThat(response.trackingToken()).isNotBlank();
        assertThat(response.trackingUrl()).contains("/suivi-commande?token=" + response.trackingToken());

        // Raw token is NEVER stored in the database; only its SHA-256 hex digest is stored
        OrderEntity persisted = savedEntityHolder[0];
        assertThat(persisted.getTrackingTokenHash()).isNotEqualTo(response.trackingToken());
        assertThat(persisted.getTrackingTokenHash()).isEqualTo(trackingTokenService.hashToken(response.trackingToken()));
        assertThat(persisted.getCustomerSubject()).isNull();
        verifyNoInteractions(cartClient, userAddressClient);
    }

    @Test
    void createsGuestDeliveryOrderWithSplit5050AndPickupOrderWithZeroDeliveryFee() {
        TrackingTokenService trackingTokenService = new TrackingTokenService("http://localhost:4201", 30);
        service.setTrackingTokenService(trackingTokenService);
        when(productPricingClient.getPublicPrice("REF-G2")).thenReturn(
            new ProductPricingClient.ProductPrice("REF-G2", "Moniteur Multiparamétrique", new BigDecimal("4000.00"), 10)
        );
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1) Guest DELIVERY + SPLIT_50_50 in Marrakech (400 DH delivery fee)
        var splitRequest = new CreateGuestOrderRequest(
            "Salma Tazi",
            "salma@example.ma",
            "+212661998877",
            null,
            "Quartier Guéliz",
            null,
            "Marrakech",
            "40000",
            "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.SPLIT_50_50,
            "bank-a",
            List.of(new GuestOrderItemRequest("REF-G2", 1)),
            null
        );
        var splitOrder = service.createGuestOrder(splitRequest, null);
        assertThat(splitOrder.deliveryFee()).isEqualByComparingTo("400.00");
        assertThat(splitOrder.total()).isEqualByComparingTo("4400.00");
        assertThat(splitOrder.paymentPlan()).isEqualTo(PaymentPlan.SPLIT_50_50);
        assertThat(splitOrder.upfrontPaymentAmount()).isEqualByComparingTo("2200.00");
        assertThat(splitOrder.remainingDeliveryAmount()).isEqualByComparingTo("2200.00");

        // 2) Guest PICKUP + BANK_TRANSFER (0 DH delivery fee)
        var pickupRequest = new CreateGuestOrderRequest(
            "Salma Tazi",
            "salma@example.ma",
            "+212661998877",
            null,
            null,
            null,
            null,
            null,
            "Maroc",
            DeliveryMethod.PICKUP,
            PaymentMethod.BANK_TRANSFER,
            "bank-a",
            List.of(new GuestOrderItemRequest("REF-G2", 1)),
            null
        );
        var pickupOrder = service.createGuestOrder(pickupRequest, null);
        assertThat(pickupOrder.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(pickupOrder.deliveryFee()).isZero();
        assertThat(pickupOrder.total()).isEqualByComparingTo("4000.00");
        assertThat(pickupOrder.upfrontPaymentAmount()).isEqualByComparingTo("4000.00");
        assertThat(pickupOrder.remainingDeliveryAmount()).isZero();

        // 3) Guest PICKUP + SPLIT_50_50 is supported
        var pickupSplit = new CreateGuestOrderRequest(
            "Salma Tazi",
            "salma@example.ma",
            "+212661998877",
            null, null, null, null, null, "Maroc",
            DeliveryMethod.PICKUP,
            PaymentMethod.SPLIT_50_50,
            "bank-a",
            List.of(new GuestOrderItemRequest("REF-G2", 1)),
            null
        );
        var pickupSplitOrder = service.createGuestOrder(pickupSplit, null);
        assertThat(pickupSplitOrder.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(pickupSplitOrder.paymentMethod()).isEqualTo(PaymentMethod.SPLIT_50_50);
        assertThat(pickupSplitOrder.paymentPlan()).isEqualTo(PaymentPlan.SPLIT_50_50);
        assertThat(pickupSplitOrder.deliveryFee()).isZero();
        assertThat(pickupSplitOrder.total()).isEqualByComparingTo("4000.00");
        assertThat(pickupSplitOrder.upfrontPaymentAmount()).isEqualByComparingTo("2000.00");
        assertThat(pickupSplitOrder.remainingDeliveryAmount()).isEqualByComparingTo("2000.00");

        // 3b) Guest PICKUP + PAY_ON_PICKUP is supported
        var pickupPayOnSite = new CreateGuestOrderRequest(
            "Salma Tazi",
            "salma@example.ma",
            "+212661998877",
            null, null, null, null, null, "Maroc",
            DeliveryMethod.PICKUP,
            PaymentMethod.PAY_ON_PICKUP,
            null,
            List.of(new GuestOrderItemRequest("REF-G2", 1)),
            null
        );
        var pickupPayOnSiteOrder = service.createGuestOrder(pickupPayOnSite, null);
        assertThat(pickupPayOnSiteOrder.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(pickupPayOnSiteOrder.paymentMethod()).isEqualTo(PaymentMethod.PAY_ON_PICKUP);
        assertThat(pickupPayOnSiteOrder.paymentPlan()).isEqualTo(PaymentPlan.PAY_ON_PICKUP);
        assertThat(pickupPayOnSiteOrder.deliveryFee()).isZero();
        assertThat(pickupPayOnSiteOrder.total()).isEqualByComparingTo("4000.00");
        assertThat(pickupPayOnSiteOrder.upfrontPaymentAmount()).isZero();
        assertThat(pickupPayOnSiteOrder.remainingDeliveryAmount()).isEqualByComparingTo("4000.00");

        // 3c) Guest DELIVERY + PAY_ON_PICKUP is rejected
        var invalidDeliveryPayOnPickup = new CreateGuestOrderRequest(
            "Salma Tazi",
            "salma@example.ma",
            "+212661998877",
            null, "Rue 1", null, "Casablanca", "20000", "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.PAY_ON_PICKUP,
            null,
            List.of(new GuestOrderItemRequest("REF-G2", 1)),
            null
        );
        assertThatThrownBy(() -> service.createGuestOrder(invalidDeliveryPayOnPickup, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // 4) Guest CASH_ON_DELIVERY is rejected
        var invalidGuestCod = new CreateGuestOrderRequest(
            "Salma Tazi",
            "salma@example.ma",
            "+212661998877",
            null, "Rue 1", null, "Casablanca", "20000", "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.CASH_ON_DELIVERY,
            null,
            List.of(new GuestOrderItemRequest("REF-G2", 1)),
            null
        );
        assertThatThrownBy(() -> service.createGuestOrder(invalidGuestCod, null))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tracksGuestOrderAndRejectsInvalidExpiredOrRevokedTokens() {
        TrackingTokenService trackingTokenService = new TrackingTokenService("http://localhost:4201", 30);
        service.setTrackingTokenService(trackingTokenService);
        when(productPricingClient.getPublicPrice("REF-TRACK")).thenReturn(
            new ProductPricingClient.ProductPrice("REF-TRACK", "Défibrillateur", new BigDecimal("12000.00"), 5)
        );
        final OrderEntity[] savedEntityHolder = new OrderEntity[1];
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(inv -> {
            savedEntityHolder[0] = inv.getArgument(0);
            return savedEntityHolder[0];
        });

        var created = service.createGuestOrder(new CreateGuestOrderRequest(
            "Nadia El Fassi",
            "nadia@example.ma",
            "0611223344",
            null, "10 Bd Anfa", null, "Casablanca", "20000", "Maroc",
            DeliveryMethod.DELIVERY,
            PaymentMethod.BANK_TRANSFER,
            "bank-a",
            List.of(new GuestOrderItemRequest("REF-TRACK", 1)),
            null
        ), null);

        String rawToken = created.trackingToken();
        String tokenHash = trackingTokenService.hashToken(rawToken);
        when(repository.findByTrackingTokenHash(tokenHash)).thenReturn(Optional.of(savedEntityHolder[0]));

        // Valid token returns non-sensitive summary
        var tracked = service.trackOrderByToken(rawToken);
        assertThat(tracked.orderNumber()).isEqualTo(created.orderNumber());
        assertThat(tracked.recipientFirstName()).isEqualTo("Nadia");
        assertThat(tracked.shippingCity()).isEqualTo("Casablanca");
        assertThat(tracked.total()).isEqualByComparingTo("12300.00");

        // Invalid token -> 404
        assertThatThrownBy(() -> service.trackOrderByToken("invalid-random-token-12345678901234567890"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Expired token -> 404
        savedEntityHolder[0].rotateTrackingToken(tokenHash, java.time.Instant.now().minusSeconds(60));
        assertThatThrownBy(() -> service.trackOrderByToken(rawToken))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Revoked token -> 404
        savedEntityHolder[0].rotateTrackingToken(tokenHash, java.time.Instant.now().plusSeconds(3600));
        savedEntityHolder[0].revokeTrackingToken();
        assertThatThrownBy(() -> service.trackOrderByToken(rawToken))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void claimsGuestOrdersOnlyWhenEmailIsVerifiedAndMatchesSameEmail() {
        TrackingTokenService trackingTokenService = new TrackingTokenService("http://localhost:4201", 30);
        var generated = trackingTokenService.generateToken(java.time.Instant.now());
        var guestOrder = new OrderEntity(
            "client.verified@example.ma",
            OrderStatus.DRAFT,
            null,
            DeliveryMethod.PICKUP,
            PaymentMethod.BANK_TRANSFER,
            List.of(),
            BigDecimal.ZERO
        );
        guestOrder.markAsGuestOrder(
            "Client Verified",
            "client.verified@example.ma",
            "0600000000",
            generated.tokenHash(),
            generated.expiresAt()
        );

        // 1) Unverified email is rejected with 403 Forbidden
        Jwt unverifiedJwt = mock(Jwt.class);
        when(unverifiedJwt.getClaimAsString("email")).thenReturn("client.verified@example.ma");
        when(unverifiedJwt.getClaimAsBoolean("email_verified")).thenReturn(false);
        when(unverifiedJwt.getSubject()).thenReturn("sub-unverified");
        JwtAuthenticationToken unverifiedAuth = mock(JwtAuthenticationToken.class);
        when(unverifiedAuth.getToken()).thenReturn(unverifiedJwt);

        assertThatThrownBy(() -> service.claimGuestOrders(unverifiedAuth))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        // 2) Verified email links matching unclaimed guest orders idempotently
        Jwt verifiedJwt = mock(Jwt.class);
        when(verifiedJwt.getClaimAsString("email")).thenReturn("Client.Verified@example.ma");
        when(verifiedJwt.getClaimAsBoolean("email_verified")).thenReturn(true);
        when(verifiedJwt.getSubject()).thenReturn("sub-verified-123");
        JwtAuthenticationToken verifiedAuth = mock(JwtAuthenticationToken.class);
        when(verifiedAuth.getToken()).thenReturn(verifiedJwt);

        when(repository.findUnclaimedGuestOrdersForUpdateByEmail("client.verified@example.ma"))
            .thenReturn(List.of(guestOrder));

        var claimResponse = service.claimGuestOrders(verifiedAuth);
        assertThat(claimResponse.linkedCount()).isEqualTo(1);
        assertThat(claimResponse.linkedOrderNumbers()).containsExactly(guestOrder.getOrderNumber());
        assertThat(guestOrder.getCustomerSubject()).isEqualTo("sub-verified-123");
        assertThat(guestOrder.getLinkedAt()).isNotNull();

        // 3) Another account with a different email cannot claim this order
        Jwt otherUserJwt = mock(Jwt.class);
        when(otherUserJwt.getClaimAsString("email")).thenReturn("other.user@example.ma");
        when(otherUserJwt.getClaimAsBoolean("email_verified")).thenReturn(true);
        when(otherUserJwt.getSubject()).thenReturn("sub-other-999");
        JwtAuthenticationToken otherAuth = mock(JwtAuthenticationToken.class);
        when(otherAuth.getToken()).thenReturn(otherUserJwt);
        when(repository.findUnclaimedGuestOrdersForUpdateByEmail("other.user@example.ma")).thenReturn(List.of());

        var otherClaim = service.claimGuestOrders(otherAuth);
        assertThat(otherClaim.linkedCount()).isZero();
        assertThat(guestOrder.getCustomerSubject()).isEqualTo("sub-verified-123");
    }

    @Test
    void preventsDuplicateGuestOrderCreationWithSameIdempotencyKey() {
        TrackingTokenService trackingTokenService = new TrackingTokenService("http://localhost:4201", 30);
        var generated = trackingTokenService.generateToken(java.time.Instant.now());
        var existingOrder = new OrderEntity(
            "idem@example.ma",
            OrderStatus.DRAFT,
            null,
            DeliveryMethod.PICKUP,
            PaymentMethod.BANK_TRANSFER,
            List.of(new OrderItemEntity("REF-1", "Produit 1", 1, new BigDecimal("500.00"))),
            BigDecimal.ZERO
        );
        existingOrder.markAsGuestOrder("Idem User", "idem@example.ma", "0600000000", generated.tokenHash(), generated.expiresAt());
        existingOrder.assignIdempotencyKey("idem-key-123");
        when(repository.findByIdempotencyKey("idem-key-123")).thenReturn(Optional.of(existingOrder));

        var request = new CreateGuestOrderRequest(
            "Idem User",
            "idem@example.ma",
            "0600000000",
            null, null, null, null, null, "Maroc",
            DeliveryMethod.PICKUP,
            PaymentMethod.BANK_TRANSFER,
            "bank-a",
            List.of(new GuestOrderItemRequest("REF-1", 1)),
            null
        );

        var response = service.createGuestOrder(request, "idem-key-123");
        assertThat(response.orderNumber()).isEqualTo(existingOrder.getOrderNumber());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMissingEmailClaim() {
        JwtAuthenticationToken auth = authenticated(null);

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.BANK_TRANSFER, "bank-a")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(cartClient, repository);
    }

    @Test
    void rejectsUnknownOrderId() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        Jwt jwt = mock(Jwt.class);
        when(auth.getToken()).thenReturn(jwt);
        when(jwt.getClaimAsString("email")).thenReturn("customer@example.test");
        when(repository.findByIdAndCustomerReference(eq(id), eq("customer@example.test"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(auth, id))
            .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void allowsStaffToReadAnyOrder() {
        UUID id = UUID.randomUUID();
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var order = new OrderEntity(
            "another-customer@example.test",
            OrderStatus.DRAFT,
            null,
            DeliveryMethod.PICKUP,
            PaymentMethod.CASH_ON_DELIVERY,
            List.of(),
            BigDecimal.ZERO
        );
        when(repository.findById(id)).thenReturn(Optional.of(order));

        var response = service.getById(auth, id);

        assertThat(response.customerReference()).isEqualTo("another-customer@example.test");
        verify(repository).findById(id);
        verify(repository, never()).findByIdAndCustomerReference(any(), any());
    }

    @Test
    void mapsPaginatedResults() {
        var pageable = PageRequest.of(1, 10);
        var order = new OrderEntity(
            "customer-1",
            OrderStatus.DRAFT,
            null,
            DeliveryMethod.PICKUP,
            PaymentMethod.CASH_ON_DELIVERY,
            List.of(),
            BigDecimal.ZERO
        );
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 11));

        var page = service.list(pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(11);
    }

    @Test
    void appliesDefaultAreaDeliveryFeesForKenitraCasablancaAndOtherCities() {
        DeliveryFeeProperties defaults = new DeliveryFeeProperties();

        assertThat(defaults.feeForCity("Kenitra")).isEqualByComparingTo("300.00");
        assertThat(defaults.feeForCity("Kénitra")).isEqualByComparingTo("300.00");
        assertThat(defaults.feeForCity("Mehdia")).isEqualByComparingTo("300.00");
        assertThat(defaults.feeForCity("Sidi Taïbi")).isEqualByComparingTo("300.00");
        assertThat(defaults.feeForCity("Casablanca")).isEqualByComparingTo("300.00");
        assertThat(defaults.feeForCity("CASABLANCA")).isEqualByComparingTo("300.00");
        assertThat(defaults.feeForCity("Rabat")).isEqualByComparingTo("400.00");
        assertThat(defaults.feeForCity("Marrakech")).isEqualByComparingTo("400.00");
        assertThat(defaults.feeForCity("Tanger")).isEqualByComparingTo("400.00");
    }

    @Test
    void enforcesStrictTwentyThousandFreeDeliveryThresholdOnPreviewAndCreate() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", "WWTRADING Client", "0600000000", "1 Bd Zerktouni", null, "Casablanca", "20000", "Maroc"
        ));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Subtotal = 19,000.00 DH -> 300.00 DH fee applies
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-19K", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-19K"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-19K", new BigDecimal("19000.00"), 5));

        var preview19k = service.previewPricing(auth, new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));
        var order19k = service.create(auth, new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a"));
        assertThat(preview19k.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(preview19k.total()).isEqualByComparingTo("19300.00");
        assertThat(order19k.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(order19k.total()).isEqualByComparingTo("19300.00");
        assertThat(order19k.deliveryFeeWaivedReason()).isNull();

        // Subtotal = 20,000.00 DH -> 300.00 DH fee STILL applies (strictly greater than 20,000 DH is free)
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-20K", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-20K"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-20K", new BigDecimal("20000.00"), 5));

        var preview20k = service.previewPricing(auth, new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));
        var order20k = service.create(auth, new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a"));
        assertThat(preview20k.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(preview20k.total()).isEqualByComparingTo("20300.00");
        assertThat(order20k.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(order20k.total()).isEqualByComparingTo("20300.00");
        assertThat(order20k.deliveryFeeWaivedReason()).isNull();

        // Subtotal = 20,000.01 DH -> 0.00 DH free delivery
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-20K-PLUS", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-20K-PLUS"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-20K-PLUS", new BigDecimal("20000.01"), 5));

        var previewFree = service.previewPricing(auth, new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));
        var orderFree = service.create(auth, new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a"));
        assertThat(previewFree.deliveryFee()).isZero();
        assertThat(previewFree.total()).isEqualByComparingTo("20000.01");
        assertThat(previewFree.deliveryFeeWaivedReason()).isEqualTo("FREE_THRESHOLD");
        assertThat(orderFree.deliveryFee()).isZero();
        assertThat(orderFree.total()).isEqualByComparingTo("20000.01");
        assertThat(orderFree.deliveryFeeWaivedReason()).isEqualTo("FREE_THRESHOLD");
    }

    @Test
    void waivesDeliveryFeeWhenAdminDisablesDeliveryFees() {
        DeliverySettingsRepository settingsRepo = mock(DeliverySettingsRepository.class);
        DeliverySettingsEntity disabledSettings = DeliverySettingsEntity.defaultSettings();
        disabledSettings.setFeesEnabled(false);
        when(settingsRepo.findById(DeliverySettingsEntity.DEFAULT_ID)).thenReturn(Optional.of(disabledSettings));
        when(settingsRepo.saveAndFlush(any(DeliverySettingsEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliverySettingsApplicationService settingsService = new DeliverySettingsApplicationService(settingsRepo, deliveryFeeProperties);
        service.setDeliverySettingsService(settingsService);

        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", null, "0600000000", "1 Avenue Mohammed V", null, "Tanger", "90000", "Maroc"
        ));
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-001", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-001"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-001", new BigDecimal("5000.00"), 5));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var preview = service.previewPricing(auth, new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));
        var order = service.create(auth, new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a"));

        assertThat(preview.deliveryFeesEnabled()).isFalse();
        assertThat(preview.deliveryFee()).isZero();
        assertThat(preview.total()).isEqualByComparingTo("5000.00");
        assertThat(preview.deliveryFeeWaivedReason()).isEqualTo("ADMIN_DISABLED");
        assertThat(order.deliveryFee()).isZero();
        assertThat(order.total()).isEqualByComparingTo("5000.00");
        assertThat(order.deliveryFeeWaivedReason()).isEqualTo("ADMIN_DISABLED");

        // Re-enable via Admin service and verify 400.00 DH fee applies for Tanger
        settingsService.updateSettings(new UpdateDeliverySettingsRequest(true));
        var reenabledPreview = service.previewPricing(auth, new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));
        assertThat(reenabledPreview.deliveryFeesEnabled()).isTrue();
        assertThat(reenabledPreview.deliveryFee()).isEqualByComparingTo("400.00");
        assertThat(reenabledPreview.total()).isEqualByComparingTo("5400.00");
    }

    @Test
    void calculatesSplit5050InstallmentsWithDeterministicRoundingAndTracksTwoStageConfirmation() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", "Entreprise B2B", "0600000000", "Rue 1", null, "Kenitra", "14000", "Maroc"
        ));
        PaymentBankEntity bank = new PaymentBankEntity("bank-a", "CIH Bank", "MA640001111122223333444455", "WWTRADING SARL", null, true);
        when(paymentBankRepository.findByReferenceAndActiveTrue("bank-a")).thenReturn(Optional.of(bank));
        // Product 701.01 + Kenitra fee 300.00 = 1001.01 DH (odd cent total)
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(
            new CartClient.CartItem(UUID.randomUUID(), "REF-ODD", 1)
        )));
        when(productPricingClient.getPrice(auth, "REF-ODD"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-ODD", new BigDecimal("701.01"), 5));

        final OrderEntity[] persistedHolder = new OrderEntity[1];
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> {
            persistedHolder[0] = invocation.getArgument(0);
            return persistedHolder[0];
        });

        var preview = service.previewPricing(auth, new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY));
        assertThat(preview.total()).isEqualByComparingTo("1001.01");
        assertThat(preview.upfrontPaymentAmount()).isEqualByComparingTo("500.51");
        assertThat(preview.remainingDeliveryAmount()).isEqualByComparingTo("500.50");
        assertThat(preview.upfrontPaymentAmount().add(preview.remainingDeliveryAmount()))
            .isEqualByComparingTo(preview.total());

        var created = service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.SPLIT_50_50, "bank-a"));

        assertThat(created.paymentMethod()).isEqualTo(PaymentMethod.SPLIT_50_50);
        assertThat(created.paymentPlan()).isEqualTo(PaymentPlan.SPLIT_50_50);
        assertThat(created.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(created.total()).isEqualByComparingTo("1001.01");
        assertThat(created.upfrontPaymentAmount()).isEqualByComparingTo("500.51");
        assertThat(created.remainingDeliveryAmount()).isEqualByComparingTo("500.50");
        assertThat(created.amountPaid()).isZero();
        assertThat(created.outstandingBalance()).isEqualByComparingTo("1001.01");

        UUID orderId = persistedHolder[0].getId();
        when(repository.findForUpdateById(orderId)).thenReturn(Optional.of(persistedHolder[0]));
        when(repository.findForUpdateByIdAndCustomerReference(orderId, "customer@example.test"))
            .thenReturn(Optional.of(persistedHolder[0]));

        // Rejects invalid/excessive/negative explicit installment amounts
        assertThatThrownBy(() -> service.confirmPayment(orderId, new BigDecimal("-10.00")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.confirmPayment(orderId, new BigDecimal("2000.00")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.confirmPayment(orderId, new BigDecimal("100.00")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // Confirm first 50% installment -> becomes PARTIALLY_PAID (NOT PAID)
        var afterFirst = service.confirmPayment(orderId);
        assertThat(afterFirst.paymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(afterFirst.amountPaid()).isEqualByComparingTo("500.51");
        assertThat(afterFirst.outstandingBalance()).isEqualByComparingTo("500.50");
        assertThat(afterFirst.firstInstallmentConfirmedAt()).isNotNull();
        assertThat(afterFirst.secondInstallmentConfirmedAt()).isNull();
        assertThat(afterFirst.paidAt()).isNull();

        // Client cannot cancel after first installment has been confirmed
        assertThatThrownBy(() -> service.cancel(auth, orderId))
            .isInstanceOf(OrderNotCancellableException.class);

        // Confirm second 50% installment -> becomes PAID
        var afterSecond = service.confirmPayment(orderId);
        assertThat(afterSecond.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(afterSecond.amountPaid()).isEqualByComparingTo("1001.01");
        assertThat(afterSecond.outstandingBalance()).isZero();
        assertThat(afterSecond.secondInstallmentConfirmedAt()).isNotNull();
        assertThat(afterSecond.paidAt()).isNotNull();

        // Duplicate confirmation attempt is rejected with 409 Conflict (PaymentConfirmationException)
        assertThatThrownBy(() -> service.confirmPayment(orderId))
            .isInstanceOf(PaymentConfirmationException.class);
    }

    @Test
    void supportsSplit5050AndPayOnPickupForPickupOrdersAndRejectsPayOnPickupForDelivery() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        var cartItem = new CartClient.CartItem(UUID.randomUUID(), "REF-P1", 2);
        when(productPricingClient.getPrice(auth, "REF-P1"))
            .thenReturn(new ProductPricingClient.ProductPrice("REF-P1", new BigDecimal("500.00"), 5));
        when(cartClient.getCart(auth)).thenReturn(new CartClient.Cart(UUID.randomUUID(), List.of(cartItem)));
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 1) PICKUP + SPLIT_50_50 is accepted
        var splitPickupOrder = service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.SPLIT_50_50, "bank-a"));
        assertThat(splitPickupOrder.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(splitPickupOrder.paymentMethod()).isEqualTo(PaymentMethod.SPLIT_50_50);
        assertThat(splitPickupOrder.paymentPlan()).isEqualTo(PaymentPlan.SPLIT_50_50);
        assertThat(splitPickupOrder.deliveryFee()).isZero();
        assertThat(splitPickupOrder.total()).isEqualByComparingTo("1000.00");
        assertThat(splitPickupOrder.upfrontPaymentAmount()).isEqualByComparingTo("500.00");
        assertThat(splitPickupOrder.remainingDeliveryAmount()).isEqualByComparingTo("500.00");

        // 2) PICKUP + PAY_ON_PICKUP is accepted
        var payOnPickupOrder = service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.PAY_ON_PICKUP, null));
        assertThat(payOnPickupOrder.deliveryMethod()).isEqualTo(DeliveryMethod.PICKUP);
        assertThat(payOnPickupOrder.paymentMethod()).isEqualTo(PaymentMethod.PAY_ON_PICKUP);
        assertThat(payOnPickupOrder.paymentPlan()).isEqualTo(PaymentPlan.PAY_ON_PICKUP);
        assertThat(payOnPickupOrder.deliveryFee()).isZero();
        assertThat(payOnPickupOrder.total()).isEqualByComparingTo("1000.00");
        assertThat(payOnPickupOrder.upfrontPaymentAmount()).isZero();
        assertThat(payOnPickupOrder.remainingDeliveryAmount()).isEqualByComparingTo("1000.00");

        // 3) DELIVERY + PAY_ON_PICKUP is rejected
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", "WWTRADING Client", "0600000000", "1 Avenue Hassan II", null, "Kenitra", "14000", "Maroc"
        ));
        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.PAY_ON_PICKUP, null)))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void supportsSecondTransferProofForSplit5050OrdersPreservingFirstProof() {
        UUID orderId = UUID.randomUUID();
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        var item = new OrderItemEntity("REF-001", "Product 1", 2, new BigDecimal("500.00"));
        var order = new OrderEntity(
            "customer@example.test",
            OrderStatus.CONFIRMED,
            null,
            DeliveryMethod.PICKUP,
            PaymentMethod.SPLIT_50_50,
            List.of(item),
            BigDecimal.ZERO
        );
        when(repository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(repository.findById(orderId)).thenReturn(Optional.of(order));

        var firstFile = new org.springframework.mock.web.MockMultipartFile("file", "first.png", "image/png", new byte[] {1, 2});
        var secondFile = new org.springframework.mock.web.MockMultipartFile("file", "second.png", "image/png", new byte[] {3, 4});

        when(transferProofStorage.store(orderId, firstFile)).thenReturn("orders/" + orderId + "/proof-1.png");
        when(transferProofStorage.store(orderId, secondFile)).thenReturn("orders/" + orderId + "/proof-2.png");

        // 1. Upload first installment proof
        var res1 = service.uploadTransferProof(auth, orderId, firstFile, 1);
        assertThat(res1.transferProofReference()).isEqualTo("orders/" + orderId + "/proof-1.png");
        assertThat(res1.secondTransferProofReference()).isNull();

        // 2. Cannot upload second proof before first installment is validated (status is still UNPAID)
        assertThatThrownBy(() -> service.uploadTransferProof(auth, orderId, secondFile, 2))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // 3. Confirm first installment -> becomes PARTIALLY_PAID
        service.confirmPayment(orderId);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);

        // 4. Upload second installment proof
        var res2 = service.uploadTransferProof(auth, orderId, secondFile, 2);
        assertThat(res2.transferProofReference()).isEqualTo("orders/" + orderId + "/proof-1.png");
        assertThat(res2.secondTransferProofReference()).isEqualTo("orders/" + orderId + "/proof-2.png");

        // 5. Confirm second installment -> becomes PAID
        service.confirmPayment(orderId);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(order.getOutstandingBalance()).isZero();

        // 6. Cannot upload more proofs once PAID
        assertThatThrownBy(() -> service.uploadTransferProof(auth, orderId, secondFile, 2))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enforcesStrictTwentyThousandFreeDeliveryThresholdOnQuotationPath() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        when(auth.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        UUID addressId = UUID.randomUUID();
        when(userAddressClient.getAddress(auth, addressId)).thenReturn(new UserAddressClient.Address(
            addressId, "Jane", "Doe", "WWTRADING Client", "0600000000", "1 Avenue Hassan II", null, "Kenitra", "14000", "Maroc"
        ));

        var discovery = mock(org.springframework.cloud.client.discovery.DiscoveryClient.class);
        var serviceInstance = mock(org.springframework.cloud.client.ServiceInstance.class);
        when(serviceInstance.getUri()).thenReturn(java.net.URI.create("http://quotation-service:8082"));
        when(discovery.getInstances("quotation-service")).thenReturn(List.of(serviceInstance));

        var restClientBuilder = mock(org.springframework.web.client.RestClient.Builder.class);
        var restClient = mock(org.springframework.web.client.RestClient.class);
        @SuppressWarnings("rawtypes")
        var requestHeadersUriSpec = mock(org.springframework.web.client.RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings("rawtypes")
        var requestHeadersSpec = mock(org.springframework.web.client.RestClient.RequestHeadersSpec.class);
        var responseSpec = mock(org.springframework.web.client.RestClient.ResponseSpec.class);
        when(restClientBuilder.clone()).thenReturn(restClientBuilder);
        when(restClientBuilder.baseUrl(any(String.class))).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/api/quotations/{id}"), any(UUID.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(any(String.class), any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        var txManager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        service.configureQuotationCheckout(restClientBuilder, discovery, txManager);
        when(repository.saveAndFlush(any(OrderEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1) Quotation subtotal = 20,000.00 DH -> Kenitra fee 300.00 DH STILL applies
        UUID quotation20kId = UUID.randomUUID();
        when(responseSpec.body(OrderApplicationService.QuotationSnapshot.class)).thenReturn(
            new OrderApplicationService.QuotationSnapshot(
                quotation20kId,
                "customer@example.test",
                "ACCEPTED",
                List.of(new OrderApplicationService.QuotationLine(
                    "REF-Q-20K", 1, new BigDecimal("20000.00"), new BigDecimal("20000.00"))),
                new BigDecimal("20000.00"),
                new BigDecimal("0.00")
            )
        );

        var previewQ20k = service.previewPricing(auth,
            new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY, quotation20kId));
        var orderQ20k = service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a", quotation20kId));

        assertThat(previewQ20k.subtotal()).isEqualByComparingTo("20000.00");
        assertThat(previewQ20k.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(previewQ20k.total()).isEqualByComparingTo("20300.00");
        assertThat(previewQ20k.deliveryFeeWaivedReason()).isNull();
        assertThat(orderQ20k.subtotal()).isEqualByComparingTo("20000.00");
        assertThat(orderQ20k.deliveryFee()).isEqualByComparingTo("300.00");
        assertThat(orderQ20k.total()).isEqualByComparingTo("20300.00");
        assertThat(orderQ20k.deliveryFeeWaivedReason()).isNull();

        // 2) Quotation subtotal = 20,000.01 DH -> delivery fee is 0.00 DH (FREE_THRESHOLD)
        UUID quotation20kPlusId = UUID.randomUUID();
        when(responseSpec.body(OrderApplicationService.QuotationSnapshot.class)).thenReturn(
            new OrderApplicationService.QuotationSnapshot(
                quotation20kPlusId,
                "customer@example.test",
                "ACCEPTED",
                List.of(new OrderApplicationService.QuotationLine(
                    "REF-Q-20K-PLUS", 1, new BigDecimal("20000.01"), new BigDecimal("20000.01"))),
                new BigDecimal("20000.01"),
                new BigDecimal("0.00")
            )
        );

        var previewQFree = service.previewPricing(auth,
            new PreviewOrderPricingRequest(addressId, DeliveryMethod.DELIVERY, quotation20kPlusId));
        var orderQFree = service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.BANK_TRANSFER, "bank-a", quotation20kPlusId));

        assertThat(previewQFree.subtotal()).isEqualByComparingTo("20000.01");
        assertThat(previewQFree.deliveryFee()).isZero();
        assertThat(previewQFree.total()).isEqualByComparingTo("20000.01");
        assertThat(previewQFree.deliveryFeeWaivedReason()).isEqualTo("FREE_THRESHOLD");
        assertThat(orderQFree.subtotal()).isEqualByComparingTo("20000.01");
        assertThat(orderQFree.deliveryFee()).isZero();
        assertThat(orderQFree.total()).isEqualByComparingTo("20000.01");
        assertThat(orderQFree.deliveryFeeWaivedReason()).isEqualTo("FREE_THRESHOLD");
    }

    @Test
    void rejectsCashOnDeliveryForNewOrders() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        when(auth.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        UUID addressId = UUID.randomUUID();
        UUID quotationId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.CASH_ON_DELIVERY)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).isEqualTo("Payment method is not supported");
            });

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(null, DeliveryMethod.PICKUP, PaymentMethod.CASH_ON_DELIVERY)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).isEqualTo("Payment method is not supported");
            });

        assertThatThrownBy(() -> service.create(auth,
            new CreateOrderRequest(addressId, DeliveryMethod.DELIVERY, PaymentMethod.CASH_ON_DELIVERY, null, quotationId)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).isEqualTo("Payment method is not supported");
            });

        verifyNoInteractions(userAddressClient, cartClient, repository);
    }

    @Test
    void selectSecondInstallmentMethodAllowsCustomerToChooseCashOrBankTransfer() {
        JwtAuthenticationToken auth = authenticated("customer@example.test");
        when(auth.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        UUID orderId = UUID.randomUUID();

        OrderEntity order = new OrderEntity(
                "customer@example.test",
                OrderStatus.CONFIRMED,
                new ma.wwtrading.orders.api.ShippingAddressSnapshot("A", "B", null, "0600000000", "L1", null, "Kenitra", "14000", "MA"),
                DeliveryMethod.DELIVERY,
                PaymentMethod.SPLIT_50_50,
                List.of(new OrderItemEntity("P1", "Product 1", 1, new BigDecimal("1000.00"))),
                BigDecimal.ZERO);
        order.assignCustomerSubject("test-subject");
        order.confirmPayment(new BigDecimal("500.00")); // 1st installment confirmed

        when(repository.findForUpdateById(orderId)).thenReturn(Optional.of(order));

        var response = service.selectSecondInstallmentMethod(auth, orderId, "CASH");
        assertThat(response.secondInstallmentPaymentMethod()).isEqualTo("CASH");

        var responseTransfer = service.selectSecondInstallmentMethod(auth, orderId, "BANK_TRANSFER");
        assertThat(responseTransfer.secondInstallmentPaymentMethod()).isEqualTo("BANK_TRANSFER");
    }

    @Test
    void selectSecondInstallmentMethodDeniesOtherCustomers() {
        JwtAuthenticationToken auth = authenticated("intruder@example.test");
        when(auth.getAuthorities()).thenReturn(List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
        UUID orderId = UUID.randomUUID();

        OrderEntity order = new OrderEntity(
                "customer@example.test",
                OrderStatus.CONFIRMED,
                new ma.wwtrading.orders.api.ShippingAddressSnapshot("A", "B", null, "0600000000", "L1", null, "Kenitra", "14000", "MA"),
                DeliveryMethod.DELIVERY,
                PaymentMethod.SPLIT_50_50,
                List.of(new OrderItemEntity("P1", "Product 1", 1, new BigDecimal("1000.00"))),
                BigDecimal.ZERO);
        order.assignCustomerSubject("owner-subject");
        order.confirmPayment(new BigDecimal("500.00"));

        when(repository.findForUpdateById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.selectSecondInstallmentMethod(auth, orderId, "CASH"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void confirmPaymentRecordsStaffIdentity() {
        UUID orderId = UUID.randomUUID();

        OrderEntity order = new OrderEntity(
                "customer@example.test",
                OrderStatus.CONFIRMED,
                new ma.wwtrading.orders.api.ShippingAddressSnapshot("A", "B", null, "0600000000", "L1", null, "Kenitra", "14000", "MA"),
                DeliveryMethod.DELIVERY,
                PaymentMethod.SPLIT_50_50,
                List.of(new OrderItemEntity("P1", "Product 1", 1, new BigDecimal("1000.00"))),
                BigDecimal.ZERO);
        order.confirmPayment(new BigDecimal("500.00")); // 1st installment confirmed

        when(repository.findForUpdateById(orderId)).thenReturn(Optional.of(order));

        var response = service.confirmPayment(orderId, new BigDecimal("500.00"), "staff_super_admin");
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.secondInstallmentConfirmedBy()).isEqualTo("staff_super_admin");
        assertThat(response.secondInstallmentConfirmedAt()).isNotNull();
    }

    private JwtAuthenticationToken authenticated(String email) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getSubject()).thenReturn("test-subject");
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.getToken()).thenReturn(jwt);
        return auth;
    }

    private JwtAuthenticationToken authenticatedStaff(String email, String role) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getSubject()).thenReturn("staff-subject");
        JwtAuthenticationToken auth = mock(JwtAuthenticationToken.class);
        when(auth.getToken()).thenReturn(jwt);
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
            .when(auth).getAuthorities();
        return auth;
    }
}

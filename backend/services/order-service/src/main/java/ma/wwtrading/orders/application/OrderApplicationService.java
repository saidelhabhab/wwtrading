package ma.wwtrading.orders.application;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;

import ma.wwtrading.orders.api.ClaimGuestOrdersResponse;
import ma.wwtrading.orders.api.CreateGuestOrderRequest;
import ma.wwtrading.orders.api.CreateOrderRequest;
import ma.wwtrading.orders.api.DeliverySettingsResponse;
import ma.wwtrading.orders.api.GuestOrderItemRequest;
import ma.wwtrading.orders.api.OrderResponse;
import ma.wwtrading.orders.api.UpdateDeliverySettingsRequest;
import ma.wwtrading.orders.api.OrderPricingResponse;
import ma.wwtrading.orders.api.PreviewGuestOrderPricingRequest;
import ma.wwtrading.orders.api.PreviewOrderPricingRequest;
import ma.wwtrading.orders.api.PublicOrderTrackingResponse;
import ma.wwtrading.orders.api.ShippingAddressSnapshot;
import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.OrderEntity;
import ma.wwtrading.orders.domain.OrderItemEntity;
import ma.wwtrading.orders.domain.OrderRepository;
import ma.wwtrading.orders.domain.PaymentMethod;
import ma.wwtrading.orders.domain.PaymentPlan;
import ma.wwtrading.orders.infrastructure.TransferProofStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ma.wwtrading.orders.domain.PaymentBankEntity;
import ma.wwtrading.orders.domain.PaymentBankRepository;
import ma.wwtrading.orders.api.PaymentBankResponse;
import ma.wwtrading.orders.domain.PaymentStatus;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.infrastructure.kafka.OrderCreatedEventEnvelope;
import ma.wwtrading.orders.infrastructure.kafka.OrderEventPublisher;
import ma.wwtrading.orders.infrastructure.kafka.OrderStatusChangedEventEnvelope;

@Service
public class OrderApplicationService {

    private final OrderRepository repository;
    private final OrderMapper mapper;
    private final UserAddressClient userAddressClient;
    private final CartClient cartClient;
    private final ProductPricingClient productPricingClient;
    private final DeliveryFeeProperties deliveryFeeProperties;
    private final PaymentBankRepository paymentBankRepository;
    private final PaymentBankApplicationService paymentBankApplicationService;
    private final TransferProofStorage transferProofStorage;
    private final OrderEventPublisher orderEventPublisher;
    private DeliverySettingsApplicationService deliverySettingsService;
    private TrackingTokenService trackingTokenService = new TrackingTokenService();
    private RestClient.Builder restClientBuilder;
    private DiscoveryClient discoveryClient;
    private TransactionTemplate quotationTransaction;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(OrderApplicationService.class);
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Autowired
    public OrderApplicationService(OrderRepository repository, OrderMapper mapper, UserAddressClient userAddressClient,
            CartClient cartClient, ProductPricingClient productPricingClient,
            DeliveryFeeProperties deliveryFeeProperties,
            PaymentBankRepository paymentBankRepository,
            PaymentBankApplicationService paymentBankApplicationService,
            TransferProofStorage transferProofStorage,
            OrderEventPublisher orderEventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.userAddressClient = userAddressClient;
        this.cartClient = cartClient;
        this.productPricingClient = productPricingClient;
        this.deliveryFeeProperties = deliveryFeeProperties;
        this.paymentBankRepository = paymentBankRepository;
        this.paymentBankApplicationService = paymentBankApplicationService;
        this.transferProofStorage = transferProofStorage;
        this.orderEventPublisher = orderEventPublisher;
    }

    public OrderApplicationService(OrderRepository repository, OrderMapper mapper, UserAddressClient userAddressClient,
            CartClient cartClient, ProductPricingClient productPricingClient,
            DeliveryFeeProperties deliveryFeeProperties) {
        this(repository, mapper, userAddressClient, cartClient, productPricingClient, deliveryFeeProperties,
                null, null, null, null);
    }

    @Autowired(required = false)
    public void setDeliverySettingsService(DeliverySettingsApplicationService deliverySettingsService) {
        this.deliverySettingsService = deliverySettingsService;
    }

    @Autowired(required = false)
    public void setTrackingTokenService(TrackingTokenService trackingTokenService) {
        if (trackingTokenService != null) {
            this.trackingTokenService = trackingTokenService;
        }
    }

    @Autowired
    void configureQuotationCheckout(RestClient.Builder builder, DiscoveryClient discovery,
            PlatformTransactionManager transactionManager) {
        this.restClientBuilder = builder;
        this.discoveryClient = discovery;
        this.quotationTransaction = new TransactionTemplate(transactionManager);
        this.quotationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public DeliverySettingsResponse getDeliverySettings() {
        if (deliverySettingsService != null) {
            return deliverySettingsService.getSettings();
        }
        return new DeliverySettingsApplicationService(null, deliveryFeeProperties).getSettings();
    }

    @Transactional
    public DeliverySettingsResponse updateDeliverySettings(UpdateDeliverySettingsRequest request) {
        if (deliverySettingsService != null) {
            return deliverySettingsService.updateSettings(request);
        }
        return new DeliverySettingsApplicationService(null, deliveryFeeProperties).updateSettings(request);
    }

    @Transactional
    public OrderResponse create(JwtAuthenticationToken authentication, CreateOrderRequest request) {
        return create(authentication, request, null);
    }

    @Transactional
    public OrderResponse create(JwtAuthenticationToken authentication, CreateOrderRequest request,
            String headerIdempotencyKey) {
        String customerReference = customerReference(authentication);
        String effectiveIdempotencyKey = resolveIdempotencyKey(headerIdempotencyKey, request != null ? request.idempotencyKey() : null);
        if (effectiveIdempotencyKey != null) {
            var existingByIdempotency = repository.findByIdempotencyKey(effectiveIdempotencyKey);
            if (existingByIdempotency != null && existingByIdempotency.isPresent()) {
                OrderEntity existingOrder = existingByIdempotency.get();
                if (!customerReference.equalsIgnoreCase(existingOrder.getCustomerReference())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to another customer");
                }
                return mapper.toResponse(existingOrder);
            }
        }
        validatePaymentSelection(request);
        syncDeliverySettings();
        if (request.quotationId() != null) {
            return createFromQuotation(authentication, request, customerReference);
        }
        PaymentBankEntity selectedBank = validateBankTransfer(request.paymentMethod(), request.bankReference());
        PreparedOrder prepared = prepare(authentication, request.addressId(), request.deliveryMethod());
        var orderEntity = mapper.toEntity(
                customerReference,
                prepared.shippingAddress(),
                request,
                prepared.items(),
                prepared.deliveryFee(),
                prepared.deliveryArea(),
                prepared.deliveryFeeWaivedReason());
        if (!isStaff(authentication)) {
            orderEntity.assignCustomerSubject(subject(authentication));
        }
        if (selectedBank != null) {
            orderEntity.selectBank(selectedBank);
        }
        if (effectiveIdempotencyKey != null) {
            orderEntity.assignIdempotencyKey(effectiveIdempotencyKey);
        }
        OrderEntity savedOrder;
        try {
            savedOrder = repository.saveAndFlush(orderEntity);
        } catch (DataIntegrityViolationException ex) {
            if (effectiveIdempotencyKey != null) {
                var concurrent = repository.findByIdempotencyKey(effectiveIdempotencyKey);
                if (concurrent.isPresent()) {
                    OrderEntity existing = concurrent.get();
                    if (!customerReference.equalsIgnoreCase(existing.getCustomerReference())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to another customer");
                    }
                    return mapper.toResponse(existing);
                }
            }
            throw ex;
        }
        OrderResponse response = mapper.toResponse(savedOrder);
        if (orderEventPublisher != null) {
            orderEventPublisher.publish(OrderCreatedEventEnvelope.orderCreated(orderEntity));
        }
        try {
            cartClient.clearCart(authentication);
        } catch (RuntimeException exception) {
            LOGGER.warn("Order created but cart could not be cleared");
        }
        return response;
    }

    @Transactional(readOnly = true)
    public OrderPricingResponse previewGuestPricing(PreviewGuestOrderPricingRequest request) {
        if (request == null || request.deliveryMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery method is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }
        syncDeliverySettings();
        List<OrderItemEntity> items = resolveGuestOrderItems(request.items());
        BigDecimal subtotal = items.stream()
                .map(OrderItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        ResolvedAddress resolvedAddress;
        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            resolvedAddress = new ResolvedAddress(null, null);
        } else {
            if (request.city() == null || request.city().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The delivery address has no city");
            }
            String city = request.city().trim();
            if (deliveryFeeProperties.calculateForDelivery(city, BigDecimal.ZERO) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery is not configured for this city");
            }
            resolvedAddress = new ResolvedAddress(null, city);
        }

        PreparedDelivery delivery = computeDeliveryFee(resolvedAddress, request.deliveryMethod(), subtotal);
        BigDecimal finalTotal = subtotal.add(delivery.fee()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal upfront = OrderEntity.calculateFirstInstallment(finalTotal);
        BigDecimal remaining = OrderEntity.calculateSecondInstallment(finalTotal, upfront);
        return new OrderPricingResponse(
                subtotal,
                delivery.fee(),
                finalTotal,
                upfront,
                remaining,
                delivery.freeDeliveryThreshold(),
                delivery.feesEnabled(),
                delivery.waivedReason(),
                delivery.area());
    }

    @Transactional
    public OrderResponse createGuestOrder(CreateGuestOrderRequest request, String headerIdempotencyKey) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guest order request is required");
        }
        String fullName = normalizeRequiredField(request.fullName(), "Le nom complet est obligatoire");
        String email = normalizeEmail(request.email());
        String phone = normalizePhone(request.phone());
        if (request.deliveryMethod() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery method is required");
        }
        validatePaymentMethodAndDelivery(request.paymentMethod(), request.deliveryMethod());

        String effectiveIdempotencyKey = resolveIdempotencyKey(headerIdempotencyKey, request.idempotencyKey());
        if (effectiveIdempotencyKey != null) {
            var existingByIdempotency = repository.findByIdempotencyKey(effectiveIdempotencyKey);
            if (existingByIdempotency != null && existingByIdempotency.isPresent()) {
                OrderEntity existingOrder = existingByIdempotency.get();
                if (!email.equalsIgnoreCase(existingOrder.getCustomerReference())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to another customer");
                }
                return mapper.toResponse(existingOrder);
            }
        }

        syncDeliverySettings();
        PaymentBankEntity selectedBank = validateBankTransfer(request.paymentMethod(), request.bankReference());
        List<OrderItemEntity> items = resolveGuestOrderItems(request.items());
        BigDecimal subtotal = items.stream()
                .map(OrderItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        ResolvedAddress resolvedAddress = resolveGuestDeliveryAddress(request, fullName, phone);
        PreparedDelivery delivery = computeDeliveryFee(resolvedAddress, request.deliveryMethod(), subtotal);

        var orderEntity = new OrderEntity(
                email,
                OrderStatus.DRAFT,
                delivery.shippingAddress(),
                request.deliveryMethod(),
                request.paymentMethod(),
                items,
                delivery.fee(),
                null,
                BigDecimal.ZERO.setScale(2),
                delivery.area(),
                delivery.waivedReason());

        if (selectedBank != null) {
            orderEntity.selectBank(selectedBank);
        } else if (orderEntity.requiresBankTransfer() && request.bankReference() != null && !request.bankReference().isBlank()) {
            orderEntity.selectBank(request.bankReference().trim());
        }

        TrackingTokenService.GeneratedTrackingToken generatedToken =
                trackingTokenService.generateToken(java.time.Instant.now());
        orderEntity.markAsGuestOrder(
                fullName,
                email,
                phone,
                generatedToken.tokenHash(),
                generatedToken.expiresAt());
        orderEntity.assignTrackingUrl(generatedToken.trackingUrl());
        if (effectiveIdempotencyKey != null) {
            orderEntity.assignIdempotencyKey(effectiveIdempotencyKey);
        }

        OrderEntity savedOrder;
        try {
            savedOrder = repository.saveAndFlush(orderEntity);
        } catch (DataIntegrityViolationException ex) {
            if (effectiveIdempotencyKey != null) {
                var concurrent = repository.findByIdempotencyKey(effectiveIdempotencyKey);
                if (concurrent.isPresent()) {
                    OrderEntity existing = concurrent.get();
                    if (!email.equalsIgnoreCase(existing.getCustomerReference())) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key belongs to another customer");
                    }
                    return mapper.toResponse(existing);
                }
            }
            throw ex;
        }
        if (orderEventPublisher != null) {
            orderEventPublisher.publish(
                    OrderCreatedEventEnvelope.orderCreated(savedOrder, generatedToken.trackingUrl()));
        }
        return mapper.toResponse(savedOrder).withTracking(generatedToken.rawToken(), generatedToken.trackingUrl());
    }

    @Transactional(readOnly = true)
    public PublicOrderTrackingResponse trackOrderByToken(String rawToken) {
        OrderEntity order = findValidOrderByTrackingToken(rawToken, false);
        return toPublicTrackingResponse(order);
    }

    @Transactional
    public PublicOrderTrackingResponse uploadGuestTransferProofByToken(
            String rawToken, org.springframework.web.multipart.MultipartFile file) {
        return uploadGuestTransferProofByToken(rawToken, file, null);
    }

    @Transactional
    public PublicOrderTrackingResponse uploadGuestTransferProofByToken(
            String rawToken, org.springframework.web.multipart.MultipartFile file, Integer installment) {
        OrderEntity order = findValidOrderByTrackingToken(rawToken, true);
        if (!order.requiresBankTransfer()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transfer proof is only valid for bank transfer orders");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot submit transfer proof for a cancelled order");
        }

        boolean isSecondInstallment = (installment != null && installment == 2)
                || (installment == null && order.getPaymentPlan() == PaymentPlan.SPLIT_50_50
                        && order.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID);

        if (isSecondInstallment) {
            if (order.getPaymentPlan() != PaymentPlan.SPLIT_50_50) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Second installment transfer proof is only valid for SPLIT_50_50 orders"
                );
            }
            if (order.getPaymentStatus() == PaymentStatus.UNPAID) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "First installment must be confirmed before uploading second transfer proof"
                );
            }
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order payment has already been fully completed"
                );
            }
            String reference = transferProofStorage.store(order.getId(), file);
            order.attachSecondTransferProof(reference);
            return toPublicTrackingResponse(order);
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Order payment has already been fully completed"
            );
        }
        String reference = transferProofStorage.store(order.getId(), file);
        order.attachTransferProof(reference);
        return toPublicTrackingResponse(order);
    }

    @Transactional
    public ClaimGuestOrdersResponse claimGuestOrders(JwtAuthenticationToken authentication) {
        String verifiedEmail = customerReference(authentication).trim().toLowerCase(java.util.Locale.ROOT);
        String verifiedSubject = subject(authentication);
        if (!isEmailVerified(authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Votre adresse e-mail doit être vérifiée pour rattacher vos commandes invitées.");
        }
        List<OrderEntity> unclaimed = repository.findUnclaimedGuestOrdersForUpdateByEmail(verifiedEmail);
        if (unclaimed == null || unclaimed.isEmpty()) {
            return new ClaimGuestOrdersResponse(0, List.of(), List.of());
        }
        java.time.Instant now = java.time.Instant.now();
        List<UUID> linkedIds = new ArrayList<>();
        List<String> linkedNumbers = new ArrayList<>();
        for (OrderEntity order : unclaimed) {
            if (order != null && order.isGuestOrder() && order.getCustomerSubject() == null) {
                String refEmail = order.getCustomerReference() != null ? order.getCustomerReference().trim().toLowerCase(java.util.Locale.ROOT) : "";
                String contactEmail = order.getContactEmail() != null ? order.getContactEmail().trim().toLowerCase(java.util.Locale.ROOT) : "";
                if (verifiedEmail.equalsIgnoreCase(refEmail) || verifiedEmail.equalsIgnoreCase(contactEmail)) {
                    if (order.linkToAccount(verifiedSubject, now)) {
                        linkedIds.add(order.getId());
                        linkedNumbers.add(order.getOrderNumber());
                    }
                }
            }
        }
        if (!linkedIds.isEmpty()) {
            repository.saveAllAndFlush(unclaimed);
        }
        return new ClaimGuestOrdersResponse(linkedIds.size(), linkedIds, linkedNumbers);
    }

    @Transactional
    public void revokeGuestTrackingToken(UUID id) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.revokeTrackingToken();
    }

    @Transactional
    public String regenerateGuestTrackingToken(UUID id) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        TrackingTokenService.GeneratedTrackingToken generated =
                trackingTokenService.generateToken(java.time.Instant.now());
        order.rotateTrackingToken(generated.tokenHash(), generated.expiresAt());
        order.assignTrackingUrl(generated.trackingUrl());
        return generated.trackingUrl();
    }

    @Transactional(readOnly = true)
    public OrderPricingResponse previewPricing(JwtAuthenticationToken authentication,
            PreviewOrderPricingRequest request) {
        String customerReference = customerReference(authentication);
        syncDeliverySettings();
        if (request.quotationId() != null) {
            requireClientForQuotation(authentication);
            QuotationSnapshot quotation = acceptedQuotation(authentication, request.quotationId(), customerReference);
            PreparedDelivery delivery = prepareDelivery(
                    authentication,
                    request.addressId(),
                    request.deliveryMethod(),
                    quotation.subtotal());
            BigDecimal finalTotal = quotation.subtotal()
                    .subtract(quotation.discount())
                    .add(delivery.fee())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal upfront = OrderEntity.calculateFirstInstallment(finalTotal);
            BigDecimal remaining = OrderEntity.calculateSecondInstallment(finalTotal, upfront);
            return new OrderPricingResponse(
                    quotation.subtotal(),
                    delivery.fee(),
                    finalTotal,
                    upfront,
                    remaining,
                    delivery.freeDeliveryThreshold(),
                    delivery.feesEnabled(),
                    delivery.waivedReason(),
                    delivery.area());
        }
        PreparedOrder prepared = prepare(authentication, request.addressId(), request.deliveryMethod());
        BigDecimal subtotal = prepared.subtotal();
        BigDecimal finalTotal = subtotal.add(prepared.deliveryFee()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal upfront = OrderEntity.calculateFirstInstallment(finalTotal);
        BigDecimal remaining = OrderEntity.calculateSecondInstallment(finalTotal, upfront);
        return new OrderPricingResponse(
                subtotal,
                prepared.deliveryFee(),
                finalTotal,
                upfront,
                remaining,
                prepared.freeDeliveryThreshold(),
                prepared.feesEnabled(),
                prepared.deliveryFeeWaivedReason(),
                prepared.deliveryArea());
    }

    private PreparedOrder prepare(JwtAuthenticationToken authentication, UUID addressId,
            DeliveryMethod deliveryMethod) {
        ResolvedAddress resolvedAddress = resolveDeliveryAddress(authentication, addressId, deliveryMethod);
        CartClient.Cart cart = cartClient.getCart(authentication);
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }
        List<OrderItemEntity> items = new ArrayList<>();
        for (CartClient.CartItem cartItem : cart.items()) {
            if (cartItem == null || cartItem.productReference() == null || cartItem.productReference().isBlank()
                    || cartItem.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart contains invalid items");
            }
            ProductPricingClient.ProductPrice price = productPricingClient.getPrice(
                    authentication, cartItem.productReference());

            LOGGER.info(
                    "Checkout stock validation productReference={} cartQuantity={} availableQuantity={} unitPrice={}",
                    cartItem.productReference(),
                    cartItem.quantity(),
                    price.availableQuantity(),
                    price.unitPrice());

            if (price.availableQuantity() != null && cartItem.quantity() > price.availableQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Available quantity exceeded: " + price.availableQuantity());
            }
            items.add(new OrderItemEntity(
                    price.productReference(), price.productName(), cartItem.quantity(), price.unitPrice()));
        }
        BigDecimal subtotal = items.stream()
                .map(OrderItemEntity::getSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        PreparedDelivery delivery = computeDeliveryFee(resolvedAddress, deliveryMethod, subtotal);
        return new PreparedOrder(
                delivery.shippingAddress(),
                delivery.fee(),
                items,
                subtotal,
                delivery.area(),
                delivery.waivedReason(),
                delivery.feesEnabled(),
                delivery.freeDeliveryThreshold());
    }

    private PreparedDelivery prepareDelivery(JwtAuthenticationToken authentication, UUID addressId,
            DeliveryMethod deliveryMethod, BigDecimal authoritativeSubtotal) {
        ResolvedAddress resolvedAddress = resolveDeliveryAddress(authentication, addressId, deliveryMethod);
        return computeDeliveryFee(resolvedAddress, deliveryMethod, authoritativeSubtotal);
    }

    private ResolvedAddress resolveDeliveryAddress(JwtAuthenticationToken authentication, UUID addressId,
            DeliveryMethod deliveryMethod) {
        if (deliveryMethod == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery method is required");
        }
        if (deliveryMethod == DeliveryMethod.PICKUP) {
            return new ResolvedAddress(null, null);
        }
        if (addressId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A delivery address is required");
        }
        UserAddressClient.Address address = userAddressClient.getAddress(authentication, addressId);
        if (address.city() == null || address.city().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The delivery address has no city");
        }
        if (deliveryFeeProperties.calculateForDelivery(address.city(), BigDecimal.ZERO) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery is not configured for this city");
        }
        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshot(
                address.firstName(), address.lastName(), address.company(), address.phone(),
                address.addressLine1(), address.addressLine2(), address.city(),
                address.postalCode(), address.country());
        return new ResolvedAddress(snapshot, address.city());
    }

    private PreparedDelivery computeDeliveryFee(ResolvedAddress resolvedAddress, DeliveryMethod deliveryMethod,
            BigDecimal authoritativeSubtotal) {
        BigDecimal threshold = deliveryFeeProperties.getFreeDeliveryThreshold() != null
                ? deliveryFeeProperties.getFreeDeliveryThreshold()
                : DeliveryFeeProperties.DEFAULT_FREE_DELIVERY_THRESHOLD;
        boolean feesEnabled = deliveryFeeProperties.isFeesEnabled();
        if (deliveryMethod == DeliveryMethod.PICKUP) {
            return new PreparedDelivery(
                    null,
                    BigDecimal.ZERO.setScale(2),
                    DeliveryFeeProperties.AREA_PICKUP,
                    DeliveryFeeProperties.WAIVED_PICKUP,
                    feesEnabled,
                    threshold);
        }
        DeliveryFeeProperties.DeliveryCalculation calc = deliveryFeeProperties
                .calculateForDelivery(resolvedAddress.city(), authoritativeSubtotal);
        if (calc == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery is not configured for this city");
        }
        return new PreparedDelivery(
                resolvedAddress.snapshot(),
                calc.fee(),
                calc.area(),
                calc.waivedReason(),
                calc.feesEnabled(),
                calc.freeDeliveryThreshold());
    }

    private record ResolvedAddress(ShippingAddressSnapshot snapshot, String city) { }

    private record PreparedDelivery(
            ShippingAddressSnapshot shippingAddress,
            BigDecimal fee,
            String area,
            String waivedReason,
            boolean feesEnabled,
            BigDecimal freeDeliveryThreshold) { }

    private record PreparedOrder(
            ShippingAddressSnapshot shippingAddress,
            BigDecimal deliveryFee,
            List<OrderItemEntity> items,
            BigDecimal subtotal,
            String deliveryArea,
            String deliveryFeeWaivedReason,
            boolean feesEnabled,
            BigDecimal freeDeliveryThreshold) {
    }

    private OrderResponse createFromQuotation(JwtAuthenticationToken authentication, CreateOrderRequest request,
            String customerReference) {
        requireClientForQuotation(authentication);
        UUID quotationId = request.quotationId();
        var existing = repository.findByQuotationIdAndCustomerReference(quotationId, customerReference);
        if (existing.isPresent()) {
            return mapper.toResponse(existing.get());
        }
        validatePaymentSelection(request);
        QuotationSnapshot quotation = acceptedQuotation(authentication, quotationId, customerReference);
        PaymentBankEntity selectedBank = validateBankTransfer(request);
        PreparedDelivery delivery = prepareDelivery(
                authentication,
                request.addressId(),
                request.deliveryMethod(),
                quotation.subtotal());
        try {
            return quotationTransaction.execute(status -> {
                var alreadyCreated = repository.findByQuotationIdAndCustomerReference(quotationId, customerReference);
                if (alreadyCreated.isPresent()) {
                    return mapper.toResponse(alreadyCreated.get());
                }
                List<OrderItemEntity> items = quotation.lines().stream()
                        .map(line -> new OrderItemEntity(
                                line.productReference(), line.proposedQuantity(), line.proposedUnitPrice()))
                        .toList();
                var order = new OrderEntity(
                        customerReference,
                        OrderStatus.DRAFT,
                        delivery.shippingAddress(),
                        request.deliveryMethod(),
                        request.paymentMethod(),
                        items,
                        delivery.fee(),
                        quotationId,
                        quotation.discount(),
                        delivery.area(),
                        delivery.waivedReason());
                if (!isStaff(authentication)) {
                    order.assignCustomerSubject(subject(authentication));
                }
                if (selectedBank != null) {
                    order.selectBank(selectedBank);
                }
                OrderResponse response = mapper.toResponse(repository.saveAndFlush(order));
                if (orderEventPublisher != null) {
                    orderEventPublisher.publish(OrderCreatedEventEnvelope.orderCreated(order));
                }
                return response;
            });
        } catch (DataIntegrityViolationException exception) {
            return repository.findByQuotationIdAndCustomerReference(quotationId, customerReference)
                    .map(mapper::toResponse)
                    .orElseThrow(() -> exception);
        }
    }

    private QuotationSnapshot acceptedQuotation(JwtAuthenticationToken authentication, UUID quotationId,
            String customerReference) {
        if (discoveryClient == null || restClientBuilder == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quotation service unavailable");
        }
        var instance = discoveryClient.getInstances("quotation-service").stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Quotation service unavailable"));
        QuotationSnapshot quotation;
        try {
            quotation = restClientBuilder.clone().baseUrl(instance.getUri().toString()).build()
                    .get().uri("/api/quotations/{id}", quotationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                    .retrieve().body(QuotationSnapshot.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 403 || exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quotation service unavailable");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quotation service unavailable");
        }
        if (quotation == null || !quotationId.equals(quotation.id())
                || quotation.customerReference() == null
                || !customerReference.equalsIgnoreCase(quotation.customerReference())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found");
        }
        if (!"ACCEPTED".equals(quotation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation has not been accepted");
        }
        if (quotation.lines() == null || quotation.lines().isEmpty() || quotation.subtotal() == null
                || quotation.discount() == null || quotation.discount().signum() < 0
                || quotation.discount().compareTo(quotation.subtotal()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation has no valid commercial lines");
        }
        try {
            quotation.discount().setScale(2, RoundingMode.UNNECESSARY);
            quotation.subtotal().setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation price precision is invalid");
        }
        var productReferences = new HashSet<String>();
        BigDecimal computedSubtotal = BigDecimal.ZERO.setScale(2);
        for (QuotationLine line : quotation.lines()) {
            if (line == null || line.productReference() == null || line.productReference().isBlank()
                    || !productReferences.add(line.productReference()) || line.proposedQuantity() == null
                    || line.proposedQuantity() < 1 || line.proposedUnitPrice() == null
                    || line.proposedUnitPrice().signum() < 0 || line.proposedLineTotal() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation has invalid commercial lines");
            }
            try {
                BigDecimal unitPrice = line.proposedUnitPrice().setScale(2, RoundingMode.UNNECESSARY);
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(line.proposedQuantity()));
                if (lineTotal.compareTo(line.proposedLineTotal()) != 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation line total is inconsistent");
                }
                computedSubtotal = computedSubtotal.add(lineTotal);
            } catch (ArithmeticException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation price precision is invalid");
            }
        }
        if (computedSubtotal.compareTo(quotation.subtotal()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation subtotal is inconsistent");
        }
        return quotation;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record QuotationSnapshot(UUID id, String customerReference, String status,
            List<QuotationLine> lines, BigDecimal subtotal, BigDecimal discount) { }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record QuotationLine(String productReference, Integer proposedQuantity,
            BigDecimal proposedUnitPrice, BigDecimal proposedLineTotal) { }

    private void requireClientForQuotation(JwtAuthenticationToken authentication) {
        if (authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_CLIENT".equals(authority.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client access required for quotation checkout");
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentBankResponse> paymentBanks() {
        if (paymentBankApplicationService != null) {
            return paymentBankApplicationService.listActiveBanks();
        }
        return List.of();
    }

    @Transactional
    public OrderResponse uploadTransferProof(JwtAuthenticationToken authentication, UUID id,
            org.springframework.web.multipart.MultipartFile file) {
        return uploadTransferProof(authentication, id, file, null);
    }

    @Transactional
    public OrderResponse uploadTransferProof(JwtAuthenticationToken authentication, UUID id,
            org.springframework.web.multipart.MultipartFile file, Integer installment) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        if (!isStaff(authentication) && !isOwnedByCustomer(order, authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This order does not belong to the authenticated customer");
        }
        if (!order.requiresBankTransfer()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Transfer proof is only valid for bank transfer orders"
            );
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot submit transfer proof for a cancelled order"
            );
        }

        boolean isSecondInstallment = (installment != null && installment == 2)
                || (installment == null && order.getPaymentPlan() == PaymentPlan.SPLIT_50_50
                        && order.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID);

        if (isSecondInstallment) {
            if (order.getPaymentPlan() != PaymentPlan.SPLIT_50_50) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Second installment transfer proof is only valid for SPLIT_50_50 orders"
                );
            }
            if (order.getPaymentStatus() == PaymentStatus.UNPAID) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "First installment must be confirmed before uploading second transfer proof"
                );
            }
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Order payment has already been fully completed"
                );
            }
            String reference = transferProofStorage.store(id, file);
            order.attachSecondTransferProof(reference);
            return mapper.toResponse(order);
        }

        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Order payment has already been fully completed"
            );
        }
        String reference = transferProofStorage.store(id, file);
        order.attachTransferProof(reference);
        return mapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<org.springframework.core.io.Resource> getTransferProof(
            JwtAuthenticationToken authentication, UUID id, boolean download) {
        return getTransferProof(authentication, id, download, 1);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<org.springframework.core.io.Resource> getTransferProof(
            JwtAuthenticationToken authentication, UUID id, boolean download, int installment) {
        OrderEntity order = repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        if (!isStaff(authentication) && !isOwnedByCustomer(order, authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This order does not belong to the authenticated customer");
        }
        String proofRef = installment == 2 ? order.getSecondTransferProofReference() : order.getTransferProofReference();
        if (proofRef == null || proofRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    installment == 2 ? "No second transfer proof associated with this order" : "No transfer proof associated with this order");
        }
        org.springframework.core.io.Resource resource = transferProofStorage.loadAsResource(proofRef);
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer proof file could not be found");
        }
        String filename = resource.getFilename();
        String mediaType = org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
        String ext = "jpg";
        if (filename != null) {
            String lower = filename.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".png")) {
                mediaType = org.springframework.http.MediaType.IMAGE_PNG_VALUE;
                ext = "png";
            } else if (lower.endsWith(".webp")) {
                mediaType = "image/webp";
                ext = "webp";
            }
        }
        String prefix = installment == 2 ? "recu-solde-virement-" : "recu-virement-";
        String downloadName = prefix + (order.getOrderNumber() != null ? order.getOrderNumber() : id) + "." + ext;
        var disposition = download
                ? org.springframework.http.ContentDisposition.attachment().filename(downloadName).build()
                : org.springframework.http.ContentDisposition.inline().filename(downloadName).build();

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(mediaType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(resource);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<org.springframework.core.io.Resource> getGuestTransferProofByToken(
            String token, boolean download) {
        return getGuestTransferProofByToken(token, download, 1);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<org.springframework.core.io.Resource> getGuestTransferProofByToken(
            String token, boolean download, int installment) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tracking token is required");
        }
        OrderEntity order = findValidOrderByTrackingToken(token, false);
        String proofRef = installment == 2 ? order.getSecondTransferProofReference() : order.getTransferProofReference();
        if (proofRef == null || proofRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    installment == 2 ? "No second transfer proof associated with this order" : "No transfer proof associated with this order");
        }
        org.springframework.core.io.Resource resource = transferProofStorage.loadAsResource(proofRef);
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer proof file could not be found");
        }
        String filename = resource.getFilename();
        String mediaType = org.springframework.http.MediaType.IMAGE_JPEG_VALUE;
        String ext = "jpg";
        if (filename != null) {
            String lower = filename.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".png")) {
                mediaType = org.springframework.http.MediaType.IMAGE_PNG_VALUE;
                ext = "png";
            } else if (lower.endsWith(".webp")) {
                mediaType = "image/webp";
                ext = "webp";
            }
        }
        String prefix = installment == 2 ? "recu-solde-virement-" : "recu-virement-";
        String downloadName = prefix + (order.getOrderNumber() != null ? order.getOrderNumber() : order.getId()) + "." + ext;
        var disposition = download
                ? org.springframework.http.ContentDisposition.attachment().filename(downloadName).build()
                : org.springframework.http.ContentDisposition.inline().filename(downloadName).build();

        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(mediaType))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(resource);
    }

    private void validatePaymentSelection(CreateOrderRequest request) {
        validatePaymentMethodAndDelivery(
                request != null ? request.paymentMethod() : null,
                request != null ? request.deliveryMethod() : null);
    }

    private void validatePaymentMethodAndDelivery(PaymentMethod paymentMethod, DeliveryMethod deliveryMethod) {
        if (paymentMethod == null
                || paymentMethod == PaymentMethod.ONLINE_PAYMENT
                || paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment method is not supported");
        }
        if (paymentMethod == PaymentMethod.PAY_ON_PICKUP && deliveryMethod != DeliveryMethod.PICKUP) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pay on pickup is only available for pickup orders");
        }
    }

    private PaymentBankEntity validateBankTransfer(CreateOrderRequest request) {
        return validateBankTransfer(request.paymentMethod(), request.bankReference());
    }

    private PaymentBankEntity validateBankTransfer(PaymentMethod paymentMethod, String bankReference) {
        if (paymentMethod != PaymentMethod.BANK_TRANSFER
                && paymentMethod != PaymentMethod.SPLIT_50_50) {
            return null;
        }
        if (bankReference == null || bankReference.isBlank()) {
            if (paymentMethod == PaymentMethod.SPLIT_50_50 && bankReference == null) {
                return null;
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A bank is required for bank transfer");
        }
        if (paymentBankRepository == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Payment banks repository is not configured");
        }
        return paymentBankRepository.findByReferenceAndActiveTrue(bankReference.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "The selected bank is not configured or is inactive"));
    }

    @Transactional
    public OrderResponse getById(JwtAuthenticationToken authentication, UUID id) {
        if (isStaff(authentication)) {
            return repository.findById(id)
                    .map(mapper::toResponse)
                    .orElseThrow(() -> new OrderNotFoundException(id));
        }
        String customerReference = customerReference(authentication);
        String jwtSubject = optionalSubject(authentication);
        if (jwtSubject != null) {
            var owned = repository.findOwnedOrderById(id, jwtSubject, customerReference);
            if (owned != null && owned.isPresent()) {
                return mapper.toResponse(owned.get());
            }
            if (isEmailVerified(authentication)) {
                var candidateOpt = repository.findById(id);
                if (candidateOpt.isPresent()) {
                    OrderEntity candidate = candidateOpt.get();
                    if (candidate.isGuestOrder() && candidate.getCustomerSubject() == null) {
                        String normEmail = customerReference.trim().toLowerCase(java.util.Locale.ROOT);
                        String refEmail = candidate.getCustomerReference() != null ? candidate.getCustomerReference().trim().toLowerCase(java.util.Locale.ROOT) : "";
                        String contactEmail = candidate.getContactEmail() != null ? candidate.getContactEmail().trim().toLowerCase(java.util.Locale.ROOT) : "";
                        if (normEmail.equalsIgnoreCase(refEmail) || normEmail.equalsIgnoreCase(contactEmail)) {
                            candidate.linkToAccount(jwtSubject, java.time.Instant.now());
                            repository.saveAndFlush(candidate);
                            return mapper.toResponse(candidate);
                        }
                    }
                }
            }
        }
        return repository.findByIdAndCustomerReference(id, customerReference)
                .filter(order -> isOwnedByCustomer(order, authentication))
                .map(mapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    public OrderResponse cancel(JwtAuthenticationToken authentication, UUID id) {
        String customerReference = customerReference(authentication);
        String jwtSubject = optionalSubject(authentication);
        java.util.Optional<OrderEntity> candidate = java.util.Optional.empty();
        if (jwtSubject != null) {
            var owned = repository.findOwnedOrderForUpdateById(id, jwtSubject, customerReference);
            if (owned != null && owned.isPresent()) {
                candidate = owned;
            }
        }
        if (candidate.isEmpty()) {
            candidate = repository.findForUpdateByIdAndCustomerReference(id, customerReference)
                    .filter(o -> isOwnedByCustomer(o, authentication));
        }
        var order = candidate.orElseThrow(() -> new OrderNotFoundException(id));
        try {
            order.cancel();
        } catch (IllegalStateException exception) {
            throw new OrderNotCancellableException(id);
        }
        if (orderEventPublisher != null) {
            ensureGuestTrackingUrl(order);
            orderEventPublisher.publish(OrderStatusChangedEventEnvelope.orderStatusChanged(order, OrderStatus.DRAFT));
        }
        return mapper.toResponse(order);
    }

    @Transactional
    public OrderResponse confirmPayment(UUID id) {
        return confirmPayment(id, null, null);
    }

    @Transactional
    public OrderResponse confirmPayment(UUID id, BigDecimal explicitAmount) {
        return confirmPayment(id, explicitAmount, null);
    }

    @Transactional
    public OrderResponse confirmPayment(UUID id, BigDecimal explicitAmount, String confirmedBy) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        PaymentStatus previousPaymentStatus = order.getPaymentStatus();
        try {
            order.confirmPayment(explicitAmount, confirmedBy);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new PaymentConfirmationException(id);
        }
        if (orderEventPublisher != null) {
            ensureGuestTrackingUrl(order);
            orderEventPublisher.publish(
                    OrderStatusChangedEventEnvelope.paymentStatusChanged(order, previousPaymentStatus));
        }
        return mapper.toResponse(order);
    }

    @Transactional
    public OrderResponse selectSecondInstallmentMethod(JwtAuthenticationToken authentication, UUID id, String method) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (!isStaff(authentication) && !isOwnedByCustomer(order, authentication)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This order does not belong to the authenticated customer");
        }

        try {
            order.selectSecondInstallmentMethod(method);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }

        return mapper.toResponse(order);
    }

    @Transactional
    public PublicOrderTrackingResponse selectGuestSecondInstallmentMethodByToken(String token, String method) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A tracking token is required");
        }
        OrderEntity order = findValidOrderByTrackingToken(token, false);

        try {
            order.selectSecondInstallmentMethod(method);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }

        return toPublicTrackingResponse(order);
    }

    @Transactional
    public OrderResponse confirm(UUID id) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        try {
            order.confirm();
        } catch (IllegalStateException exception) {
            throw new OrderConfirmationException(id);
        }
        if (orderEventPublisher != null) {
            ensureGuestTrackingUrl(order);
            orderEventPublisher.publish(OrderStatusChangedEventEnvelope.orderStatusChanged(order, OrderStatus.DRAFT));
        }
        return mapper.toResponse(order);
    }

    @Transactional
    public OrderResponse process(UUID id) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        try {
            order.startProcessing();
        } catch (IllegalStateException exception) {
            throw new OrderProcessingException(id);
        }
        if (orderEventPublisher != null) {
            ensureGuestTrackingUrl(order);
            orderEventPublisher.publish(OrderStatusChangedEventEnvelope.orderStatusChanged(order, OrderStatus.CONFIRMED));
        }
        return mapper.toResponse(order);
    }

    @Transactional
    public OrderResponse markOrderReady(UUID id) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        try {
            order.markReady();
        } catch (IllegalStateException exception) {
            throw new OrderFulfillmentException(id, "marked ready");
        }
        if (orderEventPublisher != null) {
            ensureGuestTrackingUrl(order);
            orderEventPublisher.publish(OrderStatusChangedEventEnvelope.orderStatusChanged(order, OrderStatus.PROCESSING));
        }
        return mapper.toResponse(order);
    }

    @Transactional
    public OrderResponse completeOrder(UUID id) {
        var order = repository.findForUpdateById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        try {
            order.complete();
        } catch (IllegalStateException exception) {
            throw new OrderFulfillmentException(id, "completed");
        }
        if (orderEventPublisher != null) {
            ensureGuestTrackingUrl(order);
            orderEventPublisher.publish(OrderStatusChangedEventEnvelope.orderStatusChanged(order, OrderStatus.READY));
        }
        return mapper.toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional
    public Page<OrderResponse> getMyOrders(JwtAuthenticationToken authentication, Pageable pageable) {
        String customerReference = customerReference(authentication);
        String jwtSubject = optionalSubject(authentication);
        if (jwtSubject != null && isEmailVerified(authentication)) {
            String normEmail = customerReference.trim().toLowerCase(java.util.Locale.ROOT);
            List<OrderEntity> unclaimed = repository.findUnclaimedGuestOrdersForUpdateByEmail(normEmail);
            if (unclaimed != null && !unclaimed.isEmpty()) {
                java.time.Instant now = java.time.Instant.now();
                boolean anyLinked = false;
                for (OrderEntity guestOrder : unclaimed) {
                    if (guestOrder != null && guestOrder.isGuestOrder() && guestOrder.getCustomerSubject() == null) {
                        String refEmail = guestOrder.getCustomerReference() != null ? guestOrder.getCustomerReference().trim().toLowerCase(java.util.Locale.ROOT) : "";
                        String contactEmail = guestOrder.getContactEmail() != null ? guestOrder.getContactEmail().trim().toLowerCase(java.util.Locale.ROOT) : "";
                        if (normEmail.equalsIgnoreCase(refEmail) || normEmail.equalsIgnoreCase(contactEmail)) {
                            if (guestOrder.linkToAccount(jwtSubject, now)) {
                                anyLinked = true;
                            }
                        }
                    }
                }
                if (anyLinked) {
                    repository.saveAllAndFlush(unclaimed);
                }
            }
        }
        int pageSize = Math.min(pageable.getPageSize(), 20);
        Pageable boundedPage = PageRequest.of(
                pageable.getPageNumber(),
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        if (jwtSubject != null) {
            Page<OrderEntity> ownedPage = repository.findOwnedOrders(jwtSubject, customerReference, boundedPage);
            if (ownedPage != null) {
                return ownedPage.map(mapper::toResponse);
            }
        }
        Page<OrderEntity> byRefPage = repository.findByCustomerReferenceOrderByCreatedAtDesc(customerReference, boundedPage);
        if (byRefPage == null) {
            return Page.empty(boundedPage);
        }
        List<OrderResponse> filtered = byRefPage.getContent().stream()
                .filter(order -> isOwnedByCustomer(order, authentication))
                .map(mapper::toResponse)
                .toList();
        return new org.springframework.data.domain.PageImpl<>(filtered, boundedPage, filtered.size());
    }

    private List<OrderItemEntity> resolveGuestOrderItems(List<GuestOrderItemRequest> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }
        List<OrderItemEntity> items = new ArrayList<>();
        for (GuestOrderItemRequest item : requestedItems) {
            if (item == null || item.productReference() == null || item.productReference().isBlank()
                    || item.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart contains invalid items");
            }
            String ref = item.productReference().trim();
            ProductPricingClient.ProductPrice price = productPricingClient.getPublicPrice(ref);
            if (price == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid product pricing response");
            }
            if (price.availableQuantity() != null && item.quantity() > price.availableQuantity()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Available quantity exceeded: " + price.availableQuantity());
            }
            items.add(new OrderItemEntity(
                    price.productReference(),
                    price.productName(),
                    item.quantity(),
                    price.unitPrice()));
        }
        return items;
    }

    private ResolvedAddress resolveGuestDeliveryAddress(
            CreateGuestOrderRequest request, String fullName, String phone) {
        String[] nameParts = splitFullName(fullName);
        String company = trimOrNull(request.company());
        if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
            ShippingAddressSnapshot pickupContact = new ShippingAddressSnapshot(
                    nameParts[0],
                    nameParts[1],
                    company,
                    phone,
                    null,
                    null,
                    null,
                    null,
                    "Maroc");
            return new ResolvedAddress(pickupContact, null);
        }
        String city = normalizeRequiredField(request.city(), "La ville de livraison est obligatoire");
        String addressLine1 = normalizeRequiredField(request.addressLine1(), "L'adresse de livraison est obligatoire");
        if (deliveryFeeProperties.calculateForDelivery(city, BigDecimal.ZERO) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery is not configured for this city");
        }
        String addressLine2 = trimOrNull(request.addressLine2());
        String postalCode = trimOrNull(request.postalCode());
        String country = trimOrNull(request.country());
        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshot(
                nameParts[0],
                nameParts[1],
                company,
                phone,
                addressLine1,
                addressLine2,
                city,
                postalCode,
                country != null ? country : "Maroc");
        return new ResolvedAddress(snapshot, city);
    }

    private OrderEntity findValidOrderByTrackingToken(String rawToken, boolean forUpdate) {
        String tokenHash;
        try {
            tokenHash = trackingTokenService.hashToken(rawToken);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien de suivi invalide ou expiré");
        }
        OrderEntity order = repository.findByTrackingTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien de suivi invalide ou expiré"));
        if (!order.isTrackingTokenValid(tokenHash, java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien de suivi invalide ou expiré");
        }
        if (forUpdate) {
            return repository.findForUpdateById(order.getId())
                    .filter(locked -> locked.isTrackingTokenValid(tokenHash, java.time.Instant.now()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien de suivi invalide ou expiré"));
        }
        return order;
    }

    private PublicOrderTrackingResponse toPublicTrackingResponse(OrderEntity order) {
        BigDecimal safeTotal = order.getTotal() == null ? BigDecimal.ZERO.setScale(2) : order.getTotal();
        BigDecimal safeFee = order.getDeliveryFee() == null ? BigDecimal.ZERO.setScale(2) : order.getDeliveryFee();
        BigDecimal safeDiscount = order.getDiscount() == null ? BigDecimal.ZERO.setScale(2) : order.getDiscount();
        BigDecimal subtotal = safeTotal.subtract(safeFee).add(safeDiscount);

        String shippingCity = order.getShippingAddress() != null ? order.getShippingAddress().city() : null;
        String recipientFirstName = null;
        if (order.getContactFullName() != null && !order.getContactFullName().isBlank()) {
            recipientFirstName = splitFullName(order.getContactFullName())[0];
        } else if (order.getShippingAddress() != null && order.getShippingAddress().firstName() != null) {
            recipientFirstName = order.getShippingAddress().firstName();
        }

        List<PublicOrderTrackingResponse.PublicOrderTrackingItemResponse> items = order.getItems().stream()
                .map(item -> new PublicOrderTrackingResponse.PublicOrderTrackingItemResponse(
                        item.getProductReference(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()))
                .toList();

        return new PublicOrderTrackingResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethod(),
                order.getPaymentPlan(),
                order.getDeliveryMethod(),
                order.getDeliveryArea(),
                order.getDeliveryFeeWaivedReason(),
                subtotal,
                safeDiscount,
                safeFee,
                safeTotal,
                order.getAmountPaid(),
                order.getUpfrontPaymentAmount(),
                order.getRemainingDeliveryAmount(),
                order.getOutstandingBalance(),
                order.getPaymentBankName(),
                order.getPaymentBankRib(),
                order.getPaymentBankBeneficiary(),
                order.getPaymentBankLogoUrl(),
                order.getTransferProofReference() != null && !order.getTransferProofReference().isBlank(),
                order.getSecondTransferProofReference() != null && !order.getSecondTransferProofReference().isBlank(),
                order.getSecondInstallmentPaymentMethod(),
                order.getFirstInstallmentConfirmedAt(),
                order.getSecondInstallmentConfirmedAt(),
                order.getSecondTransferProofSubmittedAt(),
                shippingCity,
                recipientFirstName,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items);
    }

    private boolean isOwnedByCustomer(OrderEntity order, JwtAuthenticationToken authentication) {
        if (order == null || authentication == null || authentication.getToken() == null) {
            return false;
        }
        String email = customerReference(authentication).trim().toLowerCase(java.util.Locale.ROOT);
        String jwtSubject = optionalSubject(authentication);
        if (order.getCustomerSubject() != null && !order.getCustomerSubject().isBlank()) {
            return jwtSubject != null && jwtSubject.equals(order.getCustomerSubject());
        }
        String refEmail = order.getCustomerReference() != null ? order.getCustomerReference().trim().toLowerCase(java.util.Locale.ROOT) : "";
        String contactEmail = order.getContactEmail() != null ? order.getContactEmail().trim().toLowerCase(java.util.Locale.ROOT) : "";
        if (order.isGuestOrder()) {
            return isEmailVerified(authentication) && (email.equalsIgnoreCase(refEmail) || email.equalsIgnoreCase(contactEmail));
        }
        return email.equalsIgnoreCase(refEmail);
    }

    private boolean isEmailVerified(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            return false;
        }
        Object verified = null;
        try {
            verified = authentication.getToken().getClaimAsBoolean("email_verified");
        } catch (Exception ignored) {
        }
        if (verified == null && authentication.getToken().getClaims() != null) {
            verified = authentication.getToken().getClaims().get("email_verified");
        }
        if (verified instanceof Boolean b) {
            return b;
        }
        if (verified instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        return false;
    }

    private String optionalSubject(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            return null;
        }
        String sub = authentication.getToken().getSubject();
        return sub != null && !sub.isBlank() ? sub : null;
    }

    private String resolveIdempotencyKey(String headerKey, String bodyKey) {
        String candidate = headerKey != null && !headerKey.isBlank() ? headerKey.trim()
                : (bodyKey != null && !bodyKey.isBlank() ? bodyKey.trim() : null);
        if (candidate == null) {
            return null;
        }
        return candidate.length() > 128 ? candidate.substring(0, 128) : candidate;
    }

    private String normalizeRequiredField(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'adresse e-mail est obligatoire");
        }
        String trimmed = email.trim();
        if (trimmed.length() > 320 || !EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresse e-mail invalide");
        }
        return trimmed;
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le numéro de téléphone est obligatoire");
        }
        String trimmed = phone.trim();
        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 8 || digitsOnly.length() > 15) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Numéro de téléphone invalide");
        }
        return trimmed;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String[] splitFullName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx <= 0) {
            return new String[]{trimmed, "-"};
        }
        String first = trimmed.substring(0, spaceIdx).trim();
        String last = trimmed.substring(spaceIdx + 1).trim();
        return new String[]{first, last.isEmpty() ? "-" : last};
    }

    private void syncDeliverySettings() {
        if (deliverySettingsService != null) {
            deliverySettingsService.syncFromRepositoryIfPresent();
        }
    }

    private String customerReference(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        String email = authentication.getToken().getClaimAsString("email");

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication email missing");
        }

        return email;
    }

    private String subject(JwtAuthenticationToken authentication) {
        String subject = authentication == null || authentication.getToken() == null
                ? null : authentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated JWT subject is required");
        }
        return subject;
    }

    private boolean isStaff(JwtAuthenticationToken authentication) {
        return authentication != null && authentication.getAuthorities() != null
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_MANAGER")
                        || authority.getAuthority().equals("ROLE_COMMERCIAL"));
    }

    private void ensureGuestTrackingUrl(OrderEntity order) {
        if (order != null && order.isGuestOrder()
                && (order.getCustomerSubject() == null || order.getCustomerSubject().isBlank())
                && (order.getTrackingUrl() == null || order.getTrackingUrl().isBlank())
                && trackingTokenService != null) {
            TrackingTokenService.GeneratedTrackingToken generatedToken =
                    trackingTokenService.generateToken(java.time.Instant.now());
            order.rotateTrackingToken(generatedToken.tokenHash(), generatedToken.expiresAt());
            order.assignTrackingUrl(generatedToken.trackingUrl());
            repository.save(order);
        }
    }
}

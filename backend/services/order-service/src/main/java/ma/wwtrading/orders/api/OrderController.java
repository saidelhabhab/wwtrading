package ma.wwtrading.orders.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import ma.wwtrading.orders.application.OrderApplicationService;
import ma.wwtrading.orders.infrastructure.PaymentBankLogoStorage;
import ma.wwtrading.orders.security.GuestEndpointRateLimiter;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@RestController
@RequestMapping("/api/orders")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class OrderController {

    private final OrderApplicationService service;
    private final PaymentBankLogoStorage logoStorage;
    private OrderPdfDocument pdfDocument;
    private GuestEndpointRateLimiter rateLimiter = new GuestEndpointRateLimiter();

    public OrderController(OrderApplicationService service, PaymentBankLogoStorage logoStorage) {
        this.service = service;
        this.logoStorage = logoStorage;
    }

    @Autowired(required = false)
    void setPdfDocument(OrderPdfDocument pdfDocument) {
        this.pdfDocument = pdfDocument;
    }

    @Autowired(required = false)
    void setRateLimiter(GuestEndpointRateLimiter rateLimiter) {
        if (rateLimiter != null) {
            this.rateLimiter = rateLimiter;
        }
    }

    @PostMapping
    @Operation(summary = "Create an order")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public ResponseEntity<OrderResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        OrderResponse order = idempotencyKey != null && !idempotencyKey.isBlank()
                ? service.create(authentication, request, idempotencyKey)
                : service.create(authentication, request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    public ResponseEntity<OrderResponse> create(
            JwtAuthenticationToken authentication,
            CreateOrderRequest request) {
        return create(authentication, request, null);
    }

    @PostMapping("/guest/pricing")
    @Operation(summary = "Preview authoritative guest checkout pricing without creating an order")
    public OrderPricingResponse previewGuestPricing(
            @Valid @RequestBody PreviewGuestOrderPricingRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.checkCheckoutAllowed(resolveClientIp(servletRequest));
        return service.previewGuestPricing(request);
    }

    @PostMapping("/guest")
    @Operation(summary = "Create a guest order without authentication")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Guest order created"),
            @ApiResponse(responseCode = "400", description = "Invalid guest order request"),
            @ApiResponse(responseCode = "409", description = "Stock unavailable or conflicting idempotency key"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<OrderResponse> createGuestOrder(
            @Valid @RequestBody CreateGuestOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest) {
        rateLimiter.checkCheckoutAllowed(resolveClientIp(servletRequest));
        OrderResponse order = service.createGuestOrder(request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/orders/guest/track")).body(order);
    }

    @GetMapping("/guest/track")
    @Operation(summary = "Track a guest order by secure tracking token")
    public ResponseEntity<PublicOrderTrackingResponse> trackGuestOrder(
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestHeader(value = "X-Tracking-Token", required = false) String headerToken,
            HttpServletRequest servletRequest) {
        rateLimiter.checkTrackingAllowed(resolveClientIp(servletRequest));
        String effectiveToken = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
        PublicOrderTrackingResponse response = service.trackOrderByToken(effectiveToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping(value = "/guest/track/transfer-proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload bank transfer proof for a guest order using its tracking token")
    public ResponseEntity<PublicOrderTrackingResponse> uploadGuestTransferProof(
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestHeader(value = "X-Tracking-Token", required = false) String headerToken,
            @RequestParam(value = "installment", required = false) Integer installment,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest servletRequest) {
        rateLimiter.checkCheckoutAllowed(resolveClientIp(servletRequest));
        String effectiveToken = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
        PublicOrderTrackingResponse response = service.uploadGuestTransferProofByToken(effectiveToken, file, installment);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/guest/track/second-installment/method")
    @Operation(summary = "Select payment method for the 50% balance of a guest order")
    public ResponseEntity<PublicOrderTrackingResponse> selectGuestSecondInstallmentMethod(
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestHeader(value = "X-Tracking-Token", required = false) String headerToken,
            @Valid @RequestBody SelectSecondInstallmentMethodRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.checkCheckoutAllowed(resolveClientIp(servletRequest));
        String effectiveToken = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
        PublicOrderTrackingResponse response = service.selectGuestSecondInstallmentMethodByToken(effectiveToken, request.normalizedMethod());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping(value = "/guest/track/transfer-proof")
    @Operation(summary = "Download or view bank transfer proof for a guest order using its tracking token")
    public ResponseEntity<Resource> getGuestTransferProof(
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestHeader(value = "X-Tracking-Token", required = false) String headerToken,
            @RequestParam(value = "download", defaultValue = "false") boolean download,
            @RequestParam(value = "installment", defaultValue = "1") int installment,
            HttpServletRequest servletRequest) {
        rateLimiter.checkCheckoutAllowed(resolveClientIp(servletRequest));
        String effectiveToken = headerToken != null && !headerToken.isBlank() ? headerToken : queryToken;
        return service.getGuestTransferProofByToken(effectiveToken, download, installment);
    }

    @PostMapping("/claim-guest-orders")
    @Operation(summary = "Link eligible guest orders matching the authenticated user's verified email")
    public ClaimGuestOrdersResponse claimGuestOrders(JwtAuthenticationToken authentication) {
        return service.claimGuestOrders(authentication);
    }

    @PostMapping("/{id}/tracking-token/revoke")
    @Operation(summary = "Revoke the guest tracking token for an order")
    public ResponseEntity<Void> revokeTrackingToken(@PathVariable UUID id) {
        service.revokeGuestTrackingToken(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/tracking-token/regenerate")
    @Operation(summary = "Regenerate the guest tracking token for an order")
    public java.util.Map<String, String> regenerateTrackingToken(@PathVariable UUID id) {
        String trackingUrl = service.regenerateGuestTrackingToken(id);
        return java.util.Map.of("trackingUrl", trackingUrl);
    }

    @PostMapping("/pricing")
    @Operation(summary = "Preview authoritative checkout pricing without creating an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current subtotal, delivery fee and total"),
            @ApiResponse(responseCode = "400", description = "Invalid delivery selection or address"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient role"),
            @ApiResponse(responseCode = "409", description = "Cart quantity exceeds current availability")
    })
    public OrderPricingResponse previewPricing(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody PreviewOrderPricingRequest request) {
        return service.previewPricing(authentication, request);
    }

    @GetMapping("/payment-banks")
    public List<PaymentBankResponse> paymentBanks() {
        return service.paymentBanks();
    }

    @GetMapping("/delivery-settings")
    @Operation(summary = "Get active delivery fee configuration and threshold")
    public DeliverySettingsResponse deliverySettings() {
        return service.getDeliverySettings();
    }

    @PutMapping("/delivery-settings")
    @Operation(summary = "Update active delivery fee configuration and threshold")
    public DeliverySettingsResponse updateDeliverySettings(@Valid @RequestBody UpdateDeliverySettingsRequest request) {
        return service.updateDeliverySettings(request);
    }

    @GetMapping("/payment-banks/logos/{filename:.+}")
    public ResponseEntity<Resource> getBankLogo(@PathVariable String filename) {
        if (logoStorage == null) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = logoStorage.loadAsResource(filename);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String mediaType = "image/png";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            mediaType = "image/jpeg";
        } else if (lower.endsWith(".webp")) {
            mediaType = "image/webp";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mediaType)
                .body(resource);
    }

    @PostMapping(value = "/{id}/transfer-proof", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrderResponse uploadTransferProof(
            JwtAuthenticationToken authentication,
            @PathVariable UUID id,
            @RequestParam(value = "installment", required = false) Integer installment,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        return service.uploadTransferProof(authentication, id, file, installment);
    }

    @GetMapping(value = "/{id}/transfer-proof")
    @Operation(summary = "Download or view the transfer proof for an authorized order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer proof returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Transfer proof not found")
    })
    public ResponseEntity<Resource> getTransferProof(
            JwtAuthenticationToken authentication,
            @PathVariable UUID id,
            @RequestParam(value = "download", defaultValue = "false") boolean download,
            @RequestParam(value = "installment", defaultValue = "1") int installment) {
        return service.getTransferProof(authentication, id, download, installment);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order by identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient role"),
            @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public OrderResponse getById(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        return service.getById(authentication, id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the persisted order as a PDF")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order PDF returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "500", description = "Document generation failed")
    })
    public ResponseEntity<byte[]> pdf(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        OrderResponse order = service.getById(authentication, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("bon-de-commande-" + id + ".pdf").build().toString())
                .body(pdfDocument.render(order));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel an owned draft order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be cancelled")
    })
    public OrderResponse cancel(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        return service.cancel(authentication, id);
    }

    @PatchMapping("/{id}/second-installment/method")
    @Operation(summary = "Select payment method for the 50% balance (BANK_TRANSFER or CASH)")
    public OrderResponse selectSecondInstallmentMethod(
            JwtAuthenticationToken authentication,
            @PathVariable UUID id,
            @Valid @RequestBody SelectSecondInstallmentMethodRequest request) {
        return service.selectSecondInstallmentMethod(authentication, id, request.normalizedMethod());
    }

    @PatchMapping("/{id}/payment/confirm")
    @Operation(summary = "Confirm payment or next installment for an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment confirmed"),
            @ApiResponse(responseCode = "400", description = "Invalid payment amount"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Payment cannot be confirmed")
    })
    public OrderResponse confirmPayment(
            JwtAuthenticationToken authentication,
            @PathVariable UUID id,
            @RequestBody(required = false) ConfirmPaymentRequest request) {
        String staffIdentifier = authentication != null ? authentication.getName() : "STAFF";
        return service.confirmPayment(id, request == null ? null : request.amount(), staffIdentifier);
    }

    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirm an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order confirmed"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be confirmed")
    })
    public OrderResponse confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @PatchMapping("/{id}/process")
    @Operation(summary = "Start processing an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order processing started"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot start processing")
    })
    public OrderResponse process(@PathVariable UUID id) {
        return service.process(id);
    }

    @PatchMapping("/{id}/ready")
    @Operation(summary = "Mark an order as ready for fulfilment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked ready"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be marked ready")
    })
    public OrderResponse markReady(@PathVariable UUID id) {
        return service.markOrderReady(id);
    }

    @PatchMapping("/{id}/complete")
    @Operation(summary = "Complete order fulfilment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order fulfilment completed"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order cannot be completed")
    })
    public OrderResponse complete(@PathVariable UUID id) {
        return service.completeOrder(id);
    }

    @GetMapping("/my-orders")
    @Operation(summary = "List the authenticated customer's orders")
    public Page<OrderResponse> myOrders(
            JwtAuthenticationToken authentication,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getMyOrders(authentication, pageable);
    }

    @GetMapping
    @Operation(summary = "List orders with pagination")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order page returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public Page<OrderResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    private String resolveClientIp(HttpServletRequest servletRequest) {
        if (servletRequest == null) {
            return "unknown";
        }
        String forwarded = servletRequest.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return servletRequest.getRemoteAddr();
    }
}

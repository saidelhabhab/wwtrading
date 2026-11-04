package ma.wwtrading.orders.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import ma.wwtrading.orders.application.PaymentBankApplicationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/payment-banks")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class AdminPaymentBankController {

    private final PaymentBankApplicationService service;

    public AdminPaymentBankController(PaymentBankApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all payment banks for administration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of payment banks"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient role (ADMIN required)")
    })
    public List<PaymentBankResponse> listAll() {
        return service.listAllBanks();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment bank by ID")
    public PaymentBankResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new payment bank with multipart form data (with optional logo)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment bank created"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Duplicate reference")
    })
    public ResponseEntity<PaymentBankResponse> createMultipart(
            @RequestParam("reference") String reference,
            @RequestParam("bankName") String bankName,
            @RequestParam("rib") String rib,
            @RequestParam("beneficiary") String beneficiary,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestPart(value = "logo", required = false) MultipartFile logo
    ) {
        CreatePaymentBankRequest request = new CreatePaymentBankRequest(reference, bankName, rib, beneficiary, null, active);
        PaymentBankResponse created = service.createBank(request, logo);
        return ResponseEntity.created(URI.create("/api/admin/payment-banks/" + created.id())).body(created);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new payment bank with JSON")
    public ResponseEntity<PaymentBankResponse> createJson(@Valid @RequestBody CreatePaymentBankRequest request) {
        PaymentBankResponse created = service.createBank(request, null);
        return ResponseEntity.created(URI.create("/api/admin/payment-banks/" + created.id())).body(created);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update a payment bank with multipart form data (with optional logo)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment bank updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Payment bank not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate reference")
    })
    public PaymentBankResponse updateMultipart(
            @PathVariable UUID id,
            @RequestParam("reference") String reference,
            @RequestParam("bankName") String bankName,
            @RequestParam("rib") String rib,
            @RequestParam("beneficiary") String beneficiary,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestPart(value = "logo", required = false) MultipartFile logo
    ) {
        UpdatePaymentBankRequest request = new UpdatePaymentBankRequest(reference, bankName, rib, beneficiary, null, active);
        return service.updateBank(id, request, logo);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a payment bank with JSON")
    public PaymentBankResponse updateJson(@PathVariable UUID id, @Valid @RequestBody UpdatePaymentBankRequest request) {
        return service.updateBank(id, request, null);
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Activate or deactivate a payment bank")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment bank active status updated"),
            @ApiResponse(responseCode = "404", description = "Payment bank not found")
    })
    public PaymentBankResponse setActive(@PathVariable UUID id, @Valid @RequestBody UpdatePaymentBankActiveRequest request) {
        return service.setActive(id, request.active());
    }
}

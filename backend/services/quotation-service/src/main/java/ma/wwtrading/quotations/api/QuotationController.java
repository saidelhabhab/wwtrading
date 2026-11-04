package ma.wwtrading.quotations.api;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import ma.wwtrading.quotations.application.QuotationApplicationService;
import ma.wwtrading.quotations.domain.QuotationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotations")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class QuotationController {

    private final QuotationApplicationService service;
    private QuotationPdfDocument pdfDocument;

    public QuotationController(QuotationApplicationService service) {
        this.service = service;
    }

    @Autowired(required = false)
    void setPdfDocument(QuotationPdfDocument pdfDocument) {
        this.pdfDocument = pdfDocument;
    }

    @PostMapping
    @Operation(summary = "Create a new quotation request")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Quotation request created"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<QuotationResponse> create(
        JwtAuthenticationToken authentication,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
        @Valid @RequestBody CreateQuotationRequest request
    ) {
        QuotationResponse quotation = service.create(authentication, idempotencyKeyHeader, request);
        return ResponseEntity.created(URI.create("/api/quotations/" + quotation.id())).body(quotation);
    }

    @GetMapping("/my-quotations")
    @Operation(summary = "Get quotations for the authenticated client")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Client quotations returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public Page<QuotationResponse> getMyQuotations(
        JwtAuthenticationToken authentication,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return service.getMyQuotations(authentication, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a quotation request by identifier")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quotation returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Quotation not found")
    })
    public QuotationResponse getById(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.getQuotationById(authentication, id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the persisted quotation as a PDF")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quotation PDF returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Quotation not found"),
        @ApiResponse(responseCode = "500", description = "Document generation failed")
    })
    public ResponseEntity<byte[]> pdf(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        QuotationResponse quotation = service.getQuotationById(authentication, id);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename("devis-" + id + ".pdf").build().toString())
            .body(pdfDocument.render(quotation));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending quotation request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quotation cancelled"),
        @ApiResponse(responseCode = "400", description = "Cannot cancel quotation in current status"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Quotation not found")
    })
    public QuotationResponse cancel(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.cancelQuotation(authentication, id);
    }

    @PutMapping("/{id}/decision")
    @Operation(summary = "Accept or reject a quoted proposal as its owner")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Client decision saved"),
        @ApiResponse(responseCode = "400", description = "Invalid decision"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Not the quotation owner"),
        @ApiResponse(responseCode = "404", description = "Quotation not found"),
        @ApiResponse(responseCode = "409", description = "Quotation is not awaiting a decision")
    })
    public QuotationResponse decide(JwtAuthenticationToken authentication, @PathVariable UUID id,
            @Valid @RequestBody QuotationDecisionRequest request) {
        return service.decide(authentication, id, request.status());
    }

    @GetMapping
    @Operation(summary = "List all quotation requests (Staff only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quotations list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public Page<QuotationResponse> getAll(
        JwtAuthenticationToken authentication,
        @RequestParam(required = false) QuotationStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return service.getAllQuotations(authentication, status, pageable);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update quotation status and proposed amount (Staff only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quotation status updated"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition or missing proposed amount"),
        @ApiResponse(responseCode = "403", description = "Insufficient role"),
        @ApiResponse(responseCode = "404", description = "Quotation not found")
    })
    public QuotationResponse updateStatus(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateQuotationStatusRequest request
    ) {
        return service.updateQuotationStatus(authentication, id, request);
    }
}

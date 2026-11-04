package ma.wwtrading.quotations.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateQuotationRequest(
    @NotBlank @Size(max = 64) String serviceType,
    @Size(max = 255) String requestedEquipment,
    @Size(max = 255) String brandModel,
    @Min(1) @Max(9999) Integer quantity,
    @Size(max = 4000) String description,
    @NotBlank @Size(max = 120) String fullName,
    @Size(max = 120) String company,
    @NotBlank @Size(max = 30)
    @Pattern(
        regexp = "^(\\+?[0-9]{1,4}[\\s.-]?)?(\\(?[0-9]{1,4}\\)?[\\s.-]?)?[0-9\\s.-]{6,20}$",
        message = "Format de numéro de téléphone invalide (ex: 0612345678, +212612345678)"
    )
    String phone,
    @NotBlank @Email @Size(max = 120) String email,
    @NotBlank @Size(max = 100) String city,
    @Size(max = 64) String desiredTimeline,
    @DecimalMin(value = "0.00", inclusive = true) BigDecimal estimatedBudget,
    @Size(max = 4000) String additionalMessage,
    @Valid @Size(max = 20) List<RequestedLine> requestedLines,
    @Size(max = 128) String idempotencyKey
) {
    public record RequestedLine(
        @NotNull UUID productId,
        @Min(1) @Max(9999) int quantity
    ) { }

    public CreateQuotationRequest(String serviceType, String requestedEquipment, String brandModel,
            Integer quantity, String description, String fullName, String company, String phone,
            String email, String city, String desiredTimeline, BigDecimal estimatedBudget,
            String additionalMessage) {
        this(serviceType, requestedEquipment, brandModel, quantity, description, fullName, company,
            phone, email, city, desiredTimeline, estimatedBudget, additionalMessage, null, null);
    }

    public CreateQuotationRequest(String serviceType, String requestedEquipment, String brandModel,
            Integer quantity, String description, String fullName, String company, String phone,
            String email, String city, String desiredTimeline, BigDecimal estimatedBudget,
            String additionalMessage, List<RequestedLine> requestedLines) {
        this(serviceType, requestedEquipment, brandModel, quantity, description, fullName, company,
            phone, email, city, desiredTimeline, estimatedBudget, additionalMessage, requestedLines, null);
    }
}

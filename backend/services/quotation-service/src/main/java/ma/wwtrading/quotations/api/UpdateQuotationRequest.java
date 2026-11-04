package ma.wwtrading.quotations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateQuotationRequest(
    @NotBlank @Size(max = 200) String subject,
    @NotBlank @Size(max = 4000) String requestDescription,
    @Size(max = 10000) String notes
) {
}

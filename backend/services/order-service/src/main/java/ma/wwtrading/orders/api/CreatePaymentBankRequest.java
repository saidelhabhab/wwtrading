package ma.wwtrading.orders.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePaymentBankRequest(
    @NotBlank(message = "Bank reference is required")
    @Size(max = 100, message = "Bank reference cannot exceed 100 characters")
    String reference,

    @NotBlank(message = "Bank name is required")
    @Size(max = 150, message = "Bank name cannot exceed 150 characters")
    String bankName,

    @NotBlank(message = "RIB is required")
    @Size(max = 50, message = "RIB cannot exceed 50 characters")
    String rib,

    @NotBlank(message = "Beneficiary is required")
    @Size(max = 150, message = "Beneficiary cannot exceed 150 characters")
    String beneficiary,

    @Size(max = 500, message = "Logo URL cannot exceed 500 characters")
    String logoUrl,

    Boolean active
) {
}


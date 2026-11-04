package ma.wwtrading.orders.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SelectSecondInstallmentMethodRequest(
    @NotBlank(message = "Le mode de paiement du solde est obligatoire")
    @Pattern(regexp = "^(?i)(BANK_TRANSFER|CASH)$", message = "Le mode doit être BANK_TRANSFER ou CASH")
    String method
) {
    public String normalizedMethod() {
        return method == null ? null : method.trim().toUpperCase();
    }
}


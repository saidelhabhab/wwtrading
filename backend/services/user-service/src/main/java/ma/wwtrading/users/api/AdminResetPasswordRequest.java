package ma.wwtrading.users.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
    @NotNull(message = "Le mode de réinitialisation est obligatoire")
    ResetPasswordMode mode,

    @Size(max = 128, message = "Le mot de passe ne doit pas dépasser 128 caractères")
    String newPassword,

    @Size(max = 128, message = "La confirmation ne doit pas dépasser 128 caractères")
    String confirmPassword
) {
    public enum ResetPasswordMode {
        DIRECT,
        EMAIL
    }
}


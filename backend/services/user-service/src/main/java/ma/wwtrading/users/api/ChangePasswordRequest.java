package ma.wwtrading.users.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#.+='\":;><,{}~`|\\[\\]\\\\/]).{12,}$",
                message = "Le mot de passe doit contenir au moins 12 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial."
        )
        String newPassword,
        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        String confirmPassword
        ) {

}

package ma.wwtrading.users.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100)
        String firstName,
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100)
        String lastName,
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Email invalide")
        @Size(max = 320)
        String email,
        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        @Pattern(regexp = "^(?:\\+212|0)[67]\\d{8}$", message = "Format de téléphone marocain valide requis (ex: 0612345678 ou +212612345678)")
        String phone,
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#.+='\":;><,{}~`|\\[\\]\\\\/]).{12,}$",
                message = "Le mot de passe doit contenir au moins 12 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial."
        )
        String password,
        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        String confirmPassword
        ) {

}

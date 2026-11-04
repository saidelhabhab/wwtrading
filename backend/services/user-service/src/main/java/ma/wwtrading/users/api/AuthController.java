package ma.wwtrading.users.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import ma.wwtrading.users.events.UserEventOutbox;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final KeycloakClient keycloakClient;
    private final UserRepository userRepository;
    private final UserEventOutbox eventOutbox;

    public AuthController(KeycloakClient keycloakClient, UserRepository userRepository, UserEventOutbox eventOutbox) {
        this.keycloakClient = keycloakClient;
        this.userRepository = userRepository;
        this.eventOutbox = eventOutbox;
    }

    @PostMapping("/register")
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les mots de passe ne correspondent pas.");
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email.");
        }

        String phone = normalizePhone(request.phone());
        String createdUserId = keycloakClient.createClientUser(
                request.firstName(),
                request.lastName(),
                email,
                phone,
                request.password()
        );

        UserEntity userEntity = UserEntity.fromIdentity(
                createdUserId,
                email,
                request.firstName().trim(),
                request.lastName().trim(),
                "fr"
        );
        userEntity.setPhone(phone);
        UserEntity saved = userRepository.save(userEntity);
        eventOutbox.appendUserCreated(saved, null);

        log.info("Registered new CLIENT user with subject ID {}", createdUserId);
        return new AuthResponse(true, "Votre compte a été créé avec succès.");
    }

    @PostMapping("/forgot-password")
    public AuthResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        keycloakClient.executePasswordResetEmail(request.email());
        return new AuthResponse(true, "Si une adresse correspond à cet email, un lien de réinitialisation a été envoyé.");
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public AuthResponse changePassword(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les nouveaux mots de passe ne correspondent pas.");
        }

        String subject = authentication.getToken().getSubject();
        keycloakClient.changeUserPassword(subject, request.newPassword());

        log.info("Password successfully updated for user subject {}", subject);
        return new AuthResponse(true, "Votre mot de passe a été modifié avec succès.");
    }

    private String normalizePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le numéro de téléphone est obligatoire.");
        }
        String cleaned = rawPhone.replaceAll("[\\s.\\-()]", "");
        if (cleaned.startsWith("0")) {
            return "+212" + cleaned.substring(1);
        }
        if (!cleaned.startsWith("+")) {
            return "+" + cleaned;
        }
        return cleaned;
    }
}

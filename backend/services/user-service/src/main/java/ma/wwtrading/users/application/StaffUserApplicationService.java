package ma.wwtrading.users.application;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import ma.wwtrading.users.api.AdminResetPasswordRequest;
import ma.wwtrading.users.api.AdminResetPasswordResponse;
import ma.wwtrading.users.api.CreateStaffUserRequest;
import ma.wwtrading.users.api.CreateStaffUserRequest.StaffRole;
import ma.wwtrading.users.api.StaffUserResponse;
import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffUserApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffUserApplicationService.class);
    private static final Set<StaffRole> ADMIN_TARGETS = Set.of(
        StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.COMMERCIAL, StaffRole.TECHNICIAN
    );
    private static final Set<StaffRole> MANAGER_TARGETS = Set.of(StaffRole.COMMERCIAL, StaffRole.TECHNICIAN);
    private static final Set<StaffRole> ADMIN_VISIBLE_ROLES = Set.of(
        StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.COMMERCIAL, StaffRole.TECHNICIAN
    );
    private static final Set<StaffRole> MANAGER_VISIBLE_ROLES = Set.of(
        StaffRole.MANAGER, StaffRole.COMMERCIAL, StaffRole.TECHNICIAN
    );
    private static final Comparator<StaffUserResponse> STAFF_ORDERING = Comparator
        .comparing((StaffUserResponse user) -> nullSafe(user.lastName()), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(user -> nullSafe(user.firstName()), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(user -> nullSafe(user.email()), String.CASE_INSENSITIVE_ORDER);

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,}$"
    );

    private final UserRepository repository;
    private final KeycloakClient keycloakClient;

    public StaffUserApplicationService(UserRepository repository, KeycloakClient keycloakClient) {
        this.repository = repository;
        this.keycloakClient = keycloakClient;
    }

    public StaffUserResponse create(Authentication authentication, CreateStaffUserRequest request) {
        authorize(authentication, request.role());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
        }

        String keycloakSubject = keycloakClient.createStaffUser(
            request.firstName().trim(), request.lastName().trim(), email, request.password(), request.role().name()
        );
        try {
            UserEntity profile = UserEntity.fromIdentity(
                keycloakSubject, email, request.firstName().trim(), request.lastName().trim(), "fr"
            );
            profile.setPhone(normalizePhone(request.phone()));
            UserEntity saved = repository.saveAndFlush(profile);
            return new StaffUserResponse(saved.getKeycloakSubject(), saved.getFirstName(), saved.getLastName(),
                saved.getEmail(), request.role(), saved.isEnabled(), saved.getPhone());
        } catch (RuntimeException persistenceFailure) {
            try {
                keycloakClient.deleteUser(keycloakSubject);
            } catch (RuntimeException compensationFailure) {
                LOGGER.error("Staff user compensation failed for newly-created Keycloak subject={}", keycloakSubject);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "User creation failed and identity cleanup requires administrator attention");
            }
            if (persistenceFailure instanceof DataIntegrityViolationException) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "User profile could not be created");
        }
    }

    public List<StaffUserResponse> getStaffUsers(Authentication authentication) {
        Set<StaffRole> visibleRoles = visibleRoles(authentication);
        Map<String, String> roleAssignments = keycloakClient.getStaffRoleAssignments();
        if (roleAssignments.isEmpty()) {
            return List.of();
        }

        return repository.findByKeycloakSubjectIn(roleAssignments.keySet()).stream()
            .map(user -> toStaffResponse(user, roleAssignments.get(user.getKeycloakSubject())))
            .filter(response -> response != null && visibleRoles.contains(response.role()))
            .sorted(STAFF_ORDERING)
            .toList();
    }

    public AdminResetPasswordResponse resetPassword(
            Authentication authentication,
            String targetUserId,
            AdminResetPasswordRequest request) {
        if (!hasRole(authentication, "ROLE_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Seul un administrateur peut modifier ou réinitialiser le mot de passe d'un utilisateur");
        }

        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identifiant utilisateur obligatoire");
        }

        if (request == null || request.mode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mode de réinitialisation est obligatoire");
        }

        String cleanUserId = targetUserId.trim();
        UserEntity targetUser = repository.findByKeycloakSubject(cleanUserId)
            .or(() -> {
                try {
                    return repository.findById(UUID.fromString(cleanUserId));
                } catch (IllegalArgumentException ex) {
                    return java.util.Optional.empty();
                }
            })
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        String keycloakSubject = targetUser.getKeycloakSubject();

        if (request.mode() == AdminResetPasswordRequest.ResetPasswordMode.DIRECT) {
            String newPassword = request.newPassword();
            String confirmPassword = request.confirmPassword();

            if (newPassword == null || newPassword.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nouveau mot de passe est obligatoire");
            }
            if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les mots de passe ne correspondent pas");
            }
            if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir au moins 12 caractères, avec une majuscule, une minuscule, un chiffre et un caractère spécial");
            }

            keycloakClient.changeUserPassword(keycloakSubject, newPassword);
            LOGGER.info("Admin [{}] successfully updated password for user subject [{}]",
                authentication != null ? authentication.getName() : "unknown", keycloakSubject);
            return new AdminResetPasswordResponse(true, "Le mot de passe a été réinitialisé avec succès.");
        } else if (request.mode() == AdminResetPasswordRequest.ResetPasswordMode.EMAIL) {
            keycloakClient.executePasswordResetEmailForUser(keycloakSubject);
            LOGGER.info("Admin [{}] sent password reset email to user subject [{}]",
                authentication != null ? authentication.getName() : "unknown", keycloakSubject);
            return new AdminResetPasswordResponse(true, "Un email de réinitialisation a été envoyé à l'adresse de l'utilisateur.");
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mode de réinitialisation non supporté");
    }

    private void authorize(Authentication authentication, StaffRole requestedRole) {
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff user creation is not permitted");
        }
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean manager = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_MANAGER".equals(authority.getAuthority()));
        if ((admin && ADMIN_TARGETS.contains(requestedRole))
                || (manager && MANAGER_TARGETS.contains(requestedRole))) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "The authenticated role cannot create the requested staff role");
    }

    private Set<StaffRole> visibleRoles(Authentication authentication) {
        if (hasRole(authentication, "ROLE_ADMIN")) {
            return ADMIN_VISIBLE_ROLES;
        }
        if (hasRole(authentication, "ROLE_MANAGER")) {
            return MANAGER_VISIBLE_ROLES;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff directory access is not permitted");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities() != null
            && authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private StaffUserResponse toStaffResponse(UserEntity user, String roleName) {
        try {
            StaffRole role = StaffRole.valueOf(roleName);
            if (role == StaffRole.CLIENT) return null;
            return new StaffUserResponse(user.getKeycloakSubject(), user.getFirstName(), user.getLastName(),
                user.getEmail(), role, user.isEnabled(), user.getPhone());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}

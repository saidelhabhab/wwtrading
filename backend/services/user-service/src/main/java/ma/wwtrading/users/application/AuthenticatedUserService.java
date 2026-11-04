package ma.wwtrading.users.application;

import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.wwtrading.security.KeycloakRealmRoles;
import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import ma.wwtrading.users.events.UserEventOutbox;

@Service
public class AuthenticatedUserService {

    private static final Set<String> STAFF_ROLES = Set.of(
        "ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN"
    );

    private final UserRepository userRepository;
    private final KeycloakClient keycloakClient;
    private final UserEventOutbox eventOutbox;

    public AuthenticatedUserService(UserRepository userRepository, KeycloakClient keycloakClient,
            UserEventOutbox eventOutbox) {
        this.userRepository = userRepository;
        this.keycloakClient = keycloakClient;
        this.eventOutbox = eventOutbox;
    }

    @Transactional
    public UserEntity synchronize(Jwt jwt) {
        IdentityClaims identity = IdentityClaims.from(jwt);
        String subject = identityKey(identity);

        Set<String> roles = KeycloakRealmRoles.roles(jwt);
        boolean isStaff = roles.stream().anyMatch(STAFF_ROLES::contains);
        if (!isStaff) {
            keycloakClient.ensureClientRole(subject);
            keycloakClient.ensureEmailVerified(subject);
        }

        var existingBySubject = userRepository.findByKeycloakSubject(subject);
        if (existingBySubject.isPresent()) return existingBySubject.get();

        var existingByEmail = userRepository.findByEmailIgnoreCase(identity.email());
        if (existingByEmail.isPresent()) {
            existingByEmail.get().refreshKeycloakSubject(subject);
            return existingByEmail.get();
        }

        UserEntity created = userRepository.save(UserEntity.fromIdentity(
                subject, identity.email(), identity.firstName(), identity.lastName(), identity.locale()));
        eventOutbox.appendUserCreated(created, null);
        return created;
    }

    private String identityKey(IdentityClaims identity) {
        if (identity.subject() == null || identity.subject().isBlank()) {
            throw new MissingIdentityClaimException(
                "The authenticated identity has no valid subject claim"
            );
        }
        return identity.subject();
    }
}

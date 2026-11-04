package ma.wwtrading.users.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import ma.wwtrading.users.events.UserEventOutbox;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakClient keycloakClient;

    @Mock
    private UserEventOutbox eventOutbox;

    private AuthenticatedUserService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticatedUserService(userRepository, keycloakClient, eventOutbox);
    }

    @Test
    void reusesExistingProfileByEmail() {
        String subject = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        UserEntity existing = UserEntity.fromIdentity(
            "previous-keycloak-subject", "client@example.test", "Existing", "Profile", "fr"
        );
        when(userRepository.findByKeycloakSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("client@example.test")).thenReturn(Optional.of(existing));

        UserEntity result = service.synchronize(jwt("client@example.test", subject, "Amina", "Client"));

        assertThat(result).isSameAs(existing);
        assertThat(result.getKeycloakSubject()).isEqualTo(subject);
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(keycloakClient).ensureClientRole(subject);
    }

    @Test
    void clientSynchronizationEnsuresClientRoleInKeycloak() {
        String subject = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        when(userRepository.findByEmailIgnoreCase("client@example.test")).thenReturn(Optional.empty());
        when(userRepository.findByKeycloakSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.synchronize(jwt("client@example.test", subject, "Amina", "Client", List.of("CLIENT")));

        assertThat(result.getEmail()).isEqualTo("client@example.test");
        assertThat(result.getKeycloakSubject()).isEqualTo(subject);
        verify(keycloakClient).ensureClientRole(subject);
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void staffSynchronizationDoesNotAssignClientRole() {
        String subject = "bbbbbbbb-cccc-dddd-eeee-ffffffffffff";
        when(userRepository.findByEmailIgnoreCase("admin@wwtrading.local")).thenReturn(Optional.empty());
        when(userRepository.findByKeycloakSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.synchronize(jwt("admin@wwtrading.local", subject, "Admin", "User", List.of("ADMIN")));

        assertThat(result.getEmail()).isEqualTo("admin@wwtrading.local");
        verify(keycloakClient, never()).ensureClientRole(any());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void managerSynchronizationDoesNotAssignClientRole() {
        String subject = "cccccccc-dddd-eeee-ffff-000000000001";
        when(userRepository.findByEmailIgnoreCase("manager@wwtrading.local")).thenReturn(Optional.empty());
        when(userRepository.findByKeycloakSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.synchronize(jwt("manager@wwtrading.local", subject, "Manager", "User", List.of("MANAGER")));

        verify(keycloakClient, never()).ensureClientRole(any());
    }

    @Test
    void commercialSynchronizationDoesNotAssignClientRole() {
        String subject = "cccccccc-dddd-eeee-ffff-000000000002";
        when(userRepository.findByEmailIgnoreCase("commercial@wwtrading.local")).thenReturn(Optional.empty());
        when(userRepository.findByKeycloakSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.synchronize(jwt("commercial@wwtrading.local", subject, "Commercial", "User", List.of("COMMERCIAL")));

        verify(keycloakClient, never()).ensureClientRole(any());
    }

    @Test
    void technicianSynchronizationDoesNotAssignClientRole() {
        String subject = "cccccccc-dddd-eeee-ffff-000000000003";
        when(userRepository.findByEmailIgnoreCase("technician@wwtrading.local")).thenReturn(Optional.empty());
        when(userRepository.findByKeycloakSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.synchronize(jwt("technician@wwtrading.local", subject, "Technician", "User", List.of("TECHNICIAN")));

        verify(keycloakClient, never()).ensureClientRole(any());
    }

    @Test
    void googleUserSynchronizationAssignsClientRole() {
        String subject = "dddddddd-eeee-ffff-0000-111111111111";
        when(userRepository.findByEmailIgnoreCase("google-user@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.findByKeycloakSubject(subject)).thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity result = service.synchronize(jwt("google-user@gmail.com", subject, "Google", "User", List.of()));

        assertThat(result.getEmail()).isEqualTo("google-user@gmail.com");
        assertThat(result.getKeycloakSubject()).isEqualTo(subject);
        verify(keycloakClient).ensureClientRole(subject);
    }

    @Test
    void rejectsJwtWithMissingSubjectOnNewProfileCreation() {
        assertThatThrownBy(() -> service.synchronize(jwt("client@example.test", null, "Amina", "Client")))
            .isInstanceOf(MissingIdentityClaimException.class)
            .hasMessageContaining("subject");
        verify(userRepository, never()).save(any(UserEntity.class));
    }

    @Test
    void repeatedSynchronizationDoesNotCreateDuplicateProfile() {
        String subject = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        UserEntity existing = UserEntity.fromIdentity(
            subject, "client@example.test", "Amina", "Client", "fr"
        );
        when(userRepository.findByEmailIgnoreCase("client@example.test"))
            .thenReturn(Optional.empty(), Optional.of(existing));
        when(userRepository.findByKeycloakSubject(subject))
            .thenReturn(Optional.empty(), Optional.of(existing));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity first = service.synchronize(jwt("client@example.test", subject, "Amina", "Client"));
        UserEntity second = service.synchronize(jwt("client@example.test", subject, "Amina", "Client"));

        assertThat(first.getEmail()).isEqualTo(second.getEmail());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void rejectsJwtWithoutEmail() {
        assertThatThrownBy(() -> service.synchronize(jwt(null, null, "Amina", "Client")))
            .isInstanceOf(MissingIdentityClaimException.class);
    }

    private Jwt jwt(String email, String subject, String firstName, String lastName) {
        return jwt(email, subject, firstName, lastName, List.of());
    }

    private Jwt jwt(String email, String subject, String firstName, String lastName, List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("given_name", firstName)
            .claim("family_name", lastName)
            .claim("realm_access", Map.of("roles", roles))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
        if (email != null) builder.claim("email", email);
        if (subject != null) builder.subject(subject);
        return builder.build();
    }
}

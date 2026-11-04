package ma.wwtrading.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import ma.wwtrading.users.api.AdminResetPasswordRequest;
import ma.wwtrading.users.api.AdminResetPasswordResponse;
import ma.wwtrading.users.api.CreateStaffUserRequest;
import ma.wwtrading.users.api.CreateStaffUserRequest.StaffRole;
import ma.wwtrading.users.api.StaffUserResponse;
import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StaffUserApplicationServiceTests {

    @Mock
    private UserRepository repository;

    @Mock
    private KeycloakClient keycloakClient;

    private StaffUserApplicationService service;

    @BeforeEach
    void setUp() {
        service = new StaffUserApplicationService(repository, keycloakClient);
    }

    @Test
    void adminCanCreateEveryStaffRole() {
        for (StaffRole role : List.of(
            StaffRole.ADMIN, StaffRole.MANAGER, StaffRole.COMMERCIAL, StaffRole.TECHNICIAN
        )) {
            String email = role.name().toLowerCase() + "@example.test";
            when(repository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
            when(keycloakClient.createStaffUser("Amina", "Admin", email, "StrongPass!123", role.name()))
                .thenReturn("kc-" + role.name());
            when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            StaffUserResponse response = service.create(authentication("ADMIN"), request(email, role));

            assertThat(response.role()).isEqualTo(role);
            assertThat(response.id()).isEqualTo("kc-" + role.name());
        }
    }

    @Test
    void managerCanCreateCommercialAndTechnician() {
        for (StaffRole role : List.of(StaffRole.COMMERCIAL, StaffRole.TECHNICIAN)) {
            String email = role.name().toLowerCase() + "@example.test";
            when(repository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
            when(keycloakClient.createStaffUser("Amina", "Admin", email, "StrongPass!123", role.name()))
                .thenReturn("kc-" + role.name());
            when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.create(authentication("MANAGER"), request(email, role)).role()).isEqualTo(role);
        }
    }

    @Test
    void managerCannotCreateAdminOrManager() {
        assertForbidden(authentication("MANAGER"), StaffRole.ADMIN);
        assertForbidden(authentication("MANAGER"), StaffRole.MANAGER);
        verify(keycloakClient, never()).createStaffUser(any(), any(), any(), any(), any());
    }

    @Test
    void clientRoleCannotBeCreatedThroughTheStaffEndpoint() {
        assertForbidden(authentication("ADMIN"), StaffRole.CLIENT);
        assertForbidden(authentication("MANAGER"), StaffRole.CLIENT);
        verify(keycloakClient, never()).createStaffUser(any(), any(), any(), any(), any());
    }

    @Test
    void nonManagementRolesCannotCreateStaffUsers() {
        for (String role : List.of("COMMERCIAL", "TECHNICIAN", "CLIENT")) {
            assertForbidden(authentication(role), StaffRole.TECHNICIAN);
        }
        verify(keycloakClient, never()).createStaffUser(any(), any(), any(), any(), any());
    }

    @Test
    void duplicateApplicationEmailReturnsConflictBeforeCallingKeycloak() {
        CreateStaffUserRequest request = request("duplicate@example.test", StaffRole.TECHNICIAN);
        when(repository.findByEmailIgnoreCase("duplicate@example.test"))
            .thenReturn(Optional.of(UserEntity.fromIdentity(
                "existing", "duplicate@example.test", "Existing", "User", "fr"
            )));

        assertThatThrownBy(() -> service.create(authentication("ADMIN"), request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode().value()).isEqualTo(409));
        verify(keycloakClient, never()).createStaffUser(any(), any(), any(), any(), any());
    }

    @Test
    void persistenceFailureDeletesOnlyTheNewKeycloakUser() {
        CreateStaffUserRequest request = request("new@example.test", StaffRole.TECHNICIAN);
        when(repository.findByEmailIgnoreCase("new@example.test")).thenReturn(Optional.empty());
        when(keycloakClient.createStaffUser(
            "Amina", "Admin", "new@example.test", "StrongPass!123", "TECHNICIAN"
        )).thenReturn("new-keycloak-subject");
        when(repository.saveAndFlush(any(UserEntity.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(authentication("ADMIN"), request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode().value()).isEqualTo(409));
        verify(keycloakClient).deleteUser("new-keycloak-subject");
    }

    @Test
    void staffCreationWithOptionalPhone() {
        String email = "tech.phone@example.test";
        when(repository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
        when(keycloakClient.createStaffUser("Karim", "Tech", email, "StrongPass!123", "TECHNICIAN"))
            .thenReturn("kc-tech-1");
        when(repository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateStaffUserRequest req = new CreateStaffUserRequest(
            "Karim", "Tech", email, "StrongPass!123", StaffRole.TECHNICIAN, " +212612345678 "
        );

        StaffUserResponse response = service.create(authentication("ADMIN"), req);

        assertThat(response.phone()).isEqualTo("+212612345678");
    }

    @Test
    void adminCanResetPasswordDirectlyWithValidPassword() {
        String targetSubject = "kc-user-123";
        UserEntity user = UserEntity.fromIdentity(targetSubject, "user@wwtrading.ma", "Ali", "Test", "fr");
        when(repository.findByKeycloakSubject(targetSubject)).thenReturn(Optional.of(user));

        AdminResetPasswordRequest request = new AdminResetPasswordRequest(
            AdminResetPasswordRequest.ResetPasswordMode.DIRECT,
            "NewSecret@2026",
            "NewSecret@2026"
        );

        AdminResetPasswordResponse response = service.resetPassword(authentication("ADMIN"), targetSubject, request);

        assertThat(response.success()).isTrue();
        verify(keycloakClient).changeUserPassword(targetSubject, "NewSecret@2026");
    }

    @Test
    void adminCannotResetPasswordIfPasswordsMismatch() {
        String targetSubject = "kc-user-123";
        UserEntity user = UserEntity.fromIdentity(targetSubject, "user@wwtrading.ma", "Ali", "Test", "fr");
        when(repository.findByKeycloakSubject(targetSubject)).thenReturn(Optional.of(user));

        AdminResetPasswordRequest request = new AdminResetPasswordRequest(
            AdminResetPasswordRequest.ResetPasswordMode.DIRECT,
            "NewSecret@2026",
            "DifferentSecret@2026"
        );

        assertThatThrownBy(() -> service.resetPassword(authentication("ADMIN"), targetSubject, request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        verify(keycloakClient, never()).changeUserPassword(any(), any());
    }

    @Test
    void adminCannotResetPasswordIfPasswordDoesNotMeetPolicy() {
        String targetSubject = "kc-user-123";
        UserEntity user = UserEntity.fromIdentity(targetSubject, "user@wwtrading.ma", "Ali", "Test", "fr");
        when(repository.findByKeycloakSubject(targetSubject)).thenReturn(Optional.of(user));

        // Missing special char & uppercase
        AdminResetPasswordRequest request = new AdminResetPasswordRequest(
            AdminResetPasswordRequest.ResetPasswordMode.DIRECT,
            "simplepass1234",
            "simplepass1234"
        );

        assertThatThrownBy(() -> service.resetPassword(authentication("ADMIN"), targetSubject, request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        verify(keycloakClient, never()).changeUserPassword(any(), any());
    }

    @Test
    void adminCanSendResetPasswordEmail() {
        String targetSubject = "kc-user-123";
        UserEntity user = UserEntity.fromIdentity(targetSubject, "user@wwtrading.ma", "Ali", "Test", "fr");
        when(repository.findByKeycloakSubject(targetSubject)).thenReturn(Optional.of(user));

        AdminResetPasswordRequest request = new AdminResetPasswordRequest(
            AdminResetPasswordRequest.ResetPasswordMode.EMAIL,
            null,
            null
        );

        AdminResetPasswordResponse response = service.resetPassword(authentication("ADMIN"), targetSubject, request);

        assertThat(response.success()).isTrue();
        verify(keycloakClient).executePasswordResetEmailForUser(targetSubject);
    }

    @Test
    void nonAdminCannotResetPassword() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest(
            AdminResetPasswordRequest.ResetPasswordMode.EMAIL,
            null,
            null
        );

        for (String role : List.of("MANAGER", "COMMERCIAL", "TECHNICIAN", "CLIENT")) {
            assertThatThrownBy(() -> service.resetPassword(authentication(role), "kc-user-123", request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
        }
        verify(keycloakClient, never()).changeUserPassword(any(), any());
        verify(keycloakClient, never()).executePasswordResetEmailForUser(any());
    }

    @Test
    void resetPasswordThrowsNotFoundForUnknownUser() {
        when(repository.findByKeycloakSubject("unknown-id")).thenReturn(Optional.empty());

        AdminResetPasswordRequest request = new AdminResetPasswordRequest(
            AdminResetPasswordRequest.ResetPasswordMode.EMAIL,
            null,
            null
        );

        assertThatThrownBy(() -> service.resetPassword(authentication("ADMIN"), "unknown-id", request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    private void assertForbidden(Authentication authentication, StaffRole targetRole) {
        assertThatThrownBy(() -> service.create(
            authentication, request(targetRole.name().toLowerCase() + "@example.test", targetRole)
        )).isInstanceOfSatisfying(ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode().value()).isEqualTo(403));
    }

    private CreateStaffUserRequest request(String email, StaffRole role) {
        return new CreateStaffUserRequest("Amina", "Admin", email, "StrongPass!123", role, null);
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken(
            "creator", "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}

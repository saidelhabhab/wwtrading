package ma.wwtrading.keycloak;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.events.Event;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientRoleEventListenerProviderTests {

    @Mock
    private KeycloakSession session;

    @Mock
    private RealmProvider realmProvider;

    @Mock
    private UserProvider userProvider;

    @Mock
    private RealmModel realm;

    @Mock
    private UserModel user;

    @Mock
    private RoleModel clientRole;

    @Mock
    private RoleModel staffRole;

    private ClientRoleEventListenerProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ClientRoleEventListenerProvider(session);
    }

    @Test
    void nativeRegistrationAssignsDirectClientRole() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "user-123");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "user-123")).thenReturn(user);
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.empty());
        when(realm.getRole("CLIENT")).thenReturn(clientRole);
        when(realm.getName()).thenReturn("wwtrading");
        when(user.getUsername()).thenReturn("client@example.com");
        when(user.getId()).thenReturn("user-123");

        provider.onEvent(event);

        verify(user).grantRole(clientRole);
    }

    @Test
    void idpFirstLoginAssignsDirectClientRole() {
        Event event = createEvent(EventType.IDENTITY_PROVIDER_FIRST_LOGIN, "wwtrading", "user-google-123");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "user-google-123")).thenReturn(user);
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.empty());
        when(realm.getRole("CLIENT")).thenReturn(clientRole);
        when(realm.getName()).thenReturn("wwtrading");
        when(user.getUsername()).thenReturn("google@example.com");
        when(user.getId()).thenReturn("user-google-123");

        provider.onEvent(event);

        verify(user).grantRole(clientRole);
    }

    @Test
    void existingDirectClientRoleIsNotGrantedAgain() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "user-123");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "user-123")).thenReturn(user);
        when(clientRole.getName()).thenReturn("CLIENT");
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.of(clientRole));
        when(realm.getRole("CLIENT")).thenReturn(clientRole);

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
    }

    @Test
    void staffUserWithAdminRoleDoesNotReceiveClientRole() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "staff-admin");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "staff-admin")).thenReturn(user);
        when(staffRole.getName()).thenReturn("ADMIN");
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.of(staffRole));

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
    }

    @Test
    void staffUserWithManagerRoleDoesNotReceiveClientRole() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "staff-manager");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "staff-manager")).thenReturn(user);
        when(staffRole.getName()).thenReturn("MANAGER");
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.of(staffRole));

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
    }

    @Test
    void staffUserWithCommercialRoleDoesNotReceiveClientRole() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "staff-commercial");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "staff-commercial")).thenReturn(user);
        when(staffRole.getName()).thenReturn("COMMERCIAL");
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.of(staffRole));

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
    }

    @Test
    void staffUserWithTechnicianRoleDoesNotReceiveClientRole() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "staff-technician");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "staff-technician")).thenReturn(user);
        when(staffRole.getName()).thenReturn("TECHNICIAN");
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.of(staffRole));

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
    }

    @Test
    void unknownRealmIsIgnoredSafely() {
        Event event = createEvent(EventType.REGISTER, "missing-realm", "user-123");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("missing-realm")).thenReturn(null);

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
        verify(session, never()).users();
    }

    @Test
    void unknownUserIsIgnoredSafely() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "ghost-user");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "ghost-user")).thenReturn(null);

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
    }

    @Test
    void missingClientRoleInRealmIsIgnoredSafely() {
        Event event = createEvent(EventType.REGISTER, "wwtrading", "user-123");

        when(session.realms()).thenReturn(realmProvider);
        when(realmProvider.getRealm("wwtrading")).thenReturn(realm);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserById(realm, "user-123")).thenReturn(user);
        when(user.getRealmRoleMappingsStream()).thenAnswer(inv -> Stream.empty());
        when(realm.getRole("CLIENT")).thenReturn(null);

        provider.onEvent(event);

        verify(user, never()).grantRole(any());
    }

    @Test
    void otherEventTypesAreIgnored() {
        Event event = createEvent(EventType.LOGIN, "wwtrading", "user-123");

        provider.onEvent(event);

        verify(session, never()).realms();
        verify(session, never()).users();
    }

    @Test
    void providerFactoryReturnsCorrectId() {
        ClientRoleEventListenerProviderFactory factory = new ClientRoleEventListenerProviderFactory();
        assertThat(factory.getId()).isEqualTo("client-role-assigner");
        assertThat(factory.create(session)).isNotNull();
    }

    private Event createEvent(EventType type, String realmId, String userId) {
        Event event = new Event();
        event.setType(type);
        event.setRealmId(realmId);
        event.setUserId(userId);
        return event;
    }
}

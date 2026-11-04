package ma.wwtrading.keycloak;

import java.util.Set;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

public class ClientRoleEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(ClientRoleEventListenerProvider.class);
    private static final String CLIENT_ROLE = "CLIENT";
    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN");

    private final KeycloakSession session;

    public ClientRoleEventListenerProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        if (event == null) {
            return;
        }
        EventType type = event.getType();
        if (type == EventType.REGISTER || type == EventType.IDENTITY_PROVIDER_FIRST_LOGIN) {
            assignClientRole(event.getRealmId(), event.getUserId());
        }
    }

    private void assignClientRole(String realmId, String userId) {
        if (userId == null || realmId == null) {
            return;
        }
        RealmModel realm = session.realms().getRealm(realmId);
        if (realm == null) {
            return;
        }
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            return;
        }

        // Staff protection: check if user has any staff role
        boolean hasStaffRole = user.getRealmRoleMappingsStream()
            .anyMatch(role -> role != null && STAFF_ROLES.contains(role.getName()));
        if (hasStaffRole) {
            LOG.debugf("Skipping CLIENT role assignment for staff user: %s", user.getUsername());
            return;
        }

        RoleModel clientRole = realm.getRole(CLIENT_ROLE);
        if (clientRole != null) {
            boolean hasDirectClientRole = user.getRealmRoleMappingsStream()
                .anyMatch(role -> role != null && CLIENT_ROLE.equals(role.getName()));
            if (!hasDirectClientRole) {
                user.grantRole(clientRole);
                LOG.infof("Direct CLIENT realm role assigned to user: %s (id: %s) in realm: %s",
                    user.getUsername(), user.getId(), realm.getName());
            }
        } else {
            LOG.warnf("CLIENT realm role not found in realm: %s", realm.getName());
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
    }

    @Override
    public void close() {
    }
}

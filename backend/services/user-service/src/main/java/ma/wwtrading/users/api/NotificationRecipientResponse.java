package ma.wwtrading.users.api;

import java.util.Set;

public record NotificationRecipientResponse(
        String keycloakSubject,
        String email,
        boolean enabled,
        Set<String> roles
) {
}

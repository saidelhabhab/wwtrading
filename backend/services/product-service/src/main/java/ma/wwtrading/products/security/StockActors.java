package ma.wwtrading.products.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Resolves a stable human-readable actor identifier from the current
 * authenticated principal, used for stock movement auditing.
 */
public final class StockActors {

    public static final String SYSTEM_ACTOR = "system";

    private StockActors() {
    }

    public static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return SYSTEM_ACTOR;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
            String subject = jwt.getSubject();
            return (subject == null || subject.isBlank()) ? SYSTEM_ACTOR : subject;
        }
        return authentication.getName();
    }
}

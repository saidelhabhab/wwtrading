package ma.wwtrading.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class KeycloakRealmRoles {

    private static final Set<String> BUSINESS_ROLES = Set.of(
        "ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN", "CLIENT"
    );

    private KeycloakRealmRoles() {
    }

    public static Collection<GrantedAuthority> authorities(Jwt jwt) {
        return roles(jwt).stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .map(GrantedAuthority.class::cast)
            .toList();
    }

    public static Set<String> roles(Jwt jwt) {
        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
            return Set.of();
        }
        Object rolesClaim = realmAccess.get("roles");
        if (!(rolesClaim instanceof Collection<?> values)) {
            return Set.of();
        }

        Set<String> roles = new LinkedHashSet<>();
        values.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(role -> !role.isBlank())
            .filter(BUSINESS_ROLES::contains)
            .forEach(roles::add);
        return Set.copyOf(roles);
    }
}

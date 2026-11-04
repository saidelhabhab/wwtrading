package ma.wwtrading.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityUtilitiesTests {

    @Test
    void acceptsRequiredAudienceAndExtractsRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject("user-1")
            .audience(List.of("wwtrading-api"))
            .claim("realm_access", Map.of("roles", List.of("CLIENT", "TECHNICIAN", "offline_access")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        assertThat(new AudienceValidator("wwtrading-api").validate(jwt).hasErrors()).isFalse();
        assertThat(KeycloakRealmRoles.roles(jwt)).containsExactlyInAnyOrder("CLIENT", "TECHNICIAN");
        assertThat(KeycloakRealmRoles.authorities(jwt))
            .extracting("authority")
            .containsExactlyInAnyOrder("ROLE_CLIENT", "ROLE_TECHNICIAN");
    }

    @Test
    void rejectsMissingAudience() {
        Jwt jwt = Jwt.withTokenValue("test")
            .header("alg", "none")
            .audience(List.of("another-api"))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        assertThat(new AudienceValidator("wwtrading-api").validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void ignoresRolesOutsideTheBusinessAllowlist() {
        Jwt jwt = Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject("user-1")
            .claim("realm_access", Map.of("roles", List.of("CLIENT", "offline_access", "unknown-role")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();

        assertThat(KeycloakRealmRoles.roles(jwt)).containsExactly("CLIENT");
        assertThat(KeycloakRealmRoles.authorities(jwt))
            .extracting("authority")
            .containsExactly("ROLE_CLIENT");
    }
}

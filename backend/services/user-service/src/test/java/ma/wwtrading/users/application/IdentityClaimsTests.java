package ma.wwtrading.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class IdentityClaimsTests {

    @Test
    void mapsTrustedIdentityClaims() {
        Jwt jwt = jwtWithEmail("client@example.test");

        IdentityClaims claims = IdentityClaims.from(jwt);

        assertThat(claims.subject()).isEqualTo("keycloak-user-1");
        assertThat(claims.email()).isEqualTo("client@example.test");
        assertThat(claims.firstName()).isEqualTo("Amina");
    }

    @Test
    void rejectsInvalidEmailClaim() {
        assertThatThrownBy(() -> IdentityClaims.from(jwtWithEmail("invalid")))
            .isInstanceOf(MissingIdentityClaimException.class);
    }

    private Jwt jwtWithEmail(String email) {
        return Jwt.withTokenValue("test")
            .header("alg", "none")
            .subject("keycloak-user-1")
            .claim("email", email)
            .claim("given_name", "Amina")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
    }
}

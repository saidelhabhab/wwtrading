package ma.wwtrading.users.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTests {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtAuthenticationConverter_extractsRealmRolesAsGrantedAuthorities() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .claim("sub", "client-123")
            .claim("realm_access", Map.of("roles", List.of("CLIENT", "COMMERCIAL", "UNKNOWN_ROLE")))
            .build();

        var auth = securityConfig.jwtAuthenticationConverter().convert(jwt);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactlyInAnyOrder("ROLE_CLIENT", "ROLE_COMMERCIAL");
    }
}


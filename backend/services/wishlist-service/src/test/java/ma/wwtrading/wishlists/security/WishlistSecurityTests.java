package ma.wwtrading.wishlists.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import ma.wwtrading.wishlists.api.WishlistController;
import ma.wwtrading.wishlists.application.WishlistApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ContextConfiguration(classes = {WishlistController.class, SecurityConfig.class})
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "wwtrading.security.issuer-uri=https://identity.example/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=https://identity.example/realms/wwtrading/protocol/openid-connect/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class WishlistSecurityTests {

    private static final String ISSUER = "https://identity.example/realms/wwtrading";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityConfig securityConfig;

    @MockitoBean
    private WishlistApplicationService service;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/wishlist"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRolesOutsideWishlistAllowlist() throws Exception {
        mockMvc.perform(get("/api/wishlist").with(jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "COMMERCIAL", "CLIENT"})
    void allowsConfiguredWishlistRoles(String role) throws Exception {
        mockMvc.perform(get("/api/wishlist").with(jwt()
                .jwt(token -> token.subject("customer-123"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk());
    }

    @Test
    void propagatesJwtSubjectAsWishlistOwner() throws Exception {
        mockMvc.perform(get("/api/wishlist").with(jwt()
                .jwt(token -> token.subject("customer-123"))
                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isOk());

        verify(service).getOrCreate("customer-123");
    }

    @Test
    void validatesIssuerAudienceAndStrictRealmRoles() {
        var validator = securityConfig.jwtValidator(ISSUER, "wwtrading-api");

        assertThat(validator.validate(token(ISSUER, List.of("wwtrading-api"))).hasErrors()).isFalse();
        assertThat(validator.validate(token(ISSUER, List.of("another-api"))).hasErrors()).isTrue();
        assertThat(validator.validate(token("https://wrong.example", List.of("wwtrading-api"))).hasErrors()).isTrue();

        var authentication = securityConfig.jwtAuthenticationConverter().convert(
            tokenWithRoles(List.of("CLIENT", "UNTRUSTED"))
        );
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .containsExactly("ROLE_CLIENT");
    }

    private Jwt token(String issuer, List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("customer-123")
            .issuer(issuer)
            .audience(audience)
            .issuedAt(now.minusSeconds(30))
            .expiresAt(now.plusSeconds(300))
            .build();
    }

    private Jwt tokenWithRoles(List<String> roles) {
        return Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("customer-123")
            .claim("realm_access", Map.of("roles", roles))
            .build();
    }
}

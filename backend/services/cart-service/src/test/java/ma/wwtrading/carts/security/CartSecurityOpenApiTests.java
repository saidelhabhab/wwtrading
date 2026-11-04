package ma.wwtrading.carts.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import ma.wwtrading.carts.api.CartController;
import ma.wwtrading.carts.api.CartResponse;
import ma.wwtrading.carts.api.OpenApiConfig;
import ma.wwtrading.carts.application.CartApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ContextConfiguration(classes = {
    CartController.class,
    SecurityConfig.class,
    OpenApiConfig.class
})
@TestPropertySource(properties = {
    "wwtrading.security.issuer-uri=https://issuer.example.test/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=https://issuer.example.test/realms/wwtrading/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class CartSecurityOpenApiTests {

    private static final String ISSUER = "https://issuer.example.test/realms/wwtrading";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CartApplicationService service;

    @Test
    void requiresAuthenticationAndRejectsTechnicianRole() throws Exception {
        mvc.perform(get("/api/cart"))
            .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/cart").with(jwt()
                .jwt(token -> token.subject("technician-1"))
                .authorities(new SimpleGrantedAuthority("ROLE_TECHNICIAN"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsConfiguredRolesAndPropagatesJwtSubjectAsOwner() throws Exception {
        when(service.getOrCreate("customer-1"))
            .thenReturn(new CartResponse(UUID.randomUUID(), List.of(), null, null));

        for (String role : List.of("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")) {
            mvc.perform(get("/api/cart").with(jwt()
                    .jwt(token -> token.subject("customer-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().isOk());
        }

        verify(service, org.mockito.Mockito.times(4)).getOrCreate("customer-1");
    }

    @Test
    void validatesIssuerAndAudience() {
        var validator = new SecurityConfig().jwtValidator(ISSUER, "wwtrading-api");

        assertThat(validator.validate(token(ISSUER, List.of("wwtrading-api"))).hasErrors()).isFalse();
        assertThat(validator.validate(token("https://wrong.example.test", List.of("wwtrading-api"))).hasErrors()).isTrue();
        assertThat(validator.validate(token(ISSUER, List.of("another-api"))).hasErrors()).isTrue();
    }

    @Test
    void extractsOnlyStrictWwtradingRealmRoles() {
        Jwt token = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("customer-1")
            .claim("realm_access", Map.of("roles", List.of("CLIENT", "UNTRUSTED")))
            .build();

        var authentication = new SecurityConfig().jwtAuthenticationConverter().convert(token);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .containsExactly("ROLE_CLIENT");
    }

    @Test
    void declaresBearerJwtOpenApiScheme() {
        var openApi = new OpenApiConfig().cartServiceOpenApi();
        var scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);

        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity()).isNotEmpty();
    }

    private Jwt token(String issuer, List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("customer-1")
            .issuer(issuer)
            .audience(audience)
            .issuedAt(now.minusSeconds(30))
            .expiresAt(now.plusSeconds(300))
            .build();
    }
}

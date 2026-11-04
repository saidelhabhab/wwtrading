package ma.wwtrading.products.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import ma.wwtrading.security.AudienceValidator;
import ma.wwtrading.security.KeycloakRealmRoles;

@Configuration
public class SecurityConfig {

    private static final String[] PRODUCT_MANAGERS = {"ADMIN", "MANAGER", "COMMERCIAL"};
    private static final String[] PRODUCT_READERS = {"ADMIN", "MANAGER", "COMMERCIAL", "CLIENT"};
    private static final String[] IMAGE_MANAGERS = {"ADMIN", "MANAGER"};
    /**
     * Stock mutations (IN/OUT/ADJUST/RESERVE/RELEASE/threshold) are
     * ADMIN+MANAGER only.
     */
    private static final String[] STOCK_MANAGERS = {"ADMIN", "MANAGER"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/products").hasAnyRole(PRODUCT_MANAGERS)
                .requestMatchers(HttpMethod.POST, "/api/products/*/images").hasAnyRole(IMAGE_MANAGERS)
                .requestMatchers(HttpMethod.PUT, "/api/products/*/images/**").hasAnyRole(IMAGE_MANAGERS)
                .requestMatchers(HttpMethod.DELETE, "/api/products/*/images/*").hasAnyRole(IMAGE_MANAGERS)
                .requestMatchers(HttpMethod.POST, "/api/products/*/activate", "/api/products/*/deactivate")
                .hasAnyRole(PRODUCT_MANAGERS)
                .requestMatchers(HttpMethod.PUT, "/api/products/*").hasAnyRole(PRODUCT_MANAGERS)
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/stock", "/api/stock/*").hasAnyRole(PRODUCT_MANAGERS)
                .requestMatchers(HttpMethod.GET, "/api/stock/*/movements")
                .hasAnyRole(STOCK_MANAGERS)
                .requestMatchers("/api/stock/**").hasAnyRole(STOCK_MANAGERS)
                .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt
                -> jwt.jwtAuthenticationConverter(converter)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(KeycloakRealmRoles::authorities);
        return converter;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtValidator(
            @Value("${wwtrading.security.issuer-uri}") String issuerUri,
            @Value("${wwtrading.security.audience}") String audience
    ) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(audience)
        );
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${wwtrading.security.jwk-set-uri}") String jwkSetUri,
            OAuth2TokenValidator<Jwt> jwtValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtValidator);
        return decoder;
    }
}

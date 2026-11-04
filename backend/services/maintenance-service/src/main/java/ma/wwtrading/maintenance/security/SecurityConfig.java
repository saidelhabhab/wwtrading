package ma.wwtrading.maintenance.security;

import ma.wwtrading.security.AudienceValidator;
import ma.wwtrading.security.KeycloakRealmRoles;
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

@Configuration
public class SecurityConfig {

    private static final String[] MAINTENANCE_CREATORS  = {"ADMIN", "MANAGER", "COMMERCIAL", "CLIENT"};
    private static final String[] MAINTENANCE_READERS   = {"ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN"};
    private static final String[] MAINTENANCE_WORKERS   = {"ADMIN", "MANAGER", "TECHNICIAN"};
    private static final String[] MAINTENANCE_CANCELLERS = {"ADMIN", "MANAGER"};
    private static final String[] MAINTENANCE_ASSIGNERS = {"ADMIN", "MANAGER"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/analytics/maintenance")
                    .hasAnyRole("ADMIN", "MANAGER", "CLIENT", "TECHNICIAN")
                // --- Technician inbox (server-side filtered by JWT subject) ---
                .requestMatchers(HttpMethod.GET, "/api/maintenance/my-assignments")
                    .hasAnyRole("TECHNICIAN", "ADMIN", "MANAGER")
                // --- Client self-service ---
                .requestMatchers(HttpMethod.GET, "/api/maintenance/my-requests")
                    .hasAnyRole("CLIENT", "ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN")
                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/attachments")
                    .hasRole("CLIENT")
                .requestMatchers(HttpMethod.GET,
                    "/api/maintenance/*/attachments", "/api/maintenance/*/attachments/**")
                    .hasAnyRole("CLIENT", "ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN")
                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/submit")
                    .hasAnyRole("CLIENT", "ADMIN", "MANAGER")
                // --- Technician workflow decisions (Accept / Reject / Start / Intervention update) ---
                .requestMatchers(HttpMethod.POST,
                    "/api/maintenance/*/accept", "/api/maintenance/*/reject", "/api/maintenance/*/start")
                    .hasRole("TECHNICIAN")
                .requestMatchers(HttpMethod.PATCH, "/api/maintenance/*/intervention")
                    .hasRole("TECHNICIAN")
                // --- Phase T4: Spare Part Requests ---
                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/parts")
                    .hasRole("TECHNICIAN")
                .requestMatchers(HttpMethod.GET, "/api/maintenance/*/parts")
                    .hasAnyRole(MAINTENANCE_READERS)
                .requestMatchers(HttpMethod.POST,
                    "/api/maintenance/*/parts/*/approve", "/api/maintenance/*/parts/*/reject")
                    .hasAnyRole(MAINTENANCE_ASSIGNERS)
                .requestMatchers(HttpMethod.POST,
                    "/api/maintenance/*/parts/*/wait", "/api/maintenance/*/resume")
                    .hasRole("TECHNICIAN")
                // --- Phase T5: Completion & Client Confirmation ---
                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/submit-completion")
                    .hasRole("TECHNICIAN")
                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/confirm-completion")
                    .hasAnyRole("CLIENT", "ADMIN", "MANAGER")
                // --- Status transitions ---
                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/complete")
                    .hasAnyRole(MAINTENANCE_WORKERS)
                .requestMatchers(HttpMethod.POST, "/api/maintenance/*/cancel")
                    .hasAnyRole(MAINTENANCE_CANCELLERS)
                // --- Assignment ---
                .requestMatchers(HttpMethod.PUT, "/api/maintenance/*/technician/*")
                    .hasAnyRole(MAINTENANCE_ASSIGNERS)
                .requestMatchers(HttpMethod.DELETE, "/api/maintenance/*/technician")
                    .hasAnyRole(MAINTENANCE_ASSIGNERS)
                // --- Create ---
                .requestMatchers(HttpMethod.POST, "/api/maintenance").hasAnyRole(MAINTENANCE_CREATORS)
                // --- Read ---
                .requestMatchers(HttpMethod.GET, "/api/maintenance/*")
                    .hasAnyRole("CLIENT", "ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN")
                .requestMatchers(HttpMethod.GET, "/api/maintenance")
                    .hasAnyRole(MAINTENANCE_READERS)
                .requestMatchers(HttpMethod.GET, "/api/maintenance/*/history")
                    .hasAnyRole(MAINTENANCE_READERS)
                .anyRequest().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt ->
                jwt.jwtAuthenticationConverter(converter)))
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

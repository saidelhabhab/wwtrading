package ma.wwtrading.quotations.security;

import ma.wwtrading.security.AudienceValidator;
import ma.wwtrading.security.KeycloakRealmRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/quotations").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/quotations/my-quotations").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.PUT, "/api/quotations/*/cancel").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.PUT, "/api/quotations/*/decision").hasRole("CLIENT")
                .requestMatchers(HttpMethod.PUT, "/api/quotations/*/status").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.POST, "/api/prospects").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.GET, "/api/prospects", "/api/prospects/*").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.PUT, "/api/prospects/*").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.DELETE, "/api/prospects/*").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.GET, "/api/reports/commercial", "/api/reports/commercial/**")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.GET, "/api/analytics/quotations")
                    .hasAnyRole("ADMIN", "MANAGER", "CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/quotations/*/pdf").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/quotations").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.GET, "/api/quotations/*").hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .anyRequest().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt ->
                jwt.jwtAuthenticationConverter(converter)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(KeycloakRealmRoles::authorities);
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(
        @Value("${wwtrading.security.issuer-uri:http://localhost:8180/realms/wwtrading}") String issuerUri,
        @Value("${wwtrading.security.jwk-set-uri:http://keycloak:8080/realms/wwtrading/protocol/openid-connect/certs}") String jwkSetUri,
        @Value("${wwtrading.security.audience:wwtrading-api}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri),
            new AudienceValidator(audience)
        ));
        return decoder;
    }
}

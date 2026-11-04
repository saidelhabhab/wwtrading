package ma.wwtrading.users.security;

import ma.wwtrading.security.AudienceValidator;
import ma.wwtrading.security.KeycloakRealmRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter,
            InternalServiceAuthenticationFilter internalServiceFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests
                .requestMatchers(
                        "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
                ).permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/analytics/clients")
                .hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users")
                .hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users")
                .hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users/*/reset-password")
                .hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/users/*/password")
                .hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users/*/reset-password-email")
                .hasRole("ADMIN")
                .requestMatchers("/api/v1/users/**")
                .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN", "CLIENT")
                .requestMatchers("/api/internal/notification-recipients").hasRole("NOTIFICATION_SERVICE")
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/forgot-password").permitAll()
                .requestMatchers("/api/v1/auth/change-password").authenticated()
                .requestMatchers("/api/v1/clients", "/api/v1/clients/**")
                .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterBefore(internalServiceFilter, BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(
                org.springframework.security.config.http.SessionCreationPolicy.STATELESS
        ))
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
            @Value("${wwtrading.security.issuer-uri}") String issuerUri,
            @Value("${wwtrading.security.jwk-set-uri}") String jwkSetUri,
            @Value("${wwtrading.security.audience}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(audience)
        ));
        return decoder;
    }
}

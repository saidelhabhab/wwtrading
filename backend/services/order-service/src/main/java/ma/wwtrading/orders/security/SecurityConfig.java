package ma.wwtrading.orders.security;

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
                .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/orders/payment-banks",
                        "/api/orders/payment-banks/logos/**",
                        "/api/orders/delivery-settings",
                        "/api/orders/guest/track",
                        "/api/orders/guest/track/transfer-proof").permitAll()
                .requestMatchers(HttpMethod.POST,
                        "/api/orders/guest/pricing",
                        "/api/orders/guest",
                        "/api/orders/guest/track/transfer-proof",
                        "/api/orders/guest/track/second-installment/method").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/analytics/orders")
                    .hasAnyRole("ADMIN", "MANAGER", "CLIENT")
                .requestMatchers(HttpMethod.POST, "/api/orders")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.POST, "/api/orders/pricing")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.POST, "/api/orders/claim-guest-orders")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.POST,
                        "/api/orders/*/tracking-token/revoke",
                        "/api/orders/*/tracking-token/regenerate")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/transfer-proof")
                    .hasRole("CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/orders")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.GET, "/api/orders/my-orders")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/orders/**")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/cancel")
                    .hasRole("CLIENT")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/second-installment/method")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL", "CLIENT")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/payment/confirm")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/confirm")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/process")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/ready", "/api/orders/*/complete")
                    .hasAnyRole("ADMIN", "MANAGER", "COMMERCIAL")
                .requestMatchers("/api/admin/payment-banks/**")
                    .hasRole("ADMIN")
                .requestMatchers("/api/admin/delivery-settings", "/api/admin/delivery-settings/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/orders/delivery-settings")
                    .hasRole("ADMIN")
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

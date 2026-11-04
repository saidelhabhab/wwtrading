package ma.wwtrading.gateway.security;

import java.util.List;
import java.security.Principal;

import ma.wwtrading.security.AudienceValidator;
import ma.wwtrading.security.KeycloakRealmRoles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.core.convert.converter.Converter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
        ServerHttpSecurity http,
        ReactiveJwtDecoder jwtDecoder,
        Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter
    ) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .cors(Customizer.withDefaults())
            .authorizeExchange(exchanges -> exchanges
            .pathMatchers("/actuator/health/**", "/actuator/info", "/api/public/**").permitAll()

            // Public authentication endpoints (registration, forgot-password)
            .pathMatchers(HttpMethod.POST,
                "/api/v1/auth/register",
                "/api/v1/auth/forgot-password").permitAll()

            // Public product catalogue
            .pathMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()

            // Public guest checkout, delivery settings, payment banks, order tracking, and quotation requests
            .pathMatchers(HttpMethod.GET,
                "/api/orders/payment-banks",
                "/api/orders/payment-banks/logos/**",
                "/api/orders/delivery-settings",
                "/api/orders/guest/track",
                "/api/orders/guest/track/transfer-proof").permitAll()
            .pathMatchers(HttpMethod.POST,
                "/api/orders/guest/pricing",
                "/api/orders/guest",
                "/api/orders/guest/track/transfer-proof",
                "/api/orders/guest/track/second-installment/method",
                "/api/quotations").permitAll()

            .pathMatchers(HttpMethod.OPTIONS).permitAll()

            // Everything else remains protected
            .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                .jwtDecoder(jwtDecoder)
                .jwtAuthenticationConverter(jwtAuthenticationConverter)))
            .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(
        @Value("${wwtrading.security.issuer-uri}") String issuerUri,
        @Value("${wwtrading.security.jwk-set-uri}") String jwkSetUri,
        @Value("${wwtrading.security.audience}") String audience
    ) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri),
            new AudienceValidator(audience)
        ));
        return decoder;
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(KeycloakRealmRoles::authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }

    @Bean
    KeyResolver authenticatedUserKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(Principal::getName)
            .switchIfEmpty(Mono.just("anonymous"));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
        @Value("${wwtrading.security.allowed-origins:http://localhost:4201}") List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID", "X-Tracking-Token"
        ));
        configuration.setExposedHeaders(List.of("Location", "X-Correlation-ID", "Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

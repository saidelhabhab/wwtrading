package ma.wwtrading.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SecurityHeadersFilterTests {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void addsApiSecurityHeadersAndHstsForForwardedHttps() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("http://localhost/api/v1/users/me")
                .header("X-Forwarded-Proto", "https")
                .build()
        );

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"))
            .isEqualTo(SecurityHeadersFilter.CONTENT_SECURITY_POLICY);
        assertThat(exchange.getResponse().getHeaders().getFirst("Strict-Transport-Security"))
            .isEqualTo("max-age=31536000; includeSubDomains");
    }
}

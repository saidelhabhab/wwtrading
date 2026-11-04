package ma.wwtrading.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdFilterTests {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void preservesAValidIncomingCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/public/status")
                .header(CorrelationIdFilter.HEADER_NAME, "quote-123")
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
            .isEqualTo("quote-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
            .isEqualTo("quote-123");
    }

    @Test
    void replacesAnUnsafeIncomingCorrelationId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/public/status")
                .header(CorrelationIdFilter.HEADER_NAME, "unsafe value\n")
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, current -> {
            forwarded.set(current);
            return Mono.empty();
        })).verifyComplete();

        String generated = forwarded.get().getRequest().getHeaders()
            .getFirst(CorrelationIdFilter.HEADER_NAME);
        assertThat(generated).isNotBlank().doesNotContain("unsafe");
    }
}

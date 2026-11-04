package ma.wwtrading.gateway.security;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PublicQuotationRateLimitFilterTests {

    @Test
    @DisplayName("Allows requests below capacity")
    void allowsRequestsBelowCapacity() {
        PublicQuotationRateLimitFilter filter = new PublicQuotationRateLimitFilter(3, 60, true);
        GatewayFilterChain chain = exchange -> Mono.empty();

        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/quotations")
                    .remoteAddress(new InetSocketAddress("192.168.1.10", 12345))
                    .build()
            );
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    @DisplayName("Rejects request exceeding capacity with HTTP 429 and Retry-After header")
    void rejectsExcessiveRequestsWith429() {
        PublicQuotationRateLimitFilter filter = new PublicQuotationRateLimitFilter(2, 60, true);
        GatewayFilterChain chain = exchange -> Mono.empty();

        // 1st request -> allowed
        MockServerWebExchange req1 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/quotations")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build()
        );
        StepVerifier.create(filter.filter(req1, chain)).verifyComplete();
        assertThat(req1.getResponse().getStatusCode()).isNull();

        // 2nd request -> allowed
        MockServerWebExchange req2 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/quotations")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build()
        );
        StepVerifier.create(filter.filter(req2, chain)).verifyComplete();
        assertThat(req2.getResponse().getStatusCode()).isNull();

        // 3rd request -> rejected HTTP 429
        MockServerWebExchange req3 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/quotations")
                .remoteAddress(new InetSocketAddress("10.0.0.5", 12345))
                .build()
        );
        StepVerifier.create(filter.filter(req3, chain)).verifyComplete();
        assertThat(req3.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(req3.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(req3.getResponse().getHeaders().getContentType().toString()).contains("application/problem+json");
    }

    @Test
    @DisplayName("Different client IPs do not share rate limit bucket")
    void isolatesDifferentClientIps() {
        PublicQuotationRateLimitFilter filter = new PublicQuotationRateLimitFilter(1, 60, true, true);
        GatewayFilterChain chain = exchange -> Mono.empty();

        // IP 1 uses its token
        MockServerWebExchange ip1Req1 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/quotations")
                .header("X-Forwarded-For", "41.248.10.20")
                .build()
        );
        StepVerifier.create(filter.filter(ip1Req1, chain)).verifyComplete();
        assertThat(ip1Req1.getResponse().getStatusCode()).isNull();

        // IP 2 is not affected and succeeds
        MockServerWebExchange ip2Req1 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/quotations")
                .header("X-Forwarded-For", "41.248.10.30")
                .build()
        );
        StepVerifier.create(filter.filter(ip2Req1, chain)).verifyComplete();
        assertThat(ip2Req1.getResponse().getStatusCode()).isNull();

        // IP 1 tries again -> rejected
        MockServerWebExchange ip1Req2 = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/quotations")
                .header("X-Forwarded-For", "41.248.10.20")
                .build()
        );
        StepVerifier.create(filter.filter(ip1Req2, chain)).verifyComplete();
        assertThat(ip1Req2.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Non-POST or non-quotation paths bypass rate limiter")
    void bypassesOtherPathsAndMethods() {
        PublicQuotationRateLimitFilter filter = new PublicQuotationRateLimitFilter(1, 60, true);
        GatewayFilterChain chain = exchange -> Mono.empty();

        // GET /api/quotations bypasses
        MockServerWebExchange getReq = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/quotations")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 1234))
                .build()
        );
        StepVerifier.create(filter.filter(getReq, chain)).verifyComplete();
        assertThat(getReq.getResponse().getStatusCode()).isNull();

        // POST /api/orders bypasses
        MockServerWebExchange ordersReq = MockServerWebExchange.from(
            MockServerHttpRequest.post("/api/orders")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 1234))
                .build()
        );
        StepVerifier.create(filter.filter(ordersReq, chain)).verifyComplete();
        assertThat(ordersReq.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Ignores client-supplied X-Forwarded-For when trustForwardedFor is false")
    void ignoresSpoofedXForwardedForByDefault() {
        PublicQuotationRateLimitFilter filter = new PublicQuotationRateLimitFilter(5, 60, true, false);

        MockServerHttpRequest req = MockServerHttpRequest.post("/api/quotations")
            .header("X-Forwarded-For", "203.0.113.195")
            .remoteAddress(new InetSocketAddress("10.0.0.1", 1234))
            .build();

        // Must use network remote address, strictly ignoring spoofed header
        assertThat(filter.resolveClientIp(req)).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("Extracts first valid IP from X-Forwarded-For when proxy is trusted")
    void resolvesClientIpFromTrustedProxy() {
        PublicQuotationRateLimitFilter filter = new PublicQuotationRateLimitFilter(5, 60, true, true);

        // Multiple proxies in X-Forwarded-For
        MockServerHttpRequest reqWithProxy = MockServerHttpRequest.post("/api/quotations")
            .header("X-Forwarded-For", "196.200.150.12, 10.0.0.1, 172.16.0.1")
            .build();
        assertThat(filter.resolveClientIp(reqWithProxy)).isEqualTo("196.200.150.12");

        // Invalid X-Forwarded-For falls back to remoteAddress
        MockServerHttpRequest reqInvalidProxy = MockServerHttpRequest.post("/api/quotations")
            .header("X-Forwarded-For", "<script>alert(1)</script>")
            .remoteAddress(new InetSocketAddress("10.10.10.10", 9999))
            .build();
        assertThat(filter.resolveClientIp(reqInvalidProxy)).isEqualTo("10.10.10.10");

        // Completely missing falls back to unknown
        MockServerHttpRequest reqEmpty = MockServerHttpRequest.post("/api/quotations").build();
        assertThat(filter.resolveClientIp(reqEmpty)).isEqualTo("unknown");
    }
}

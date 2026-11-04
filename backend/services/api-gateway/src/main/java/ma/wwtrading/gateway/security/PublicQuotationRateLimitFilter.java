package ma.wwtrading.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate limiting filter for public quotation requests (POST /api/quotations).
 * Uses an in-memory token-bucket algorithm backed by Caffeine cache per client IP.
 */
@Component
public class PublicQuotationRateLimitFilter implements GlobalFilter, Ordered {

    private static final String TARGET_PATH = "/api/quotations";
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    );
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^[0-9a-fA-F:]{2,39}$"
    );

    private final int capacity;
    private final int refillSeconds;
    private final boolean enabled;
    private final boolean trustForwardedFor;
    private final Cache<String, TokenBucket> buckets;

    public PublicQuotationRateLimitFilter() {
        this(10, 60, true, false);
    }

    @Autowired
    public PublicQuotationRateLimitFilter(
        @Value("${wwtrading.gateway.rate-limit.quotations.capacity:10}") int capacity,
        @Value("${wwtrading.gateway.rate-limit.quotations.refill-seconds:60}") int refillSeconds,
        @Value("${wwtrading.gateway.rate-limit.quotations.enabled:true}") boolean enabled,
        @Value("${wwtrading.gateway.rate-limit.quotations.trust-forwarded-for:false}") boolean trustForwardedFor
    ) {
        this.capacity = Math.max(1, capacity);
        this.refillSeconds = Math.max(1, refillSeconds);
        this.enabled = enabled;
        this.trustForwardedFor = trustForwardedFor;
        this.buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(15))
            .maximumSize(50_000)
            .build();
    }

    public PublicQuotationRateLimitFilter(int capacity, int refillSeconds, boolean enabled) {
        this(capacity, refillSeconds, enabled, false);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        if (!isQuotationCreation(request)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(request);
        TokenBucket bucket = buckets.get(clientIp, key -> new TokenBucket(capacity, refillSeconds));

        long retryAfterSeconds = bucket.tryConsume();
        if (retryAfterSeconds == 0) {
            return chain.filter(exchange);
        }

        return rejectRateLimited(exchange, retryAfterSeconds);
    }

    private boolean isQuotationCreation(ServerHttpRequest request) {
        if (request.getMethod() != HttpMethod.POST) {
            return false;
        }
        String path = request.getPath().pathWithinApplication().value();
        return TARGET_PATH.equals(path) || (TARGET_PATH + "/").equals(path);
    }

    String resolveClientIp(ServerHttpRequest request) {
        if (trustForwardedFor) {
            String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String candidate = xForwardedFor.split(",")[0].trim();
                if (isValidIp(candidate)) {
                    return candidate;
                }
            }
        }

        // When not behind a verified proxy or proxy header is missing, rely on the actual network remote address
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            String hostAddress = remoteAddress.getAddress().getHostAddress();
            if (hostAddress != null && isValidIp(hostAddress)) {
                return hostAddress;
            }
        }

        return "unknown";
    }

    private boolean isValidIp(String ip) {
        return ip != null && (IPV4_PATTERN.matcher(ip).matches() || IPV6_PATTERN.matcher(ip).matches());
    }

    private Mono<Void> rejectRateLimited(ServerWebExchange exchange, long retryAfterSeconds) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String problemJson = """
            {
              "type": "https://wwtrading.ma/errors/rate-limit-exceeded",
              "title": "Too Many Requests",
              "status": 429,
              "detail": "Limite de création de devis atteinte. Veuillez patienter avant de renouveler votre demande.",
              "retryAfterSeconds": %d
            }
            """.formatted(retryAfterSeconds);

        byte[] bytes = problemJson.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    static class TokenBucket {
        private final double capacity;
        private final double refillRatePerNano;
        private double availableTokens;
        private long lastRefillNanos;

        TokenBucket(int capacity, int refillSeconds) {
            this.capacity = capacity;
            this.availableTokens = capacity;
            this.refillRatePerNano = (double) capacity / ((double) refillSeconds * 1_000_000_000.0);
            this.lastRefillNanos = System.nanoTime();
        }

        /**
         * @return 0 if token was consumed successfully; otherwise the retry-after seconds to wait.
         */
        synchronized long tryConsume() {
            long now = System.nanoTime();
            long elapsedNanos = Math.max(0, now - lastRefillNanos);
            availableTokens = Math.min(capacity, availableTokens + (elapsedNanos * refillRatePerNano));
            lastRefillNanos = now;

            if (availableTokens >= 1.0) {
                availableTokens -= 1.0;
                return 0;
            }

            double missingTokens = 1.0 - availableTokens;
            double waitNanos = missingTokens / refillRatePerNano;
            return Math.max(1, (long) Math.ceil(waitNanos / 1_000_000_000.0));
        }
    }
}

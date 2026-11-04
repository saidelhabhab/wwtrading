package ma.wwtrading.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";
    static final String CONTENT_SECURITY_POLICY = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Permissions-Policy", PERMISSIONS_POLICY);
        headers.set("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        if (isHttps(exchange)) {
            headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        return chain.filter(exchange);
    }

    private boolean isHttps(ServerWebExchange exchange) {
        String forwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        return exchange.getRequest().getURI().getScheme().equalsIgnoreCase("https")
            || "https".equalsIgnoreCase(forwardedProto);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

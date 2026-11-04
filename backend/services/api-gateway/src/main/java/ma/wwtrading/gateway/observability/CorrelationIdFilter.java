package ma.wwtrading.gateway.observability;

import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-ID";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String suppliedValue = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String correlationId = suppliedValue != null && SAFE_VALUE.matcher(suppliedValue).matches()
            ? suppliedValue
            : UUID.randomUUID().toString();

        ServerWebExchange correlatedExchange = exchange.mutate()
            .request(request -> request.headers(headers -> headers.set(HEADER_NAME, correlationId)))
            .build();

        correlatedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
        return chain.filter(correlatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

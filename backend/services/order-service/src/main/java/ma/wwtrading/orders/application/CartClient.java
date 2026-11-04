package ma.wwtrading.orders.application;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CartClient {

    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;

    public CartClient(RestClient.Builder restClientBuilder, DiscoveryClient discoveryClient) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
    }

    public Cart getCart(JwtAuthenticationToken authentication) {
        return execute(authentication, HttpMethod.GET, "/api/cart", Cart.class);
    }

    public void clearCart(JwtAuthenticationToken authentication) {
        execute(authentication, HttpMethod.DELETE, "/api/cart/items", Void.class);
    }

    private <T> T execute(JwtAuthenticationToken authentication, HttpMethod method, String path, Class<T> responseType) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ServiceInstance instance = discoveryClient.getInstances("cart-service").stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cart service unavailable"));
        try {
            ResponseEntity<T> response = restClientBuilder.clone()
                .baseUrl(instance.getUri().toString())
                .build()
                .method(method)
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                .retrieve()
                .toEntity(responseType);
            return response.getBody();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                || exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cart service authorization failed");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cart service request failed");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cart service unavailable");
        }
    }

    public record Cart(UUID id, List<CartItem> items) {
    }

    public record CartItem(UUID id, String productReference, int quantity) {
    }
}
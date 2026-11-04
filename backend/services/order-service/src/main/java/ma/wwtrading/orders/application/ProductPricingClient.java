package ma.wwtrading.orders.application;

import java.math.BigDecimal;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductPricingClient {

    private static final String PRODUCT_SERVICE = "product-service";
    private static final String PRODUCT_PATH = "/api/products/reference/{reference}";

    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;

    public ProductPricingClient(RestClient.Builder restClientBuilder, DiscoveryClient discoveryClient) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
    }

    public ProductPrice getPrice(JwtAuthenticationToken authentication, String productReference) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return fetchPrice(authentication.getToken().getTokenValue(), productReference);
    }

    public ProductPrice getPublicPrice(String productReference) {
        return fetchPrice(null, productReference);
    }

    private ProductPrice fetchPrice(String bearerToken, String productReference) {
        ServiceInstance instance = discoveryClient.getInstances(PRODUCT_SERVICE).stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Product service unavailable"
            ));

        try {
            RestClient.RequestHeadersSpec<?> requestSpec = restClientBuilder.clone()
                .baseUrl(instance.getUri().toString())
                .build()
                .get()
                .uri(PRODUCT_PATH, productReference);
            if (bearerToken != null && !bearerToken.isBlank()) {
                requestSpec = requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            ResponseEntity<ProductResponse> response = requestSpec
                .retrieve()
                .toEntity(ProductResponse.class);
            ProductResponse product = response.getBody();
            if (product == null || product.reference() == null || product.price() == null
                || product.price().signum() < 0 || !product.active()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid product pricing response");
            }
            return new ProductPrice(product.reference(), product.name(), product.price(), product.availableQuantity());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
            if (exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                || exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product pricing unavailable");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product pricing request failed");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable");
        }
    }

    public record ProductPrice(String productReference, String productName, BigDecimal unitPrice, Integer availableQuantity) {
        public ProductPrice(String productReference, BigDecimal unitPrice, Integer availableQuantity) {
            this(productReference, null, unitPrice, availableQuantity);
        }

        public ProductPrice(String productReference, BigDecimal unitPrice) {
            this(productReference, null, unitPrice, null);
        }
    }

    private record ProductResponse(String reference, String name, BigDecimal price, boolean active, Integer availableQuantity) {
    }
}

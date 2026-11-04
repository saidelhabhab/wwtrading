package ma.wwtrading.carts.application;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ProductAvailabilityClient {

    private static final String PRODUCT_SERVICE = "product-service";
    private static final String PRODUCT_PATH = "/api/products/reference/{reference}";

    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;

    public ProductAvailabilityClient(RestClient.Builder restClientBuilder, DiscoveryClient discoveryClient) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
    }

    public ProductAvailability getAvailability(String productReference) {
        ServiceInstance instance = discoveryClient.getInstances(PRODUCT_SERVICE).stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Product service unavailable"
            ));

        try {
            ResponseEntity<ProductResponse> response = restClientBuilder.clone()
                .baseUrl(instance.getUri().toString())
                .build()
                .get()
                .uri(PRODUCT_PATH, productReference)
                .retrieve()
                .toEntity(ProductResponse.class);
            ProductResponse product = response.getBody();
            if (product == null || !productReference.equals(product.reference())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid product response");
            }
            return new ProductAvailability(product.reference(), product.availableQuantity(), product.active());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Product availability request failed");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product service unavailable");
        }
    }

    public record ProductAvailability(String productReference, Integer availableQuantity, boolean active) {
    }

    private record ProductResponse(String reference, Integer availableQuantity, boolean active) {
    }
}

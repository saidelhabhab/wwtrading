package ma.wwtrading.quotations.application;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class QuotationProductClient {
    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;

    public QuotationProductClient(RestClient.Builder restClientBuilder, DiscoveryClient discoveryClient) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
    }

    public CatalogProduct getProduct(UUID productId) {
        var instance = discoveryClient.getInstances("product-service").stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Product catalog unavailable"));
        try {
            CatalogProduct product = restClientBuilder.clone().baseUrl(instance.getUri().toString()).build()
                .get().uri("/api/products/{id}", productId).retrieve().body(CatalogProduct.class);
            if (product == null || !productId.equals(product.id()) || product.reference() == null
                    || product.reference().isBlank() || product.name() == null || product.name().isBlank()
                    || product.price() == null || product.price().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid product catalog response");
            }
            if (!product.active()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is not available for quotation");
            }
            return product;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected product does not exist");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Product catalog unavailable");
        }
    }

    public record CatalogProduct(UUID id, String reference, String name, BigDecimal price, boolean active) { }
}

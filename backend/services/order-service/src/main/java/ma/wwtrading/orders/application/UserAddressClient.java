package ma.wwtrading.orders.application;

import java.util.Arrays;
import java.util.UUID;

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
public class UserAddressClient {

    private static final String USER_SERVICE = "user-service";
    private static final String ADDRESS_PATH = "/api/v1/users/me/addresses";

    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;

    public UserAddressClient(RestClient.Builder restClientBuilder, DiscoveryClient discoveryClient) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
    }

    public Address getAddress(JwtAuthenticationToken authentication, UUID addressId) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        ServiceInstance instance = discoveryClient.getInstances(USER_SERVICE).stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Address service unavailable"
            ));

        try {
            ResponseEntity<Address[]> response = restClientBuilder.clone()
                .baseUrl(instance.getUri().toString())
                .build()
                .get()
                .uri(ADDRESS_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                .retrieve()
                .toEntity(Address[].class);
            Address address = response.getBody() == null
                ? null
                : Arrays.stream(response.getBody())
                    .filter(candidate -> candidate != null && addressId.equals(candidate.id()))
                    .findFirst()
                    .orElse(null);
            if (address == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
            }
            if (address.firstName() == null || address.lastName() == null
                || address.addressLine1() == null || address.city() == null
                || address.country() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid address response");
            }
            return address;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                || exception.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                || exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Address service request failed");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Address service unavailable");
        }
    }

    public record Address(
        UUID id,
        String firstName,
        String lastName,
        String company,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String country
    ) {
    }
}
package ma.wwtrading.support.application;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AgentDirectoryClient {
    private final RestClient.Builder restClientBuilder;
    private final DiscoveryClient discoveryClient;

    public AgentDirectoryClient(RestClient.Builder restClientBuilder, DiscoveryClient discoveryClient) {
        this.restClientBuilder = restClientBuilder;
        this.discoveryClient = discoveryClient;
    }

    public void requireCommercialAgent(JwtAuthenticationToken authentication, String agentSubject) {
        try {
            UUID.fromString(agentSubject);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agent subject must be a UUID");
        }
        ServiceInstance userService = discoveryClient.getInstances("user-service").stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "User directory is unavailable"));
        try {
            StaffUser[] staff = restClientBuilder.clone().baseUrl(userService.getUri().toString()).build()
                    .get().uri("/api/v1/users")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                    .retrieve().body(StaffUser[].class);
            boolean valid = staff != null && Arrays.stream(staff).anyMatch(user ->
                    agentSubject.equals(user.id()) && "COMMERCIAL".equals(user.role()) && user.enabled());
            if (!valid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The assigned user must be an enabled COMMERCIAL");
            }
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "User directory authorization failed");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User directory is unavailable");
        }
    }

    private record StaffUser(String id, String role, boolean enabled) {}
}

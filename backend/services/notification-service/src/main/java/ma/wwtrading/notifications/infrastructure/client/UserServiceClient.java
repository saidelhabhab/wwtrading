package ma.wwtrading.notifications.infrastructure.client;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestTemplate restTemplate;
    private final String userServiceUrl;
    private final String internalKey;

    public UserServiceClient(
            @Value("${wwtrading.services.user-service-url:http://user-service:8081}") String userServiceUrl,
            @Value("${wwtrading.internal.notification-key:}") String internalKey) {
        this.restTemplate = new RestTemplate();
        this.userServiceUrl = userServiceUrl;
        this.internalKey = internalKey;
    }

    public record ClientRecipient(
            String keycloakSubject,
            String firstName,
            String lastName,
            String email,
            String phone,
            boolean enabled,
            Set<String> roles
            ) {

    }

    public List<ClientRecipient> getClientRecipients() {
        return getRecipients(Set.of("CLIENT"), null);
    }

    public List<ClientRecipient> getRecipients(Set<String> roles, String subject) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(
                userServiceUrl + "/api/internal/notification-recipients");
        if (roles != null) roles.forEach(role -> uri.queryParam("role", role));
        if (subject != null && !subject.isBlank()) uri.queryParam("subject", subject);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-WWTRADING-Internal-Key", internalKey);
            ClientRecipient[] response = restTemplate.exchange(uri.build(true).toUri(), HttpMethod.GET,
                    new HttpEntity<>(headers), ClientRecipient[].class).getBody();
            return response == null ? List.of() : List.of(response);
        } catch (Exception e) {
            log.error("Failed to fetch protected notification recipients: {}", e.getMessage(), e);
            throw new IllegalStateException("Protected recipient resolution failed", e);
        }
    }

    public Optional<ClientRecipient> findEnabledClient(String subject) {
        if (subject == null || subject.isBlank()) return Optional.empty();
        return getRecipients(Set.of("CLIENT"), subject).stream()
                .filter(ClientRecipient::enabled)
                .filter(recipient -> subject.equals(recipient.keycloakSubject()))
                .findFirst();
    }
}

package ma.wwtrading.users.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;

@RestController
@RequestMapping("/api/analytics/clients")
public class ClientAnalyticsController {
    private final KeycloakClient keycloakClient;
    private final UserRepository userRepository;

    public ClientAnalyticsController(KeycloakClient keycloakClient, UserRepository userRepository) {
        this.keycloakClient = keycloakClient;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ClientAnalyticsResponse report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusYears(1).isBefore(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid analytics period");
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Set<String> clientSubjects = keycloakClient.getClientSubjectIds();
        if (clientSubjects.isEmpty()) return new ClientAnalyticsResponse(0, 0, 0);
        return new ClientAnalyticsResponse(
                userRepository.countByKeycloakSubjectIn(clientSubjects),
                userRepository.countByKeycloakSubjectInAndEnabledTrue(clientSubjects),
                userRepository.countByKeycloakSubjectInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        clientSubjects, fromInstant, toExclusive));
    }

    public record ClientAnalyticsResponse(long totalClients, long activeClients, long newClients) {}
}

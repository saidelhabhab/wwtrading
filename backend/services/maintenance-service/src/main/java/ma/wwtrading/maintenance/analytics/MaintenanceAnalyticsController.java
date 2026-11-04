package ma.wwtrading.maintenance.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.maintenance.domain.MaintenanceRepository;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;

@RestController
@RequestMapping("/api/analytics/maintenance")
public class MaintenanceAnalyticsController {
    private final MaintenanceRepository repository;
    public MaintenanceAnalyticsController(MaintenanceRepository repository) { this.repository = repository; }

    @GetMapping
    @Transactional(readOnly = true)
    public MaintenanceAnalyticsResponse report(JwtAuthenticationToken authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Bounds bounds = bounds(from, to);
        boolean management = hasRole(authentication, "ADMIN") || hasRole(authentication, "MANAGER");
        boolean client = hasRole(authentication, "CLIENT");
        boolean technician = hasRole(authentication, "TECHNICIAN");
        if (!management && !client && !technician) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Maintenance analytics access denied");
        String subject = client ? subject(authentication) : null;
        UUID technicianId = technician ? technicianId(authentication) : null;
        List<MaintenanceRepository.StatusCountView> grouped = management
                ? repository.countByStatusForPeriod(bounds.from(), bounds.toExclusive())
                : client
                    ? repository.countByCustomerSubjectAndStatus(subject, bounds.from(), bounds.toExclusive())
                    : repository.countByTechnicianAndStatus(technicianId, bounds.from(), bounds.toExclusive());
        Map<MaintenanceStatus, Long> counts = new EnumMap<>(MaintenanceStatus.class);
        grouped.forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        var daily = management ? repository.countCreatedByDay(bounds.from(), bounds.toExclusive())
                : client ? repository.countCreatedByCustomerSubjectAndDay(subject, bounds.from(), bounds.toExclusive())
                : repository.countCreatedByTechnicianAndDay(technicianId, bounds.from(), bounds.toExclusive());
        return new MaintenanceAnalyticsResponse(
                counts.values().stream().mapToLong(Long::longValue).sum(),
                Arrays.stream(MaintenanceStatus.values()).map(status -> new StatusCount(status, counts.getOrDefault(status, 0L))).toList(),
                daily.stream().map(row -> new DailyCount(row.getDay(), row.getTotal())).toList());
    }
    private Bounds bounds(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusYears(1).isBefore(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid analytics period");
        return new Bounds(from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }
    private String subject(JwtAuthenticationToken authentication) {
        String value = authentication == null || authentication.getToken() == null ? null : authentication.getToken().getSubject();
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated JWT subject is required");
        return value;
    }
    private UUID technicianId(JwtAuthenticationToken authentication) {
        try { return UUID.fromString(subject(authentication)); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is not a valid UUID"); }
    }
    private boolean hasRole(JwtAuthenticationToken authentication, String role) { return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority())); }
    public record MaintenanceAnalyticsResponse(long totalRequests, List<StatusCount> byStatus, List<DailyCount> overTime) {}
    public record StatusCount(MaintenanceStatus status, long count) {}
    public record DailyCount(LocalDate date, long count) {}
    private record Bounds(Instant from, Instant toExclusive) {}
}

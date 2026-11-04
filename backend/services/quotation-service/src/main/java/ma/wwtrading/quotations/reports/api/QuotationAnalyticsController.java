package ma.wwtrading.quotations.reports.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.quotations.domain.QuotationRepository;
import ma.wwtrading.quotations.domain.QuotationStatus;

@RestController
@RequestMapping("/api/analytics/quotations")
public class QuotationAnalyticsController {
    private final QuotationRepository repository;

    public QuotationAnalyticsController(QuotationRepository repository) { this.repository = repository; }

    @GetMapping
    @Transactional(readOnly = true)
    public QuotationAnalyticsResponse report(JwtAuthenticationToken authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Bounds bounds = bounds(from, to);
        boolean management = hasRole(authentication, "ADMIN") || hasRole(authentication, "MANAGER");
        boolean client = hasRole(authentication, "CLIENT");
        if (!management && !client) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Quotation analytics access denied");
        String subject = client ? subject(authentication) : null;
        var grouped = management ? repository.countByStatus(bounds.from(), bounds.toExclusive())
                : repository.countByCustomerSubjectAndStatus(subject, bounds.from(), bounds.toExclusive());
        Map<QuotationStatus, Long> counts = new EnumMap<>(QuotationStatus.class);
        grouped.forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        var daily = management ? repository.countCreatedByDay(bounds.from(), bounds.toExclusive())
                : repository.countCreatedByCustomerSubjectAndDay(subject, bounds.from(), bounds.toExclusive());
        return new QuotationAnalyticsResponse(
                counts.values().stream().mapToLong(Long::longValue).sum(),
                Arrays.stream(QuotationStatus.values()).map(status -> new StatusCount(status, counts.getOrDefault(status, 0L))).toList(),
                daily.stream().map(row -> new DailyCount(row.getDay(), row.getTotal())).toList());
    }

    private Bounds bounds(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusYears(1).isBefore(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid analytics period");
        return new Bounds(from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }
    private String subject(JwtAuthenticationToken authentication) {
        String value = authentication == null || authentication.getToken() == null ? null : authentication.getToken().getSubject();
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated JWT subject is required");
        return value;
    }
    private boolean hasRole(JwtAuthenticationToken authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority()));
    }
    public record QuotationAnalyticsResponse(long totalQuotations, List<StatusCount> byStatus, List<DailyCount> overTime) {}
    public record StatusCount(QuotationStatus status, long count) {}
    public record DailyCount(LocalDate date, long count) {}
    private record Bounds(Instant from, Instant toExclusive) {}
}

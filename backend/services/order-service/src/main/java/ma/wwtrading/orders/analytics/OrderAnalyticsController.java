package ma.wwtrading.orders.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import ma.wwtrading.orders.domain.OrderRepository;
import ma.wwtrading.orders.domain.OrderStatus;

@RestController
@RequestMapping("/api/analytics/orders")
public class OrderAnalyticsController {

    private final OrderRepository repository;

    public OrderAnalyticsController(OrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public OrderAnalyticsResponse report(
            JwtAuthenticationToken authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Bounds bounds = bounds(from, to);
        boolean management = hasRole(authentication, "ADMIN") || hasRole(authentication, "MANAGER");
        boolean client = hasRole(authentication, "CLIENT");
        if (!management && !client) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order analytics access denied");

        String customer = client ? subject(authentication) : null;
        var grouped = management
                ? repository.countByStatus(bounds.from(), bounds.toExclusive())
                : repository.countByCustomerAndStatus(customer, bounds.from(), bounds.toExclusive());
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        grouped.forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        // Order value: what was ordered. Never labelled or treated as revenue.
        BigDecimal orderValue = management
                ? repository.sumOrderValue(bounds.from(), bounds.toExclusive())
                : repository.sumOrderValueByCustomer(customer, bounds.from(), bounds.toExclusive());

        // Paid revenue: payment explicitly confirmed through the existing payment flow.
        BigDecimal paidRevenue = management
                ? repository.sumPaidRevenue(bounds.from(), bounds.toExclusive())
                : repository.sumPaidRevenueByCustomer(customer, bounds.from(), bounds.toExclusive());

        // Recognized revenue: paid AND reached the existing COMPLETED state.
        BigDecimal recognizedRevenue = management
                ? repository.sumRecognizedRevenue(bounds.from(), bounds.toExclusive())
                : repository.sumRecognizedRevenueByCustomer(customer, bounds.from(), bounds.toExclusive());

        long orderValueOrders = management
                ? repository.countOrderValueOrders(bounds.from(), bounds.toExclusive())
                : repository.countOrderValueOrdersByCustomer(customer, bounds.from(), bounds.toExclusive());

        BigDecimal averageOrderValue = orderValueOrders == 0
                ? BigDecimal.ZERO.setScale(2)
                : orderValue.divide(BigDecimal.valueOf(orderValueOrders), 2, RoundingMode.HALF_UP);

        var daily = management
                ? repository.countCreatedByDay(bounds.from(), bounds.toExclusive())
                : repository.countCreatedByCustomerAndDay(customer, bounds.from(), bounds.toExclusive());

        var revenueByDay = management
                ? repository.revenueByDay(bounds.from(), bounds.toExclusive())
                : repository.revenueByCustomerAndDay(customer, bounds.from(), bounds.toExclusive());

        return new OrderAnalyticsResponse(
                total,
                orderValue,
                paidRevenue,
                recognizedRevenue,
                averageOrderValue,
                orderValueOrders,
                Arrays.stream(OrderStatus.values()).map(status -> new StatusCount(status, counts.getOrDefault(status, 0L))).toList(),
                daily.stream().map(row -> new DailyCount(row.getDay(), row.getTotal())).toList(),
                revenueByDay.stream().map(row -> new RevenuePoint(
                        row.getDay(),
                        scale(row.getPaid()),
                        scale(row.getRecognized()),
                        row.getOrders())).toList());
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Bounds bounds(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusYears(1).isBefore(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid analytics period");
        return new Bounds(from.atStartOfDay(ZoneOffset.UTC).toInstant(), to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private String subject(JwtAuthenticationToken authentication) {
        String subject = authentication == null || authentication.getToken() == null ? null : authentication.getToken().getSubject();
        if (subject == null || subject.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated JWT subject is required");
        return subject;
    }

    private boolean hasRole(JwtAuthenticationToken authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    public record OrderAnalyticsResponse(
            long totalOrders,
            /** Order value: sum of all non-cancelled orders. What was ordered, NOT revenue. */
            BigDecimal totalValue,
            /** Paid revenue: orders whose payment was explicitly confirmed. */
            BigDecimal paidRevenue,
            /** Recognized revenue: paid orders that also reached COMPLETED. */
            BigDecimal recognizedRevenue,
            /** Order value divided by the number of non-cancelled orders. */
            BigDecimal averageOrderValue,
            /** Denominator of the average order value. */
            long orderValueOrders,
            List<StatusCount> byStatus,
            List<DailyCount> overTime,
            /** Paid and recognized revenue per day, plus the contributing order count. */
            List<RevenuePoint> revenueEvolution) {}
    public record StatusCount(OrderStatus status, long count) {}
    public record DailyCount(LocalDate date, long count) {}
    public record RevenuePoint(LocalDate date, BigDecimal paidRevenue, BigDecimal recognizedRevenue, long orders) {}
    private record Bounds(Instant from, Instant toExclusive) {}
}

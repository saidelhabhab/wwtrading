package ma.wwtrading.orders.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.orders.domain.OrderRepository;

/**
 * Focused tests for the revenue aggregates. They assert which repository
 * aggregates the controller calls and with which date bounds, so the three
 * financially distinct figures cannot silently drift into one another.
 */
class OrderAnalyticsRevenueTests {

    private OrderRepository repository;
    private OrderAnalyticsController controller;

    @BeforeEach
    void setUp() {
        repository = mock(OrderRepository.class);
        controller = new OrderAnalyticsController(repository);
        // Default every management aggregate to zero so each test only asserts its own metric.
        when(repository.countByStatus(any(), any())).thenReturn(List.of());
        when(repository.sumOrderValue(any(), any())).thenReturn(new BigDecimal("0.00"));
        when(repository.sumPaidRevenue(any(), any())).thenReturn(new BigDecimal("0.00"));
        when(repository.sumRecognizedRevenue(any(), any())).thenReturn(new BigDecimal("0.00"));
        when(repository.countOrderValueOrders(any(), any())).thenReturn(0L);
        when(repository.countCreatedByDay(any(), any())).thenReturn(List.of());
        when(repository.revenueByDay(any(), any())).thenReturn(List.of());
        // Client-scoped aggregates, used when the caller holds ROLE_CLIENT.
        when(repository.countByCustomerAndStatus(any(), any(), any())).thenReturn(List.of());
        when(repository.sumOrderValueByCustomer(any(), any(), any())).thenReturn(new BigDecimal("0.00"));
        when(repository.sumPaidRevenueByCustomer(any(), any(), any())).thenReturn(new BigDecimal("0.00"));
        when(repository.sumRecognizedRevenueByCustomer(any(), any(), any())).thenReturn(new BigDecimal("0.00"));
        when(repository.countOrderValueOrdersByCustomer(any(), any(), any())).thenReturn(0L);
        when(repository.countCreatedByCustomerAndDay(any(), any(), any())).thenReturn(List.of());
        when(repository.revenueByCustomerAndDay(any(), any(), any())).thenReturn(List.of());
    }

    private JwtAuthenticationToken admin() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject("admin-subject")
                .claim("realm_access", java.util.Map.of("roles", List.of("ADMIN")))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private JwtAuthenticationToken client(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject(subject)
                .claim("realm_access", java.util.Map.of("roles", List.of("CLIENT")))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    private JwtAuthenticationToken commercial() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none")
                .subject("commercial-subject")
                .claim("realm_access", java.util.Map.of("roles", List.of("COMMERCIAL")))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_COMMERCIAL")));
    }

    @Test
    void keepsTheThreeFinancialMetricsSeparate() {
        when(repository.sumOrderValue(any(), any())).thenReturn(new BigDecimal("15000.00"));
        when(repository.sumPaidRevenue(any(), any())).thenReturn(new BigDecimal("12000.00"));
        when(repository.sumRecognizedRevenue(any(), any())).thenReturn(new BigDecimal("8000.00"));
        when(repository.countOrderValueOrders(any(), any())).thenReturn(4L);

        var response = controller.report(admin(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response.totalValue()).isEqualByComparingTo("15000.00");
        assertThat(response.paidRevenue()).isEqualByComparingTo("12000.00");
        assertThat(response.recognizedRevenue()).isEqualByComparingTo("8000.00");
        assertThat(response.orderValueOrders()).isEqualTo(4L);
    }

    @Test
    void averageOrderValueIsOrderValueDividedByNonCancelledOrders() {
        when(repository.sumOrderValue(any(), any())).thenReturn(new BigDecimal("15000.00"));
        when(repository.countOrderValueOrders(any(), any())).thenReturn(4L);

        var response = controller.report(admin(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response.averageOrderValue()).isEqualByComparingTo("3750.00");
    }

    @Test
    void averageOrderValueIsZeroWhenThereAreNoNonCancelledOrders() {
        when(repository.countOrderValueOrders(any(), any())).thenReturn(0L);

        var response = controller.report(admin(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(response.averageOrderValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void usesExclusiveUpperBoundSoTheWholeToDateIsIncluded() {
        controller.report(admin(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        verify(repository).sumOrderValue(
                eq(java.time.LocalDate.of(2026, 9, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()),
                eq(java.time.LocalDate.of(2026, 10, 1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
    }

    @Test
    void rejectsAnInvertedOrOversizedPeriod() {
        assertThatThrownBy(() -> controller.report(admin(), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid analytics period");

        assertThatThrownBy(() -> controller.report(admin(), LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid analytics period");
    }

    @Test
    void clientScopeIsRestrictedToItsOwnSubjectAndNeverGlobal() {
        var response = controller.report(client("client-1"), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        verify(repository).sumOrderValueByCustomer(eq("client-1"), any(), any());
        verify(repository).sumPaidRevenueByCustomer(eq("client-1"), any(), any());
        verify(repository).sumRecognizedRevenueByCustomer(eq("client-1"), any(), any());
        verify(repository, never()).sumOrderValue(any(), any());
        verify(repository, never()).sumPaidRevenue(any(), any());
        verify(repository, never()).sumRecognizedRevenue(any(), any());
        assertThat(response.totalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void managementScopeUsesGlobalAggregates() {
        controller.report(admin(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        verify(repository).sumOrderValue(any(), any());
        verify(repository).sumPaidRevenue(any(), any());
        verify(repository).sumRecognizedRevenue(any(), any());
    }

    @Test
    void commercialIsDeniedFinancialAnalytics() {
        assertThatThrownBy(() -> controller.report(commercial(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Order analytics access denied");

        verify(repository, never()).sumPaidRevenue(any(), any());
        verify(repository, never()).sumRecognizedRevenue(any(), any());
    }

    @Test
    void exposesPaidAndRecognizedAsSeparateDailySeries() {
        var response = controller.report(admin(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        // The evolution series must remain two distinct fields, never a merged total.
        assertThat(OrderAnalyticsController.RevenuePoint.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("date", "paidRevenue", "recognizedRevenue", "orders");
        assertThat(response.revenueEvolution()).isEmpty();
    }
}

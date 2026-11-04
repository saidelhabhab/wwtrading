package ma.wwtrading.quotations.reports.application;

import static ma.wwtrading.quotations.reports.api.CommercialReportDtos.ActivityPointResponse;
import static ma.wwtrading.quotations.reports.api.CommercialReportDtos.CommercialReportResponse;
import static ma.wwtrading.quotations.reports.api.CommercialReportDtos.KpiResponse;
import static ma.wwtrading.quotations.reports.api.CommercialReportDtos.PeriodResponse;
import static ma.wwtrading.quotations.reports.api.CommercialReportDtos.ProspectReportRow;
import static ma.wwtrading.quotations.reports.api.CommercialReportDtos.StatusCountResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.quotations.prospects.domain.ProspectEntity;
import ma.wwtrading.quotations.prospects.domain.ProspectRepository;
import ma.wwtrading.quotations.prospects.domain.ProspectStatus;

@Service
public class CommercialReportService {

    private static final int MAX_PERIOD_DAYS = 366;
    private static final List<ProspectStatus> ACTIVE_STATUSES = List.of(
            ProspectStatus.NEW,
            ProspectStatus.CONTACTED,
            ProspectStatus.INTERESTED,
            ProspectStatus.QUOTE_SENT);

    private final ProspectRepository prospectRepository;

    public CommercialReportService(ProspectRepository prospectRepository) {
        this.prospectRepository = prospectRepository;
    }

    @Transactional(readOnly = true)
    public CommercialReportResponse report(
            JwtAuthenticationToken authentication,
            LocalDate from,
            LocalDate to) {
        Period period = validatePeriod(from, to);
        Scope scope = scope(authentication);

        List<ProspectRepository.StatusCountView> grouped = scope.management()
                ? prospectRepository.countByStatusForPeriod(period.fromInstant(), period.toExclusiveInstant())
                : prospectRepository.countByStatusForCommercialAndPeriod(
                        scope.subject(), period.fromInstant(), period.toExclusiveInstant());

        Map<ProspectStatus, Long> counts = new EnumMap<>(ProspectStatus.class);
        grouped.forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        List<StatusCountResponse> statusCounts = Arrays.stream(ProspectStatus.values())
                .map(status -> new StatusCountResponse(status, counts.getOrDefault(status, 0L)))
                .toList();

        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long active = ACTIVE_STATUSES.stream().mapToLong(status -> counts.getOrDefault(status, 0L)).sum();
        long won = counts.getOrDefault(ProspectStatus.WON, 0L);
        long lost = counts.getOrDefault(ProspectStatus.LOST, 0L);

        List<ProspectRepository.DailyCountView> daily = scope.management()
                ? prospectRepository.countCreatedByDayForPeriod(period.fromInstant(), period.toExclusiveInstant())
                : prospectRepository.countCreatedByDayForCommercialAndPeriod(
                        scope.subject(), period.fromInstant(), period.toExclusiveInstant());

        Specification<ProspectEntity> specification = specification(scope, period, null, null);
        List<ProspectReportRow> recent = prospectRepository.findAll(
                        specification,
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .stream()
                .map(this::toRow)
                .toList();

        return new CommercialReportResponse(
                new PeriodResponse(from, to),
                new KpiResponse(total, active, won, lost),
                statusCounts,
                daily.stream().map(row -> new ActivityPointResponse(row.getDay(), row.getTotal())).toList(),
                recent);
    }

    @Transactional(readOnly = true)
    public Page<ProspectReportRow> prospects(
            JwtAuthenticationToken authentication,
            LocalDate from,
            LocalDate to,
            ProspectStatus status,
            String search,
            Pageable pageable) {
        Period period = validatePeriod(from, to);
        Scope scope = scope(authentication);
        Pageable safePageable = PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return prospectRepository.findAll(specification(scope, period, status, search), safePageable)
                .map(this::toRow);
    }

    private Specification<ProspectEntity> specification(
            Scope scope,
            Period period,
            ProspectStatus status,
            String search) {
        Specification<ProspectEntity> result = (root, query, builder) -> builder.and(
                builder.greaterThanOrEqualTo(root.get("createdAt"), period.fromInstant()),
                builder.lessThan(root.get("createdAt"), period.toExclusiveInstant()));
        if (!scope.management()) {
            result = result.and((root, query, builder) ->
                    builder.equal(root.get("assignedCommercialSubject"), scope.subject()));
        }
        if (status != null) {
            result = result.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            result = result.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("companyName")), pattern),
                    builder.like(builder.lower(root.get("contactName")), pattern),
                    builder.like(builder.lower(root.get("city")), pattern)));
        }
        return result;
    }

    private Period validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report period is required");
        }
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_PERIOD_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report period must be between 1 and 366 days");
        }
        return new Period(
                from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private Scope scope(JwtAuthenticationToken authentication) {
        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "MANAGER")) {
            return new Scope(true, null);
        }
        if (!hasRole(authentication, "COMMERCIAL")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Commercial report access denied");
        }
        String subject = authentication == null || authentication.getToken() == null
                ? null : authentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated JWT subject is required");
        }
        return new Scope(false, subject);
    }

    private boolean hasRole(JwtAuthenticationToken authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    private ProspectReportRow toRow(ProspectEntity prospect) {
        return new ProspectReportRow(
                prospect.getId(), prospect.getCompanyName(), prospect.getContactName(),
                prospect.getCity(), prospect.getActivity(), prospect.getStatus(),
                prospect.getCreatedAt(), prospect.getUpdatedAt());
    }

    private record Period(Instant fromInstant, Instant toExclusiveInstant) {
    }

    private record Scope(boolean management, String subject) {
    }
}

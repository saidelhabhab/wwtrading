package ma.wwtrading.quotations.reports.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.quotations.prospects.domain.ProspectStatus;

public final class CommercialReportDtos {

    private CommercialReportDtos() {
    }

    public record PeriodResponse(LocalDate from, LocalDate to) {
    }

    public record KpiResponse(long prospects, long activeProspects, long wonProspects, long lostProspects) {
    }

    public record StatusCountResponse(ProspectStatus status, long count) {
    }

    public record ActivityPointResponse(LocalDate date, long newProspects) {
    }

    public record ProspectReportRow(
            UUID id,
            String companyName,
            String contactName,
            String city,
            String activity,
            ProspectStatus status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CommercialReportResponse(
            PeriodResponse period,
            KpiResponse kpis,
            List<StatusCountResponse> prospectsByStatus,
            List<ActivityPointResponse> activityOverTime,
            List<ProspectReportRow> recentProspects) {
    }
}

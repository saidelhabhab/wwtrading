package ma.wwtrading.quotations.reports.api;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ma.wwtrading.quotations.prospects.domain.ProspectStatus;
import ma.wwtrading.quotations.reports.api.CommercialReportDtos.CommercialReportResponse;
import ma.wwtrading.quotations.reports.api.CommercialReportDtos.ProspectReportRow;
import ma.wwtrading.quotations.reports.application.CommercialReportService;

@RestController
@RequestMapping("/api/reports/commercial")
public class CommercialReportController {

    private final CommercialReportService service;

    public CommercialReportController(CommercialReportService service) {
        this.service = service;
    }

    @GetMapping
    public CommercialReportResponse report(
            JwtAuthenticationToken authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(authentication, from, to);
    }

    @GetMapping("/prospects")
    public Page<ProspectReportRow> prospects(
            JwtAuthenticationToken authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ProspectStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.prospects(authentication, from, to, status, search, pageable);
    }
}

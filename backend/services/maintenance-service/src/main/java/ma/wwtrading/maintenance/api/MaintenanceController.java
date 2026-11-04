package ma.wwtrading.maintenance.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.wwtrading.maintenance.application.MaintenanceApplicationService;

@RestController
@RequestMapping("/api/maintenance")
@Tag(name = "Maintenance", description = "Maintenance request creation and management operations")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class MaintenanceController {

    private final MaintenanceApplicationService service;

    public MaintenanceController(MaintenanceApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a maintenance request")
    public ResponseEntity<MaintenanceResponse> create(
        JwtAuthenticationToken authentication,
        @Valid @RequestBody CreateMaintenanceRequest request
    ) {
        MaintenanceResponse maintenance = service.create(authentication, request);
        return ResponseEntity.created(URI.create("/api/maintenance/" + maintenance.id())).body(maintenance);
    }

    @GetMapping("/my-requests")
    @Operation(summary = "Get client maintenance requests")
    public Page<MaintenanceResponse> getMyRequests(
        JwtAuthenticationToken authentication,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return service.getMyRequests(authentication, pageable);
    }

    /**
     * Technician inbox: returns only maintenance requests assigned to the authenticated technician.
     * Server-side filtering by JWT subject.
     */
    @GetMapping("/my-assignments")
    @Operation(summary = "Get technician assigned maintenance requests")
    public Page<MaintenanceResponse> getMyAssignments(
        JwtAuthenticationToken authentication,
        @PageableDefault(size = 20, sort = "assignedAt") Pageable pageable
    ) {
        return service.getMyAssignments(authentication, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a maintenance request by ID")
    public MaintenanceResponse getById(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.getById(authentication, id);
    }

    @GetMapping
    @Operation(summary = "List maintenance requests")
    public Page<MaintenanceResponse> list(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get maintenance history")
    public Page<MaintenanceHistoryResponse> getHistory(
        @PathVariable UUID id,
        @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable
    ) {
        return service.getHistory(id, pageable);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit a maintenance request for manager review (OPEN → SUBMITTED)")
    public MaintenanceResponse submit(@PathVariable UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Assigned technician accepts maintenance request (ASSIGNED → ACCEPTED)")
    public MaintenanceResponse accept(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.accept(authentication, id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Assigned technician rejects maintenance request (ASSIGNED → REJECTED)")
    public MaintenanceResponse reject(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.reject(authentication, id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start a maintenance request (ACCEPTED → IN_PROGRESS)")
    public MaintenanceResponse start(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.start(authentication, id);
    }

    @PatchMapping("/{id}/intervention")
    @Operation(summary = "Technician updates diagnosis and intervention notes (status: IN_PROGRESS)")
    public MaintenanceResponse updateIntervention(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id,
        @RequestBody UpdateInterventionRequest request
    ) {
        return service.updateIntervention(authentication, id, request);
    }

    // ── Phase T4: Spare Part Requests ──────────────────────────────────────────

    @PostMapping("/{id}/parts")
    @Operation(summary = "Technician creates a spare part request for maintenance")
    public ResponseEntity<MaintenancePartRequestResponse> createPartRequest(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id,
        @Valid @RequestBody CreatePartRequestPayload payload
    ) {
        MaintenancePartRequestResponse res = service.createPartRequest(authentication, id, payload);
        return ResponseEntity.created(URI.create("/api/maintenance/" + id + "/parts/" + res.id())).body(res);
    }

    @GetMapping("/{id}/parts")
    @Operation(summary = "List spare part requests for a maintenance request")
    public List<MaintenancePartRequestResponse> getPartRequests(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.getPartRequests(authentication, id);
    }

    @PostMapping("/{id}/parts/{partRequestId}/approve")
    @Operation(summary = "Manager/Admin approves a spare part request")
    public MaintenancePartRequestResponse approvePartRequest(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id,
        @PathVariable UUID partRequestId
    ) {
        return service.approvePartRequest(authentication, id, partRequestId);
    }

    @PostMapping("/{id}/parts/{partRequestId}/reject")
    @Operation(summary = "Manager/Admin rejects a spare part request")
    public MaintenancePartRequestResponse rejectPartRequest(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id,
        @PathVariable UUID partRequestId
    ) {
        return service.rejectPartRequest(authentication, id, partRequestId);
    }

    @PostMapping("/{id}/parts/{partRequestId}/wait")
    @Operation(summary = "Technician places maintenance in WAITING_FOR_PART for an approved part request")
    public MaintenanceResponse waitOnPart(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id,
        @PathVariable UUID partRequestId
    ) {
        return service.waitOnPart(authentication, id, partRequestId);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Technician resumes maintenance intervention (WAITING_FOR_PART → IN_PROGRESS)")
    public MaintenanceResponse resumeFromPartWait(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.resumeFromPartWait(authentication, id);
    }

    // ── Phase T5: Completion & Client Confirmation ─────────────────────────────

    @PostMapping("/{id}/submit-completion")
    @Operation(summary = "Technician submits maintenance completion (IN_PROGRESS → COMPLETION_PENDING_CONFIRMATION)")
    public MaintenanceResponse submitCompletion(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id,
        @RequestBody(required = false) SubmitCompletionRequest request
    ) {
        return service.submitCompletion(authentication, id, request);
    }

    @PostMapping("/{id}/confirm-completion")
    @Operation(summary = "Client confirms maintenance completion (COMPLETION_PENDING_CONFIRMATION → COMPLETED)")
    public MaintenanceResponse confirmCompletion(
        JwtAuthenticationToken authentication,
        @PathVariable UUID id
    ) {
        return service.confirmCompletion(authentication, id);
    }

    // ───────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a maintenance request (legacy/direct)")
    public MaintenanceResponse complete(
        @PathVariable UUID id,
        @RequestBody(required = false) CompleteMaintenanceRequest request
    ) {
        String interventionNotes = request != null ? request.interventionNotes() : null;
        return service.complete(id, interventionNotes);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a maintenance request")
    public MaintenanceResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PutMapping("/{id}/technician/{technicianId}")
    @Operation(summary = "Assign or reassign a technician")
    public MaintenanceResponse assignTechnician(
        @PathVariable UUID id,
        @PathVariable UUID technicianId
    ) {
        return service.assignTechnician(id, technicianId);
    }

    @DeleteMapping("/{id}/technician")
    @Operation(summary = "Remove the assigned technician")
    public MaintenanceResponse unassignTechnician(@PathVariable UUID id) {
        return service.unassignTechnician(id);
    }
}

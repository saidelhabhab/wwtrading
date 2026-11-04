package ma.wwtrading.maintenance.application;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import ma.wwtrading.maintenance.api.CreateMaintenanceRequest;
import ma.wwtrading.maintenance.api.CreatePartRequestPayload;
import ma.wwtrading.maintenance.api.MaintenanceHistoryResponse;
import ma.wwtrading.maintenance.api.MaintenancePartRequestResponse;
import ma.wwtrading.maintenance.api.MaintenanceResponse;
import ma.wwtrading.maintenance.api.SubmitCompletionRequest;
import ma.wwtrading.maintenance.api.UpdateInterventionRequest;
import ma.wwtrading.maintenance.domain.MaintenanceEntity;
import ma.wwtrading.maintenance.domain.MaintenanceHistoryEntity;
import ma.wwtrading.maintenance.domain.MaintenanceHistoryRepository;
import ma.wwtrading.maintenance.domain.MaintenancePartRequestEntity;
import ma.wwtrading.maintenance.domain.MaintenancePartRequestRepository;
import ma.wwtrading.maintenance.domain.MaintenancePartRequestStatus;
import ma.wwtrading.maintenance.domain.MaintenanceRepository;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;
import ma.wwtrading.maintenance.infrastructure.kafka.MaintenanceEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceApplicationService {

    private final MaintenanceRepository repository;
    private final MaintenanceHistoryRepository historyRepository;
    private final MaintenancePartRequestRepository partRequestRepository;
    private final MaintenanceMapper mapper;
    private MaintenanceEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setEventPublisher(MaintenanceEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public MaintenanceApplicationService(MaintenanceRepository repository,
                                         MaintenanceHistoryRepository historyRepository,
                                         MaintenancePartRequestRepository partRequestRepository,
                                         MaintenanceMapper mapper) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.partRequestRepository = partRequestRepository;
        this.mapper = mapper;
    }

    public MaintenanceApplicationService(MaintenanceRepository repository,
                                         MaintenanceHistoryRepository historyRepository,
                                         MaintenanceMapper mapper) {
        this(repository, historyRepository, null, mapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MaintenanceApplicationService(MaintenanceRepository repository,
                                         MaintenancePartRequestRepository partRequestRepository,
                                         MaintenanceMapper mapper) {
        this(repository, null, partRequestRepository, mapper);
    }

    public MaintenanceApplicationService(MaintenanceRepository repository,
                                         MaintenanceMapper mapper) {
        this(repository, null, null, mapper);
    }

    @Transactional
    public MaintenanceResponse create(CreateMaintenanceRequest request) {
        return create(null, request);
    }

    @Transactional
    public MaintenanceResponse create(JwtAuthenticationToken authentication, CreateMaintenanceRequest request) {
        String customerRef = resolveCustomerReference(authentication, request.customerReference());
        MaintenanceEntity maintenance = mapper.toEntity(customerRef, request);
        if (authentication != null) {
            if (!isStaff(authentication) && !isTechnician(authentication)) {
                maintenance.assignCustomerSubject(authentication.getToken().getSubject());
            }
            maintenance.submit();
        }
        try {
            MaintenanceEntity saved = repository.saveAndFlush(maintenance);
            if (historyRepository != null) {
                writeHistory(MaintenanceHistoryEntity.created(saved.getId()));
                if (authentication != null) {
                    writeHistory(MaintenanceHistoryEntity.statusChanged(
                            saved.getId(), MaintenanceStatus.OPEN, MaintenanceStatus.SUBMITTED));
                }
            }
            if (eventPublisher != null) eventPublisher.created(saved);
            return mapper.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new MaintenanceDataConflictException();
        }
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse getById(UUID id) {
        return getById(null, id);
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse getById(JwtAuthenticationToken authentication, UUID id) {
        MaintenanceEntity maintenance = repository.findById(id)
            .orElseThrow(() -> new MaintenanceNotFoundException(id));

        if (isStaff(authentication)) {
            return mapper.toResponse(maintenance);
        }

        // Technician: can only view their own assigned request
        if (isTechnician(authentication)) {
            UUID sub = requireSubject(authentication);
            if (!sub.equals(maintenance.getTechnicianId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this maintenance request");
            }
            return mapper.toResponse(maintenance);
        }

        // Client: can only view their own request
        String customerRef = requireCustomerReference(authentication);
        if (!maintenance.getCustomerReference().equalsIgnoreCase(customerRef)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this maintenance request");
        }
        return mapper.toResponse(maintenance);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> getMyRequests(JwtAuthenticationToken authentication, Pageable pageable) {
        String customerRef = requireCustomerReference(authentication);
        return repository.findByCustomerReferenceOrderByCreatedAtDesc(customerRef, pageable)
            .map(mapper::toResponse);
    }

    /**
     * Technician inbox: returns only maintenance requests assigned to the authenticated technician.
     * Server-side filtering by JWT subject.
     */
    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> getMyAssignments(JwtAuthenticationToken authentication, Pageable pageable) {
        UUID technicianId = requireSubject(authentication);
        return repository.findByTechnicianIdOrderByAssignedAtDesc(technicianId, pageable)
            .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> list(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceHistoryResponse> getHistory(UUID maintenanceId, Pageable pageable) {
        if (!repository.existsById(maintenanceId)) {
            throw new MaintenanceNotFoundException(maintenanceId);
        }
        return historyRepository.findByMaintenanceIdOrderByCreatedAtDesc(maintenanceId, pageable)
            .map(MaintenanceHistoryResponse::from);
    }

    @Transactional
    public MaintenanceResponse submit(UUID id) {
        return transition(id, MaintenanceEntity::submit, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    /**
     * Technician accepts an assigned maintenance request.
     * Transition: ASSIGNED → ACCEPTED.
     */
    @Transactional
    public MaintenanceResponse accept(JwtAuthenticationToken authentication, UUID id) {
        verifyAssignedTechnician(authentication, id);
        return transition(id, MaintenanceEntity::accept, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    /**
     * Technician rejects an assigned maintenance request.
     * Transition: ASSIGNED → REJECTED.
     */
    @Transactional
    public MaintenanceResponse reject(JwtAuthenticationToken authentication, UUID id) {
        verifyAssignedTechnician(authentication, id);
        return transition(id, MaintenanceEntity::reject, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    /**
     * Technician starts the intervention.
     * Transition: ACCEPTED → IN_PROGRESS.
     */
    @Transactional
    public MaintenanceResponse start(JwtAuthenticationToken authentication, UUID id) {
        verifyAssignedTechnician(authentication, id);
        return transition(id, MaintenanceEntity::start, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    @Transactional
    public MaintenanceResponse start(UUID id) {
        return transition(id, MaintenanceEntity::start, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    /**
     * Technician updates diagnosis and intervention notes for an IN_PROGRESS request.
     */
    @Transactional
    public MaintenanceResponse updateIntervention(JwtAuthenticationToken authentication, UUID id, UpdateInterventionRequest request) {
        verifyAssignedTechnician(authentication, id);
        MaintenanceEntity maintenance = findForUpdate(id);
        try {
            maintenance.updateIntervention(request.diagnosis(), request.interventionNotes());
            MaintenanceEntity saved = repository.saveAndFlush(maintenance);
            return mapper.toResponse(saved);
        } catch (IllegalStateException | DataIntegrityViolationException exception) {
            throw new MaintenanceDataConflictException();
        }
    }

    // ── Phase T4: Spare Part Requests ──────────────────────────────────────────

    @Transactional
    public MaintenancePartRequestResponse createPartRequest(JwtAuthenticationToken authentication,
                                                           UUID maintenanceId,
                                                           CreatePartRequestPayload payload) {
        verifyAssignedTechnician(authentication, maintenanceId);
        MaintenanceEntity maintenance = repository.findById(maintenanceId)
            .orElseThrow(() -> new MaintenanceNotFoundException(maintenanceId));
        if (maintenance.getStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spare part requests can only be created when maintenance is IN_PROGRESS");
        }

        UUID technicianId = requireSubject(authentication);
        MaintenancePartRequestEntity partRequest = new MaintenancePartRequestEntity(
            maintenanceId,
            payload.partName(),
            payload.partReference(),
            payload.quantity(),
            payload.reason(),
            technicianId
        );

        MaintenancePartRequestEntity saved = partRequestRepository.saveAndFlush(partRequest);
        return MaintenancePartRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MaintenancePartRequestResponse> getPartRequests(JwtAuthenticationToken authentication, UUID maintenanceId) {
        getById(authentication, maintenanceId);
        return partRequestRepository.findByMaintenanceIdOrderByRequestedAtDesc(maintenanceId)
            .stream()
            .map(MaintenancePartRequestResponse::from)
            .toList();
    }

    @Transactional
    public MaintenancePartRequestResponse approvePartRequest(JwtAuthenticationToken authentication,
                                                             UUID maintenanceId,
                                                             UUID partRequestId) {
        requireManagerOrAdmin(authentication);
        MaintenancePartRequestEntity partRequest = partRequestRepository.findById(partRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part request not found"));
        if (!partRequest.getMaintenanceId().equals(maintenanceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Part request does not belong to specified maintenance");
        }

        String reviewer = getReviewerIdentity(authentication);
        partRequest.approve(reviewer);
        MaintenancePartRequestEntity saved = partRequestRepository.saveAndFlush(partRequest);
        return MaintenancePartRequestResponse.from(saved);
    }

    @Transactional
    public MaintenancePartRequestResponse rejectPartRequest(JwtAuthenticationToken authentication,
                                                            UUID maintenanceId,
                                                            UUID partRequestId) {
        requireManagerOrAdmin(authentication);
        MaintenancePartRequestEntity partRequest = partRequestRepository.findById(partRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part request not found"));
        if (!partRequest.getMaintenanceId().equals(maintenanceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Part request does not belong to specified maintenance");
        }

        String reviewer = getReviewerIdentity(authentication);
        partRequest.reject(reviewer);
        MaintenancePartRequestEntity saved = partRequestRepository.saveAndFlush(partRequest);
        return MaintenancePartRequestResponse.from(saved);
    }

    @Transactional
    public MaintenanceResponse waitOnPart(JwtAuthenticationToken authentication, UUID maintenanceId, UUID partRequestId) {
        verifyAssignedTechnician(authentication, maintenanceId);
        MaintenancePartRequestEntity partRequest = partRequestRepository.findById(partRequestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Part request not found"));
        if (!partRequest.getMaintenanceId().equals(maintenanceId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Part request does not belong to specified maintenance");
        }
        if (partRequest.getStatus() != MaintenancePartRequestStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot wait for a part request that is not APPROVED");
        }

        return transition(maintenanceId, MaintenanceEntity::waitOnPart, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(maintenanceId, previous, current));
    }

    @Transactional
    public MaintenanceResponse resumeFromPartWait(JwtAuthenticationToken authentication, UUID maintenanceId) {
        verifyAssignedTechnician(authentication, maintenanceId);
        return transition(maintenanceId, MaintenanceEntity::resumeFromPartWait, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(maintenanceId, previous, current));
    }

    // ── Phase T5: Completion & Client Confirmation ─────────────────────────────

    /**
     * Technician submits maintenance completion report.
     * Transition: IN_PROGRESS → COMPLETION_PENDING_CONFIRMATION.
     * Security: Must be authenticated assigned TECHNICIAN.
     */
    @Transactional
    public MaintenanceResponse submitCompletion(JwtAuthenticationToken authentication, UUID id, SubmitCompletionRequest request) {
        verifyAssignedTechnician(authentication, id);
        String summary = request != null ? request.completionSummary() : null;
        String notes = request != null ? request.interventionNotes() : null;
        return transition(id, maintenance -> maintenance.submitCompletion(summary, notes), (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    /**
     * Client confirms maintenance completion.
     * Transition: COMPLETION_PENDING_CONFIRMATION → COMPLETED.
     * Security: Must be client owner (or Admin/Manager).
     */
    @Transactional
    public MaintenanceResponse confirmCompletion(JwtAuthenticationToken authentication, UUID id) {
        MaintenanceEntity maintenance = findForUpdate(id);
        if (!isStaff(authentication)) {
            String customerRef = requireCustomerReference(authentication);
            if (!maintenance.getCustomerReference().equalsIgnoreCase(customerRef)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to confirm completion for this maintenance request");
            }
        }
        return transition(id, MaintenanceEntity::confirmCompletion, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    // ───────────────────────────────────────────────────────────────────────────

    @Transactional
    public MaintenanceResponse complete(UUID id) {
        return complete(id, null);
    }

    @Transactional
    public MaintenanceResponse complete(UUID id, String interventionNotes) {
        return transition(id, maintenance -> maintenance.complete(interventionNotes), (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    @Transactional
    public MaintenanceResponse cancel(UUID id) {
        return transition(id, MaintenanceEntity::cancel, (previous, current) ->
            MaintenanceHistoryEntity.statusChanged(id, previous, current));
    }

    @Transactional
    public MaintenanceResponse assignTechnician(UUID id, UUID technicianId) {
        MaintenanceEntity maintenance = findForUpdate(id);
        UUID previousTechnician = maintenance.getTechnicianId();
        if (previousTechnician != null && previousTechnician.equals(technicianId)
                && maintenance.getStatus() == MaintenanceStatus.ASSIGNED) {
            return mapper.toResponse(repository.saveAndFlush(maintenance));
        }
        maintenance.assignTechnician(technicianId);
        MaintenanceHistoryEntity event = previousTechnician == null
            ? MaintenanceHistoryEntity.technicianAssigned(id, technicianId)
            : MaintenanceHistoryEntity.technicianReassigned(id, technicianId);
        try {
            MaintenanceEntity saved = repository.saveAndFlush(maintenance);
            writeHistory(event);
            if (eventPublisher != null) eventPublisher.assigned(saved);
            return mapper.toResponse(saved);
        } catch (IllegalStateException | DataIntegrityViolationException exception) {
            throw new MaintenanceDataConflictException();
        }
    }

    @Transactional
    public MaintenanceResponse unassignTechnician(UUID id) {
        MaintenanceEntity maintenance = findForUpdate(id);
        UUID previousTechnician = maintenance.getTechnicianId();
        if (previousTechnician == null) {
            return mapper.toResponse(repository.saveAndFlush(maintenance));
        }
        maintenance.unassignTechnician();
        try {
            MaintenanceEntity saved = repository.saveAndFlush(maintenance);
            writeHistory(MaintenanceHistoryEntity.technicianUnassigned(id, previousTechnician));
            return mapper.toResponse(saved);
        } catch (IllegalStateException | DataIntegrityViolationException exception) {
            throw new MaintenanceDataConflictException();
        }
    }

    private void verifyAssignedTechnician(JwtAuthenticationToken authentication, UUID maintenanceId) {
        if (!isTechnician(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only technicians can perform this action");
        }
        UUID sub = requireSubject(authentication);
        MaintenanceEntity maintenance = repository.findById(maintenanceId)
            .orElseThrow(() -> new MaintenanceNotFoundException(maintenanceId));
        if (!sub.equals(maintenance.getTechnicianId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the technician assigned to this request");
        }
    }

    private void requireManagerOrAdmin(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean isAuthorized = authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager or Admin role required");
        }
    }

    private String getReviewerIdentity(JwtAuthenticationToken authentication) {
        if (authentication != null && authentication.getToken() != null) {
            String email = authentication.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) return email;
            String sub = authentication.getToken().getClaimAsString("sub");
            if (sub != null && !sub.isBlank()) return sub;
        }
        return "manager";
    }

    private MaintenanceResponse transition(UUID id, Consumer<MaintenanceEntity> command,
                                           java.util.function.BiFunction<MaintenanceStatus, MaintenanceStatus, MaintenanceHistoryEntity> historyFactory) {
        MaintenanceEntity maintenance = findForUpdate(id);
        MaintenanceStatus previousStatus = maintenance.getStatus();
        try {
            command.accept(maintenance);
            MaintenanceEntity saved = repository.saveAndFlush(maintenance);
            if (historyRepository != null) {
                writeHistory(historyFactory.apply(previousStatus, saved.getStatus()));
            }
            if (eventPublisher != null && previousStatus != saved.getStatus()) {
                eventPublisher.statusChanged(saved, previousStatus);
            }
            return mapper.toResponse(saved);
        } catch (IllegalStateException | DataIntegrityViolationException exception) {
            throw new MaintenanceDataConflictException();
        }
    }

    private void writeHistory(MaintenanceHistoryEntity event) {
        if (historyRepository == null) {
            return;
        }
        historyRepository.saveAndFlush(event);
    }

    private MaintenanceEntity findForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
            .orElseThrow(() -> new MaintenanceNotFoundException(id));
    }

    private String resolveCustomerReference(JwtAuthenticationToken authentication, String fallbackRef) {
        if (authentication != null && authentication.getToken() != null) {
            String email = authentication.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return (fallbackRef != null && !fallbackRef.isBlank()) ? fallbackRef : "anonymous";
    }

    private String requireCustomerReference(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String email = authentication.getToken().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication email missing");
        }
        return email;
    }

    private UUID requireSubject(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String sub = authentication.getToken().getClaimAsString("sub");
        if (sub == null || sub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject claim missing");
        }
        try {
            return UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is not a valid UUID");
        }
    }

    private boolean isStaff(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                    || a.getAuthority().equals("ROLE_MANAGER")
                    || a.getAuthority().equals("ROLE_COMMERCIAL"));
    }

    private boolean isTechnician(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_TECHNICIAN"));
    }
}

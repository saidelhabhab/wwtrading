package ma.wwtrading.quotations.prospects.application;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.quotations.prospects.api.ProspectDtos.ProspectRequest;
import ma.wwtrading.quotations.prospects.api.ProspectDtos.ProspectResponse;
import ma.wwtrading.quotations.prospects.domain.ProspectEntity;
import ma.wwtrading.quotations.prospects.domain.ProspectRepository;

@Service
public class ProspectApplicationService {

    private final ProspectRepository repository;

    public ProspectApplicationService(ProspectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProspectResponse create(JwtAuthenticationToken authentication, ProspectRequest request) {
        requireCommercialOrManagement(authentication);
        String subject = subject(authentication);
        ProspectEntity entity = new ProspectEntity(
                request.companyName(), request.contactName(), request.phone(), request.email(),
                request.city(), request.activity(), request.source(), request.status(), request.notes(), subject);
        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<ProspectResponse> list(JwtAuthenticationToken authentication, Pageable pageable) {
        if (isManagement(authentication)) {
            return repository.findAllByOrderByUpdatedAtDesc(pageable).map(this::toResponse);
        }
        requireCommercial(authentication);
        return repository.findByAssignedCommercialSubjectOrderByUpdatedAtDesc(subject(authentication), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProspectResponse get(JwtAuthenticationToken authentication, UUID id) {
        ProspectEntity entity = require(id);
        authorize(authentication, entity);
        return toResponse(entity);
    }

    @Transactional
    public ProspectResponse update(JwtAuthenticationToken authentication, UUID id, ProspectRequest request) {
        ProspectEntity entity = require(id);
        authorize(authentication, entity);
        entity.update(request.companyName(), request.contactName(), request.phone(), request.email(),
                request.city(), request.activity(), request.source(), request.status(), request.notes());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(JwtAuthenticationToken authentication, UUID id) {
        ProspectEntity entity = require(id);
        authorize(authentication, entity);
        repository.delete(entity);
    }

    private ProspectEntity require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prospect not found"));
    }

    private void authorize(JwtAuthenticationToken authentication, ProspectEntity entity) {
        if (isManagement(authentication)) {
            return;
        }
        requireCommercial(authentication);
        if (!subject(authentication).equals(entity.getAssignedCommercialSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Prospect access denied");
        }
    }

    private void requireCommercialOrManagement(JwtAuthenticationToken authentication) {
        if (!isCommercial(authentication) && !isManagement(authentication)) {
            throw forbidden();
        }
    }

    private void requireCommercial(JwtAuthenticationToken authentication) {
        if (!isCommercial(authentication)) {
            throw forbidden();
        }
    }

    private boolean isCommercial(JwtAuthenticationToken authentication) {
        return hasRole(authentication, "COMMERCIAL");
    }

    private boolean isManagement(JwtAuthenticationToken authentication) {
        return hasRole(authentication, "ADMIN") || hasRole(authentication, "MANAGER");
    }

    private boolean hasRole(JwtAuthenticationToken authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
    }

    private String subject(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null
                || authentication.getToken().getSubject() == null
                || authentication.getToken().getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated JWT subject is required");
        }
        return authentication.getToken().getSubject();
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Prospect access denied");
    }

    private ProspectResponse toResponse(ProspectEntity entity) {
        return new ProspectResponse(entity.getId(), entity.getCompanyName(), entity.getContactName(),
                entity.getPhone(), entity.getEmail(), entity.getCity(), entity.getActivity(), entity.getSource(),
                entity.getStatus(), entity.getNotes(), entity.getAssignedCommercialSubject(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}

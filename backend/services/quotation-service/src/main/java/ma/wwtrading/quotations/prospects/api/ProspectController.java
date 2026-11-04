package ma.wwtrading.quotations.prospects.api;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import ma.wwtrading.quotations.prospects.api.ProspectDtos.ProspectRequest;
import ma.wwtrading.quotations.prospects.api.ProspectDtos.ProspectResponse;
import ma.wwtrading.quotations.prospects.application.ProspectApplicationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prospects")
public class ProspectController {

    private final ProspectApplicationService service;

    public ProspectController(ProspectApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ProspectResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody ProspectRequest request) {
        ProspectResponse response = service.create(authentication, request);
        return ResponseEntity.created(URI.create("/api/prospects/" + response.id())).body(response);
    }

    @GetMapping
    public Page<ProspectResponse> list(
            JwtAuthenticationToken authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(authentication, pageable);
    }

    @GetMapping("/{id}")
    public ProspectResponse get(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        return service.get(authentication, id);
    }

    @PutMapping("/{id}")
    public ProspectResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID id,
            @Valid @RequestBody ProspectRequest request) {
        return service.update(authentication, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(JwtAuthenticationToken authentication, @PathVariable UUID id) {
        service.delete(authentication, id);
        return ResponseEntity.noContent().build();
    }
}

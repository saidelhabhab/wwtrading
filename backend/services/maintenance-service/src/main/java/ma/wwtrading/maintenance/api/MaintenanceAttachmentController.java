package ma.wwtrading.maintenance.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.maintenance.application.MaintenanceAttachmentApplicationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/maintenance/{maintenanceId}/attachments")
public class MaintenanceAttachmentController {

    private final MaintenanceAttachmentApplicationService service;

    public MaintenanceAttachmentController(MaintenanceAttachmentApplicationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<MaintenanceAttachmentResponse>> upload(
            JwtAuthenticationToken authentication,
            @PathVariable UUID maintenanceId,
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(authentication, maintenanceId, files));
    }

    @GetMapping
    public List<MaintenanceAttachmentResponse> list(
            JwtAuthenticationToken authentication,
            @PathVariable UUID maintenanceId) {
        return service.list(authentication, maintenanceId);
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> content(
            JwtAuthenticationToken authentication,
            @PathVariable UUID maintenanceId,
            @PathVariable UUID attachmentId) throws IOException {
        var content = service.content(authentication, maintenanceId, attachmentId);
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .contentLength(content.resource().contentLength())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }
}

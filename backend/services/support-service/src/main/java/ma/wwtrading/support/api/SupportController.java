package ma.wwtrading.support.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.wwtrading.support.api.SupportDtos.AssignConversationRequest;
import ma.wwtrading.support.api.SupportDtos.AttachmentResponse;
import ma.wwtrading.support.api.SupportDtos.ChangeStatusRequest;
import ma.wwtrading.support.api.SupportDtos.ConversationResponse;
import ma.wwtrading.support.api.SupportDtos.CreateConversationRequest;
import ma.wwtrading.support.api.SupportDtos.MessageResponse;
import ma.wwtrading.support.api.SupportDtos.SendMessageRequest;
import ma.wwtrading.support.application.SupportApplicationService;
import ma.wwtrading.support.domain.SupportStatus;

@RestController
@RequestMapping("/api/support")
@Tag(name = "Support Chat", description = "Client and authorized support-agent conversations")
@SecurityRequirement(name = "keycloakBearer")
public class SupportController {

    private final SupportApplicationService service;

    public SupportController(SupportApplicationService service) {
        this.service = service;
    }

    @PostMapping("/conversations")
    @Operation(summary = "Create a client support conversation with its first message")
    public ResponseEntity<ConversationResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication, request));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List the authenticated client's conversations")
    public Page<ConversationResponse> listMine(
            JwtAuthenticationToken authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listMine(authentication, pageable);
    }

    @GetMapping("/agent/conversations")
    @Operation(summary = "List conversations visible to the authenticated support agent")
    public Page<ConversationResponse> listForAgent(
            JwtAuthenticationToken authentication,
            @RequestParam(required = false) SupportStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listForAgent(authentication, status, pageable);
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Get an authorized support conversation")
    public ConversationResponse get(JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId) {
        return service.get(authentication, conversationId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Get authorized conversation messages in chronological order")
    public Page<MessageResponse> messages(
            JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.messages(authentication, conversationId, page, size);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a message as the authenticated user")
    public ResponseEntity<MessageResponse> send(
            JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(authentication, conversationId, request));
    }

    @PutMapping("/conversations/{conversationId}/assign")
    @Operation(summary = "Assign an enabled COMMERCIAL agent")
    public ConversationResponse assign(
            JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody AssignConversationRequest request) {
        return service.assign(authentication, conversationId, request.agentKeycloakSubject());
    }

    @PutMapping("/conversations/{conversationId}/status")
    @Operation(summary = "Apply a legal support conversation status transition")
    public ConversationResponse changeStatus(
            JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(authentication, conversationId, request);
    }

    @PostMapping(value = "/conversations/{conversationId}/messages/{messageId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a validated attachment to a conversation message")
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.upload(authentication, conversationId, messageId, file));
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/attachments")
    @Operation(summary = "List attachment metadata for an authorized conversation message")
    public List<AttachmentResponse> attachments(
            JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId) {
        return service.listAttachments(authentication, conversationId, messageId);
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentId}/content")
    @Operation(summary = "Download authorized support attachment content")
    public ResponseEntity<org.springframework.core.io.Resource> attachmentContent(
            JwtAuthenticationToken authentication,
            @PathVariable UUID conversationId,
            @PathVariable UUID messageId,
            @PathVariable UUID attachmentId) throws IOException {
        var content = service.attachmentContent(authentication, conversationId, messageId, attachmentId);
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .contentLength(content.resource().contentLength())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.filename()).build().toString())
                .body(content.resource());
    }
}

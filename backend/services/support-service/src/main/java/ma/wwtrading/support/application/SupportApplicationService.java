package ma.wwtrading.support.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import ma.wwtrading.support.api.SupportDtos.AttachmentResponse;
import ma.wwtrading.support.api.SupportDtos.ChangeStatusRequest;
import ma.wwtrading.support.api.SupportDtos.ConversationResponse;
import ma.wwtrading.support.api.SupportDtos.CreateConversationRequest;
import ma.wwtrading.support.api.SupportDtos.MessageResponse;
import ma.wwtrading.support.api.SupportDtos.SendMessageRequest;
import ma.wwtrading.support.domain.SupportAttachmentEntity;
import ma.wwtrading.support.domain.SupportAttachmentRepository;
import ma.wwtrading.support.domain.SupportConversationEntity;
import ma.wwtrading.support.domain.SupportConversationRepository;
import ma.wwtrading.support.domain.SupportMessageEntity;
import ma.wwtrading.support.domain.SupportMessageRepository;
import ma.wwtrading.support.domain.SupportSenderRole;
import ma.wwtrading.support.domain.SupportStatus;
import ma.wwtrading.support.infrastructure.SupportAttachmentStorage;
import ma.wwtrading.support.infrastructure.kafka.SupportEventPublisher;

@Service
public class SupportApplicationService {

    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
    private static final Set<String> PNG_EXTENSIONS = Set.of("png");
    private static final Set<String> WEBP_EXTENSIONS = Set.of("webp");
    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

    private final SupportConversationRepository conversations;
    private final SupportMessageRepository messages;
    private final SupportAttachmentRepository attachments;
    private final SupportAttachmentStorage storage;
    private final AgentDirectoryClient agentDirectory;
    private final long maximumAttachmentSize;
    private SupportEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setEventPublisher(SupportEventPublisher eventPublisher) { this.eventPublisher = eventPublisher; }

    public SupportApplicationService(
            SupportConversationRepository conversations,
            SupportMessageRepository messages,
            SupportAttachmentRepository attachments,
            SupportAttachmentStorage storage,
            AgentDirectoryClient agentDirectory,
            @Value("${support.attachment.max-size:10MB}") DataSize maximumAttachmentSize) {
        this.conversations = conversations;
        this.messages = messages;
        this.attachments = attachments;
        this.storage = storage;
        this.agentDirectory = agentDirectory;
        this.maximumAttachmentSize = maximumAttachmentSize.toBytes();
    }

    @Transactional
    public ConversationResponse create(JwtAuthenticationToken authentication, CreateConversationRequest request) {
        requireRole(authentication, "CLIENT");
        String clientSubject = subject(authentication);
        SupportConversationEntity conversation = conversations.save(
                new SupportConversationEntity(clientSubject, request.subject()));
        SupportMessageEntity message = messages.save(new SupportMessageEntity(conversation.getId(), clientSubject,
                SupportSenderRole.CLIENT, request.message()));
        if (eventPublisher != null) eventPublisher.messageCreated(conversation, message);
        return toConversation(conversation);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> listMine(JwtAuthenticationToken authentication, Pageable pageable) {
        requireRole(authentication, "CLIENT");
        return conversations.findByClientKeycloakSubject(subject(authentication), conversationPage(pageable))
                .map(this::toConversation);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> listForAgent(
            JwtAuthenticationToken authentication,
            SupportStatus status,
            Pageable pageable) {

        Pageable requested = conversationPage(pageable);

        Page<SupportConversationEntity> result;

        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "MANAGER")
                || hasRole(authentication, "COMMERCIAL")) {

            result = status == null
                    ? conversations.findAll(requested)
                    : conversations.findByStatus(status, requested);

        } else {
            throw forbidden();
        }

        return result.map(this::toConversation);
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(JwtAuthenticationToken authentication, UUID conversationId) {
        SupportConversationEntity conversation = requireConversation(conversationId);
        authorize(authentication, conversation);
        return toConversation(conversation);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> messages(JwtAuthenticationToken authentication, UUID conversationId,
            int page, int size) {
        SupportConversationEntity conversation = requireConversation(conversationId);
        authorize(authentication, conversation);
        PageRequest request = PageRequest.of(nonNegative(page), boundedSize(size, 50),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        return messages.findByConversationIdOrderByCreatedAtAsc(conversationId, request).map(this::toMessage);
    }

    @Transactional
    public MessageResponse send(JwtAuthenticationToken authentication, UUID conversationId,
            SendMessageRequest request) {
        SupportConversationEntity conversation = requireConversationForUpdate(conversationId);
        authorize(authentication, conversation);
        requireOpen(conversation);
        SupportMessageEntity message = messages.save(new SupportMessageEntity(conversationId,
                subject(authentication), senderRole(authentication), request.content()));
        conversation.touch();
        conversations.save(conversation);
        if (eventPublisher != null) eventPublisher.messageCreated(conversation, message);
        return toMessage(message);
    }

    @Transactional
    public ConversationResponse assign(JwtAuthenticationToken authentication, UUID conversationId,
            String agentKeycloakSubject) {
        requireManagement(authentication);
        agentDirectory.requireCommercialAgent(authentication, agentKeycloakSubject);
        SupportConversationEntity conversation = requireConversationForUpdate(conversationId);
        conversation.assign(agentKeycloakSubject);
        return toConversation(conversations.save(conversation));
    }

    @Transactional
    public ConversationResponse changeStatus(JwtAuthenticationToken authentication, UUID conversationId,
            ChangeStatusRequest request) {
        SupportConversationEntity conversation = requireConversationForUpdate(conversationId);
        authorizeAgent(authentication, conversation);
        try {
            conversation.changeStatus(request.status());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid support status transition");
        }
        return toConversation(conversations.save(conversation));
    }

    @Transactional
    public AttachmentResponse upload(JwtAuthenticationToken authentication, UUID conversationId,
            UUID messageId, MultipartFile file) {
        SupportConversationEntity conversation = requireConversationForUpdate(conversationId);
        authorize(authentication, conversation);
        requireOpen(conversation);
        messages.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support message not found"));
        FileType fileType = validate(file);
        String storageKey;
        try (InputStream content = file.getInputStream()) {
            storageKey = storage.store(conversationId, messageId, fileType.extension(), content);
        } catch (IOException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Support attachment could not be stored");
        }
        registerRollbackCleanup(storageKey);
        SupportAttachmentEntity entity = new SupportAttachmentEntity(messageId, subject(authentication),
                safeFilename(file.getOriginalFilename()), fileType.mediaType(), file.getSize(), storageKey);
        try {
            return toAttachment(conversationId, attachments.saveAndFlush(entity));
        } catch (RuntimeException exception) {
            safeDelete(storageKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(JwtAuthenticationToken authentication, UUID conversationId,
            UUID messageId) {
        SupportConversationEntity conversation = requireConversation(conversationId);
        authorize(authentication, conversation);
        messages.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support message not found"));
        return attachments.findByMessageIdOrderByCreatedAtAsc(messageId).stream()
                .map(attachment -> toAttachment(conversationId, attachment)).toList();
    }

    @Transactional(readOnly = true)
    public AttachmentContent attachmentContent(JwtAuthenticationToken authentication, UUID conversationId,
            UUID messageId, UUID attachmentId) {
        SupportConversationEntity conversation = requireConversation(conversationId);
        authorize(authentication, conversation);
        messages.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support message not found"));
        SupportAttachmentEntity attachment = attachments.findByIdAndMessageId(attachmentId, messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support attachment not found"));
        try {
            return new AttachmentContent(new FileSystemResource(storage.resolve(attachment.getStorageKey())),
                    MediaType.parseMediaType(attachment.getContentType()), attachment.getOriginalFilename());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Support attachment not found");
        }
    }

    private SupportConversationEntity requireConversation(UUID id) {
        return conversations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support conversation not found"));
    }

    private SupportConversationEntity requireConversationForUpdate(UUID id) {
        return conversations.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Support conversation not found"));
    }

    private void authorize(
            JwtAuthenticationToken authentication,
            SupportConversationEntity conversation) {

        String authenticatedSubject = subject(authentication);

        // ADMIN and MANAGER can access all conversations
        if (hasRole(authentication, "ADMIN")
                || hasRole(authentication, "MANAGER")) {
            return;
        }

        // COMMERCIAL can access all client support conversations
        // Assignment is NOT required for access.
        if (hasRole(authentication, "COMMERCIAL")) {
            return;
        }

        // CLIENT can access only their own conversations
        if (hasRole(authentication, "CLIENT")
                && authenticatedSubject.equals(conversation.getClientKeycloakSubject())) {
            return;
        }

        throw forbidden();
    }

    private void authorizeAgent(
            JwtAuthenticationToken authentication,
            SupportConversationEntity conversation) {

        if (hasRole(authentication, "ADMIN")
                || hasRole(authentication, "MANAGER")
                || hasRole(authentication, "COMMERCIAL")) {
            return;
        }

        throw forbidden();
    }

    private void requireManagement(JwtAuthenticationToken authentication) {
        if (!hasRole(authentication, "ADMIN") && !hasRole(authentication, "MANAGER")) {
            throw forbidden();
        }
    }

    private void requireRole(JwtAuthenticationToken authentication, String role) {
        if (!hasRole(authentication, role)) {
            throw forbidden();
        }
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

    private SupportSenderRole senderRole(JwtAuthenticationToken authentication) {
        if (hasRole(authentication, "ADMIN")) {
            return SupportSenderRole.ADMIN;
        }
        if (hasRole(authentication, "MANAGER")) {
            return SupportSenderRole.MANAGER;
        }
        if (hasRole(authentication, "COMMERCIAL")) {
            return SupportSenderRole.COMMERCIAL;
        }
        if (hasRole(authentication, "CLIENT")) {
            return SupportSenderRole.CLIENT;
        }
        throw forbidden();
    }

    private void requireOpen(SupportConversationEntity conversation) {
        if (conversation.getStatus() == SupportStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Closed conversations do not accept messages");
        }
    }

    private Pageable conversationPage(Pageable pageable) {
        return PageRequest.of(nonNegative(pageable.getPageNumber()), boundedSize(pageable.getPageSize(), 20),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private int nonNegative(int value) {
        return Math.max(value, 0);
    }

    private int boundedSize(int value, int fallback) {
        return value < 1 ? fallback : Math.min(value, 100);
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Support conversation access denied");
    }

    private ConversationResponse toConversation(SupportConversationEntity entity) {
        return new ConversationResponse(entity.getId(), entity.getSubject(), entity.getStatus(),
                entity.getClientKeycloakSubject(), entity.getAssignedAgentKeycloakSubject(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getClosedAt());
    }

    private MessageResponse toMessage(SupportMessageEntity entity) {
        List<AttachmentResponse> messageAttachments = attachments.findByMessageIdOrderByCreatedAtAsc(entity.getId())
                .stream().map(attachment -> toAttachment(entity.getConversationId(), attachment)).toList();
        return new MessageResponse(entity.getId(), entity.getConversationId(), entity.getSenderKeycloakSubject(),
                entity.getSenderRole(), entity.getContent(), entity.getCreatedAt(), messageAttachments);
    }

    private AttachmentResponse toAttachment(UUID conversationId, SupportAttachmentEntity entity) {
        String contentUrl = "/api/support/conversations/" + conversationId + "/messages/" + entity.getMessageId()
                + "/attachments/" + entity.getId() + "/content";
        return new AttachmentResponse(entity.getId(), entity.getMessageId(), entity.getOriginalFilename(),
                entity.getContentType(), entity.getFileSize(), contentUrl, entity.getCreatedAt());
    }

    private FileType validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment file is empty");
        }
        if (file.getSize() > maximumAttachmentSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Attachment exceeds the configured maximum size");
        }
        String filename = safeFilename(file.getOriginalFilename());
        int separator = filename.lastIndexOf('.');
        if (separator < 1 || separator == filename.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment filename must include an extension");
        }
        String extension = filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        FileType detected = detectContent(file);
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(declared)) {
            declared = MediaType.IMAGE_JPEG_VALUE;
        }
        if (!detected.extensions().contains(extension) || !detected.mediaType().equals(declared)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Attachment extension, media type and content do not match");
        }
        return new FileType(extension, detected.mediaType(), detected.extensions());
    }

    private FileType detectContent(MultipartFile file) {
        byte[] header;
        try (InputStream content = file.getInputStream()) {
            header = content.readNBytes(12);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment content could not be read");
        }
        if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                && (header[2] & 0xff) == 0xff) {
            return new FileType("jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_EXTENSIONS);
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (header.length >= png.length && Arrays.equals(Arrays.copyOf(header, png.length), png)) {
            return new FileType("png", MediaType.IMAGE_PNG_VALUE, PNG_EXTENSIONS);
        }
        if (header.length >= 12 && "RIFF".equals(new String(header, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
            return new FileType("webp", "image/webp", WEBP_EXTENSIONS);
        }
        if (header.length >= 5 && "%PDF-".equals(new String(header, 0, 5, StandardCharsets.US_ASCII))) {
            return new FileType("pdf", MediaType.APPLICATION_PDF_VALUE, PDF_EXTENSIONS);
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Only JPEG, PNG, WEBP and PDF attachments are supported");
    }

    private String safeFilename(String filename) {
        String normalized = filename == null ? "" : filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (basename.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment filename is required");
        }
        return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
    }

    private void registerRollbackCleanup(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        safeDelete(storageKey);
                    }
                }
            });
        }
    }

    private void safeDelete(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException ignored) {
        }
    }

    public record AttachmentContent(Resource resource, MediaType mediaType, String filename) {

    }

    private record FileType(String extension, String mediaType, Set<String> extensions) {

    }
}

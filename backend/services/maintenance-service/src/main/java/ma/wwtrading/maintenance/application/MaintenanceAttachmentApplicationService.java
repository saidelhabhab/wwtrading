package ma.wwtrading.maintenance.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import ma.wwtrading.maintenance.api.MaintenanceAttachmentResponse;
import ma.wwtrading.maintenance.domain.MaintenanceAttachmentEntity;
import ma.wwtrading.maintenance.domain.MaintenanceAttachmentRepository;
import ma.wwtrading.maintenance.infrastructure.MaintenanceAttachmentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

@Service
public class MaintenanceAttachmentApplicationService {

    private static final int MAXIMUM_IMAGES = 5;
    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
    private static final Set<String> PNG_EXTENSIONS = Set.of("png");
    private static final Set<String> WEBP_EXTENSIONS = Set.of("webp");

    private final MaintenanceApplicationService maintenanceService;
    private final MaintenanceAttachmentRepository repository;
    private final MaintenanceAttachmentStorage storage;
    private final long maximumSize;

    public MaintenanceAttachmentApplicationService(
        MaintenanceApplicationService maintenanceService,
        MaintenanceAttachmentRepository repository,
        MaintenanceAttachmentStorage storage,
        @Value("${maintenance.attachment.max-size:10MB}") DataSize maximumSize
    ) {
        this.maintenanceService = maintenanceService;
        this.repository = repository;
        this.storage = storage;
        this.maximumSize = maximumSize.toBytes();
    }

    @Transactional
    public List<MaintenanceAttachmentResponse> upload(JwtAuthenticationToken authentication,
                                                      UUID maintenanceId,
                                                      List<MultipartFile> files) {
        maintenanceService.getById(authentication, maintenanceId);
        if (files == null || files.isEmpty()) {
            throw invalid("At least one image is required");
        }
        if (repository.countByMaintenanceId(maintenanceId) + files.size() > MAXIMUM_IMAGES) {
            throw invalid("A maintenance request can contain at most five images");
        }

        List<MaintenanceAttachmentResponse> uploaded = new ArrayList<>();
        for (MultipartFile file : files) {
            ImageType type = validate(file);
            String storageKey;
            try (InputStream content = file.getInputStream()) {
                storageKey = storage.store(maintenanceId, type.extension(), content);
            } catch (IOException | IllegalStateException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "The maintenance image could not be stored");
            }
            registerRollbackCleanup(storageKey);
            String filename = safeFilename(file.getOriginalFilename(), type.extension());
            MaintenanceAttachmentEntity entity = new MaintenanceAttachmentEntity(
                    maintenanceId, storageKey, filename, type.mediaType(), file.getSize());
            try {
                uploaded.add(toResponse(repository.saveAndFlush(entity)));
            } catch (RuntimeException exception) {
                safeDelete(storageKey);
                throw exception;
            }
        }
        return uploaded;
    }

    @Transactional(readOnly = true)
    public List<MaintenanceAttachmentResponse> list(JwtAuthenticationToken authentication, UUID maintenanceId) {
        maintenanceService.getById(authentication, maintenanceId);
        return repository.findByMaintenanceIdOrderByCreatedAtAsc(maintenanceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentContent content(JwtAuthenticationToken authentication, UUID maintenanceId, UUID attachmentId) {
        maintenanceService.getById(authentication, maintenanceId);
        MaintenanceAttachmentEntity attachment = repository.findByIdAndMaintenanceId(attachmentId, maintenanceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance image not found"));
        try {
            Path path = storage.resolve(attachment.getStorageKey());
            return new AttachmentContent(new FileSystemResource(path),
                    MediaType.parseMediaType(attachment.getContentType()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Maintenance image not found");
        }
    }

    private ImageType validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw invalid("The image file is empty");
        }
        if (file.getSize() > maximumSize) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The image exceeds the configured maximum size");
        }
        String extension = extension(file.getOriginalFilename());
        String declaredType = normalizeMediaType(file.getContentType());
        ImageType detected = detectContent(file);
        if (!detected.extensions().contains(extension) || !detected.mediaType().equals(declaredType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "The image extension, media type and content do not match");
        }
        return new ImageType(extension, detected.mediaType(), detected.extensions());
    }

    private ImageType detectContent(MultipartFile file) {
        byte[] header;
        try (InputStream content = file.getInputStream()) {
            header = content.readNBytes(12);
        } catch (IOException exception) {
            throw invalid("The image content could not be read");
        }
        if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                && (header[2] & 0xff) == 0xff) {
            return new ImageType("jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_EXTENSIONS);
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (header.length >= png.length && Arrays.equals(Arrays.copyOf(header, png.length), png)) {
            return new ImageType("png", MediaType.IMAGE_PNG_VALUE, PNG_EXTENSIONS);
        }
        if (header.length >= 12 && new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            return new ImageType("webp", "image/webp", WEBP_EXTENSIONS);
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Only JPEG, PNG and WEBP images are supported");
    }

    private String extension(String filename) {
        String safe = safeFilename(filename, "");
        int separator = safe.lastIndexOf('.');
        if (separator < 1 || separator == safe.length() - 1) {
            throw invalid("The image filename must include an extension");
        }
        String extension = safe.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!JPEG_EXTENSIONS.contains(extension) && !PNG_EXTENSIONS.contains(extension)
                && !WEBP_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only JPEG, PNG and WEBP images are supported");
        }
        return extension;
    }

    private String safeFilename(String filename, String fallbackExtension) {
        String normalized = filename == null ? "" : filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (basename.isBlank()) {
            return "photo." + fallbackExtension;
        }
        return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null) return "";
        return mediaType.equalsIgnoreCase("image/jpg")
                ? MediaType.IMAGE_JPEG_VALUE
                : mediaType.toLowerCase(Locale.ROOT);
    }

    private MaintenanceAttachmentResponse toResponse(MaintenanceAttachmentEntity attachment) {
        return new MaintenanceAttachmentResponse(
                attachment.getId(), attachment.getOriginalFilename(), attachment.getContentType(),
                attachment.getFileSize(), "/api/maintenance/" + attachment.getMaintenanceId()
                        + "/attachments/" + attachment.getId() + "/content",
                attachment.getCreatedAt());
    }

    private void registerRollbackCleanup(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) safeDelete(storageKey);
                }
            });
        }
    }

    private void safeDelete(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException ignored) {
            // Database metadata remains authoritative; orphan cleanup can be retried operationally.
        }
    }

    private ResponseStatusException invalid(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    public record AttachmentContent(Resource resource, MediaType mediaType) {
    }

    private record ImageType(String extension, String mediaType, Set<String> extensions) {
    }
}

package ma.wwtrading.products.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import ma.wwtrading.products.api.ProductImageResponse;
import ma.wwtrading.products.domain.ProductEntity;
import ma.wwtrading.products.domain.ProductImageEntity;
import ma.wwtrading.products.domain.ProductImageRepository;
import ma.wwtrading.products.domain.ProductRepository;
import ma.wwtrading.products.infrastructure.ProductImageStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ProductImageApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductImageApplicationService.class);

    private static final Set<String> JPEG_EXTENSIONS = Set.of("jpg", "jpeg");
    private static final Set<String> PNG_EXTENSIONS = Set.of("png");
    private static final Set<String> WEBP_EXTENSIONS = Set.of("webp");

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductImageStorage storage;
    private final EntityManager entityManager;
    private final long maximumSize;

    public ProductImageApplicationService(
        ProductRepository productRepository,
        ProductImageRepository imageRepository,
        ProductImageStorage storage,
        EntityManager entityManager,
        @Value("${product.image.max-size:10MB}") DataSize maximumSize
    ) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.storage = storage;
        this.entityManager = entityManager;
        this.maximumSize = maximumSize.toBytes();
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> list(UUID productId) {
        requireProduct(productId);
        return imageRepository.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProductImageContent content(UUID productId, UUID imageId) {
        requireProduct(productId);
        ProductImageEntity image = findImage(productId, imageId);
        try {
            Path path = storage.resolve(image.getStorageKey());
            return new ProductImageContent(new FileSystemResource(path), mediaType(image.getStorageKey()));
        } catch (IllegalArgumentException exception) {
            throw ProductImageException.notFound();
        }
    }

    @Transactional
    public ProductImageResponse upload(UUID productId, MultipartFile file) {
        ProductEntity product = lockProduct(productId);
        ImageType imageType = validate(file);
        List<ProductImageEntity> images = imageRepository
            .findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId);
        int displayOrder = images.isEmpty() ? 0 : images.get(images.size() - 1).getDisplayOrder() + 1;
        String storageKey;
        try (InputStream content = file.getInputStream()) {
            storageKey = storage.store(productId, "upload." + imageType.extension(), content);
        } catch (IOException | IllegalStateException exception) {
            LOGGER.error("Product image storage failed for product {} and filename {}", productId,
                file.getOriginalFilename(), exception);
            throw ProductImageException.storageFailure();
        }

        registerRollbackCleanup(storageKey);
        ProductImageEntity image = new ProductImageEntity(product, storageKey, images.isEmpty(), displayOrder);
        try {
            return toResponse(imageRepository.saveAndFlush(image));
        } catch (RuntimeException exception) {
            safeDelete(storageKey);
            throw exception;
        }
    }

    @Transactional
    public ProductImageResponse setMain(UUID productId, UUID imageId) {
        lockProduct(productId);
        ProductImageEntity selected = findImage(productId, imageId);
        if (selected.isMain()) {
            return toResponse(selected);
        }

        imageRepository.findByProductIdAndMainTrue(productId).ifPresent(ProductImageEntity::clearMain);
        imageRepository.flush();
        selected.markMain();
        return toResponse(imageRepository.saveAndFlush(selected));
    }

    @Transactional
    public void delete(UUID productId, UUID imageId) {
        lockProduct(productId);
        List<ProductImageEntity> images = imageRepository
            .findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId);
        ProductImageEntity selected = images.stream()
            .filter(image -> image.getId().equals(imageId))
            .findFirst()
            .orElseThrow(ProductImageException::notFound);

        List<ProductImageEntity> remaining = images.stream()
            .filter(image -> !image.getId().equals(imageId))
            .toList();
        imageRepository.delete(selected);
        imageRepository.flush();

        for (int index = 0; index < remaining.size(); index++) {
            ProductImageEntity image = remaining.get(index);
            image.changeDisplayOrder(index);
            if (selected.isMain() && index == 0) {
                image.markMain();
            }
        }
        imageRepository.saveAllAndFlush(remaining);
        registerCommitDeletion(selected.getStorageKey());
    }

    @Transactional
    public List<ProductImageResponse> reorder(UUID productId, List<UUID> imageIds) {
        lockProduct(productId);
        List<ProductImageEntity> images = imageRepository
            .findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(productId);
        validateOrder(images, imageIds);

        for (int index = 0; index < imageIds.size(); index++) {
            UUID id = imageIds.get(index);
            int displayOrder = index;
            images.stream()
                .filter(image -> image.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> ProductImageException.invalid("The image order contains an unknown image"))
                .changeDisplayOrder(displayOrder);
        }

        imageRepository.saveAllAndFlush(images);
        return images.stream()
            .sorted(java.util.Comparator.comparingInt(ProductImageEntity::getDisplayOrder))
            .map(this::toResponse)
            .toList();
    }

    private ProductEntity requireProduct(UUID productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private ProductEntity lockProduct(UUID productId) {
        ProductEntity product = entityManager.find(ProductEntity.class, productId, LockModeType.PESSIMISTIC_WRITE);
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }
        return product;
    }

    private ProductImageEntity findImage(UUID productId, UUID imageId) {
        return imageRepository.findByIdAndProductId(imageId, productId)
            .orElseThrow(ProductImageException::notFound);
    }

    private ImageType validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw ProductImageException.invalid("An image file is required");
        }
        if (file.getSize() > maximumSize) {
            throw ProductImageException.tooLarge("The image exceeds the configured maximum size");
        }

        String extension = extension(file.getOriginalFilename());
        String declaredType = normalizeMediaType(file.getContentType());
        ImageType detected = detectContent(file);
        if (!detected.extensions().contains(extension) || !detected.mediaType().equals(declaredType)) {
            throw ProductImageException.unsupported("The image extension, media type and content do not match");
        }
        return detected.withExtension(extension);
    }

    private ImageType detectContent(MultipartFile file) {
        byte[] header;
        try (InputStream content = file.getInputStream()) {
            header = content.readNBytes(12);
        } catch (IOException exception) {
            throw ProductImageException.invalid("The image content could not be read");
        }

        if (header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
            && (header[2] & 0xff) == 0xff) {
            return new ImageType("jpg", MediaType.IMAGE_JPEG_VALUE, JPEG_EXTENSIONS);
        }
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (header.length >= png.length && Arrays.equals(Arrays.copyOf(header, png.length), png)) {
            return new ImageType("png", MediaType.IMAGE_PNG_VALUE, PNG_EXTENSIONS);
        }
        if (header.length >= 12 && new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
            && new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) {
            return new ImageType("webp", "image/webp", WEBP_EXTENSIONS);
        }
        throw ProductImageException.unsupported("Only JPEG, PNG and WEBP images are supported");
    }

    private String extension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw ProductImageException.invalid("The image filename must include an extension");
        }
        String normalized = filename.replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        int separator = basename.lastIndexOf('.');
        if (separator < 1 || separator == basename.length() - 1) {
            throw ProductImageException.invalid("The image filename must include an extension");
        }
        return basename.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMediaType(String mediaType) {
        if (mediaType == null) {
            return "";
        }
        return mediaType.equalsIgnoreCase("image/jpg") ? MediaType.IMAGE_JPEG_VALUE : mediaType.toLowerCase(Locale.ROOT);
    }

    private MediaType mediaType(String storageKey) {
        String extension = extension(storageKey);
        if (JPEG_EXTENSIONS.contains(extension)) {
            return MediaType.IMAGE_JPEG;
        }
        if (PNG_EXTENSIONS.contains(extension)) {
            return MediaType.IMAGE_PNG;
        }
        if (WEBP_EXTENSIONS.contains(extension)) {
            return MediaType.parseMediaType("image/webp");
        }
        throw ProductImageException.notFound();
    }

    private void validateOrder(List<ProductImageEntity> images, List<UUID> imageIds) {
        if (imageIds == null || imageIds.size() != images.size()) {
            throw ProductImageException.invalid("The image order must contain every product image exactly once");
        }
        Set<UUID> requested = new HashSet<>(imageIds);
        Set<UUID> existing = images.stream().map(ProductImageEntity::getId).collect(java.util.stream.Collectors.toSet());
        if (requested.size() != imageIds.size() || !requested.equals(existing)) {
            throw ProductImageException.invalid("The image order contains duplicates, missing images or unknown images");
        }
    }

    private ProductImageResponse toResponse(ProductImageEntity image) {
        UUID productId = image.getProduct().getId();
        return new ProductImageResponse(
            image.getId(),
            "/api/products/" + productId + "/images/" + image.getId() + "/content",
            image.isMain(),
            image.getDisplayOrder(),
            image.getCreatedAt(),
            image.getUpdatedAt()
        );
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

    private void registerCommitDeletion(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeDelete(storageKey);
                }
            });
        } else {
            safeDelete(storageKey);
        }
    }

    private void safeDelete(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException ignored) {
            // The database remains authoritative; inaccessible orphan cleanup can be retried operationally.
        }
    }

    public record ProductImageContent(Resource resource, MediaType mediaType) {
    }

    private record ImageType(String extension, String mediaType, Set<String> extensions) {
        ImageType withExtension(String value) {
            return new ImageType(value, mediaType, extensions);
        }
    }
}

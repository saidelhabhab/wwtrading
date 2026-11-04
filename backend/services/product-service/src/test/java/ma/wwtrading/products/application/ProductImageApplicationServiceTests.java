package ma.wwtrading.products.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import ma.wwtrading.products.domain.ProductEntity;
import ma.wwtrading.products.domain.ProductImageEntity;
import ma.wwtrading.products.domain.ProductImageRepository;
import ma.wwtrading.products.domain.ProductRepository;
import ma.wwtrading.products.infrastructure.ProductImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
class ProductImageApplicationServiceTests {

    private static final byte[] PNG = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01, 0x02, 0x03, 0x04
    };

    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository imageRepository;
    @Mock private ProductImageStorage storage;
    @Mock private EntityManager entityManager;

    private ProductImageApplicationService service;
    private ProductEntity product;

    @BeforeEach
    void setUp() {
        service = new ProductImageApplicationService(
            productRepository, imageRepository, storage, entityManager, DataSize.ofMegabytes(10)
        );
        product = new ProductEntity("PR-IMAGE", "Image product", null, BigDecimal.TEN);
        when(entityManager.find(ProductEntity.class, product.getId(), LockModeType.PESSIMISTIC_WRITE))
            .thenReturn(product);
    }

    @Test
    void firstUploadIsMainAndFollowingUploadUsesNextOrder() throws Exception {
        when(storage.store(eq(product.getId()), anyString(), any(InputStream.class)))
            .thenReturn("products/one.png", "products/two.png");
        when(imageRepository.saveAndFlush(any(ProductImageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(imageRepository.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(product.getId()))
            .thenReturn(List.of())
            .thenReturn(List.of(new ProductImageEntity(product, "products/one.png", true, 0)));

        var first = service.upload(product.getId(), png("../unsafe-name.png"));
        var second = service.upload(product.getId(), png("second.png"));

        assertThat(first.main()).isTrue();
        assertThat(first.displayOrder()).isZero();
        assertThat(second.main()).isFalse();
        assertThat(second.displayOrder()).isEqualTo(1);
        assertThat(first.imageUrl()).doesNotContain("products/one.png");
    }

    @Test
    void rejectsEmptyUnsupportedMismatchedAndOversizedFiles() {
        assertImageStatus(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]), HttpStatus.BAD_REQUEST);
        assertImageStatus(new MockMultipartFile("file", "image.gif", "image/gif", "GIF89a".getBytes()),
            HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertImageStatus(new MockMultipartFile("file", "image.jpg", "image/jpeg", PNG),
            HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        var smallLimitService = new ProductImageApplicationService(
            productRepository, imageRepository, storage, entityManager, DataSize.ofBytes(4)
        );
        assertThatThrownBy(() -> smallLimitService.upload(product.getId(), png("image.png")))
            .isInstanceOfSatisfying(ProductImageException.class,
                exception -> assertThat(exception.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void setMainIsTransactionalAndIdempotent() {
        ProductImageEntity oldMain = new ProductImageEntity(product, "products/old.png", true, 0);
        ProductImageEntity selected = new ProductImageEntity(product, "products/new.png", false, 1);
        when(imageRepository.findByIdAndProductId(selected.getId(), product.getId())).thenReturn(Optional.of(selected));
        when(imageRepository.findByProductIdAndMainTrue(product.getId())).thenReturn(Optional.of(oldMain));
        when(imageRepository.saveAndFlush(selected)).thenReturn(selected);

        assertThat(service.setMain(product.getId(), selected.getId()).main()).isTrue();
        assertThat(oldMain.isMain()).isFalse();

        when(imageRepository.findByIdAndProductId(selected.getId(), product.getId())).thenReturn(Optional.of(selected));
        assertThat(service.setMain(product.getId(), selected.getId()).main()).isTrue();
    }

    @Test
    void deletingMainPromotesNextImageAndNormalizesOrder() {
        ProductImageEntity main = new ProductImageEntity(product, "products/main.png", true, 4);
        ProductImageEntity next = new ProductImageEntity(product, "products/next.png", false, 9);
        when(imageRepository.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(product.getId()))
            .thenReturn(List.of(main, next));

        service.delete(product.getId(), main.getId());

        assertThat(next.isMain()).isTrue();
        assertThat(next.getDisplayOrder()).isZero();
        verify(imageRepository).delete(main);
        verify(storage).delete("products/main.png");
    }

    @Test
    void reorderRequiresEveryImageExactlyOnce() {
        ProductImageEntity first = new ProductImageEntity(product, "products/first.png", true, 0);
        ProductImageEntity second = new ProductImageEntity(product, "products/second.png", false, 1);
        when(imageRepository.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(product.getId()))
            .thenReturn(List.of(first, second));

        var reordered = service.reorder(product.getId(), List.of(second.getId(), first.getId()));
        assertThat(reordered).extracting(response -> response.id())
            .containsExactly(second.getId(), first.getId());
        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);

        assertThatThrownBy(() -> service.reorder(product.getId(), List.of(first.getId(), first.getId())))
            .isInstanceOf(ProductImageException.class);
        assertThatThrownBy(() -> service.reorder(product.getId(), List.of(first.getId())))
            .isInstanceOf(ProductImageException.class);
    }

    @Test
    void imageMustBelongToRequestedProduct() {
        UUID unknownImage = UUID.randomUUID();
        when(imageRepository.findByIdAndProductId(unknownImage, product.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setMain(product.getId(), unknownImage))
            .isInstanceOfSatisfying(ProductImageException.class,
                exception -> assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private MockMultipartFile png(String filename) {
        return new MockMultipartFile("file", filename, "image/png", PNG);
    }

    private void assertImageStatus(MockMultipartFile file, HttpStatus status) {
        assertThatThrownBy(() -> service.upload(product.getId(), file))
            .isInstanceOfSatisfying(ProductImageException.class,
                exception -> assertThat(exception.status()).isEqualTo(status));
    }
}

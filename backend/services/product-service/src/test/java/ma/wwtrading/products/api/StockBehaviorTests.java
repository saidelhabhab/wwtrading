package ma.wwtrading.products.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ma.wwtrading.products.application.ProductNotFoundException;
import ma.wwtrading.products.application.StockApplicationService;
import ma.wwtrading.products.application.StockValidationException;
import ma.wwtrading.products.domain.InsufficientStockException;
import ma.wwtrading.products.domain.ProductEntity;
import ma.wwtrading.products.domain.ProductRepository;
import ma.wwtrading.products.domain.StockEntity;
import ma.wwtrading.products.domain.StockInvariantViolationException;
import ma.wwtrading.products.domain.StockMovementEntity;
import ma.wwtrading.products.domain.StockMovementRepository;
import ma.wwtrading.products.domain.StockMovementType;
import ma.wwtrading.products.domain.StockRepository;
import ma.wwtrading.products.domain.StockStatus;

class StockBehaviorTests {

    private final StockRepository stockRepository = mock(StockRepository.class);
    private final StockMovementRepository movementRepository = mock(StockMovementRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final StockApplicationService service = new StockApplicationService(
            stockRepository, movementRepository, productRepository
    );

    private ProductEntity product;

    @BeforeEach
    void setUp() {
        product = new ProductEntity("KM-C360I", "bizhub C360i", null, BigDecimal.TEN);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(productRepository.findByProductReference("KM-C360I")).thenReturn(Optional.of(product));
        when(stockRepository.saveAndFlush(any(StockEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stockRepository.save(any(StockEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.save(any(StockMovementEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private StockEntity existingStock(int onHand, int reserved) {
        StockEntity stock = new StockEntity(product.getId(), onHand, 5);
        if (reserved > 0) {
            stock.reserve(reserved);
        }
        when(stockRepository.findForUpdateByProductId(product.getId())).thenReturn(Optional.of(stock));
        when(stockRepository.findByProductId(product.getId())).thenReturn(Optional.of(stock));
        return stock;
    }

    private void noStockYet() {
        when(stockRepository.findForUpdateByProductId(product.getId())).thenReturn(Optional.empty());
        when(stockRepository.findByProductId(product.getId())).thenReturn(Optional.empty());
    }

    @Test
    void getStockReturnsDerivedBalanceForExistingProduct() {
        existingStock(10, 3);
        var response = service.getStock(product.getId());
        assertThat(response.productId()).isEqualTo(product.getId());
        assertThat(response.productReference()).isEqualTo("KM-C360I");
        assertThat(response.onHandQuantity()).isEqualTo(10);
        assertThat(response.reservedQuantity()).isEqualTo(3);
        assertThat(response.availableQuantity()).isEqualTo(7);
        assertThat(response.status()).isEqualTo(StockStatus.IN_STOCK);
    }

    @Test
    void getStockFallsBackToEmptyBalanceForProductWithoutStockRow() {
        noStockYet();
        var response = service.getStock(product.getId());
        assertThat(response.onHandQuantity()).isZero();
        assertThat(response.availableQuantity()).isZero();
        assertThat(response.status()).isEqualTo(StockStatus.OUT_OF_STOCK);
        verify(stockRepository, never()).save(any(StockEntity.class));
    }

    @Test
    void getStockRejectsUnknownProduct() {
        UUID unknown = UUID.randomUUID();
        when(productRepository.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getStock(unknown))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void stockInIncreasesOnHandAndRecordsInMovement() {
        existingStock(10, 0);
        var response = service.stockIn(
                product.getId(), new StockDtos.StockOperationRequest(5, "PO-1", "Restock", null), "admin");
        assertThat(response.onHandQuantity()).isEqualTo(15);
        ArgumentCaptor<StockMovementEntity> captor = ArgumentCaptor.forClass(StockMovementEntity.class);
        verify(movementRepository).save(captor.capture());
        StockMovementEntity movement = captor.getValue();
        assertThat(movement.getType()).isEqualTo(StockMovementType.IN);
        assertThat(movement.getQuantity()).isEqualTo(5);
        assertThat(movement.getOnHandDelta()).isEqualTo(5);
        assertThat(movement.getActor()).isEqualTo("admin");
    }

    @Test
    void stockOutRejectsInsufficientOnHandStockWithoutRecordingMovement() {
        existingStock(10, 0);
        assertThatThrownBy(() -> service.stockOut(
                product.getId(), new StockDtos.StockOperationRequest(11, null, null, null), "admin"))
                .isInstanceOf(InsufficientStockException.class);
        verify(movementRepository, never()).save(any(StockMovementEntity.class));
    }

    @Test
    void adjustRecordsSignedDelta() {
        existingStock(10, 0);
        var response = service.adjust(
                product.getId(), new StockDtos.StockAdjustRequest(7, "INV-2026", "Physical count"), "manager");
        assertThat(response.onHandQuantity()).isEqualTo(7);
        ArgumentCaptor<StockMovementEntity> captor = ArgumentCaptor.forClass(StockMovementEntity.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(StockMovementType.ADJUST);
        assertThat(captor.getValue().getQuantity()).isEqualTo(3);
        assertThat(captor.getValue().getOnHandDelta()).isEqualTo(-3);
    }

    @Test
    void reserveConsumesAvailableAndRejectsOvercommit() {
        existingStock(10, 4);
        var response = service.reserve(
                product.getId(), new StockDtos.StockOperationRequest(6, "ORD-9", null, null), "admin");
        assertThat(response.availableQuantity()).isZero();
        assertThat(response.reservedQuantity()).isEqualTo(10);
        assertThatThrownBy(() -> service.reserve(
                product.getId(), new StockDtos.StockOperationRequest(1, null, null, null), "admin"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void releaseOnlyReleasesWhatIsCurrentlyReserved() {
        StockEntity stock = existingStock(10, 4);
        service.release(product.getId(), new StockDtos.StockOperationRequest(4, "ORD-9", "Cancelled", null), "admin");
        assertThat(stock.getReservedQuantity()).isZero();
        assertThatThrownBy(() -> service.release(
                product.getId(), new StockDtos.StockOperationRequest(1, null, null, null), "admin"))
                .isInstanceOf(StockInvariantViolationException.class);
    }

    @Test
    void updateLowStockThresholdPersistsNewThreshold() {
        StockEntity stock = existingStock(10, 0);
        service.updateLowStockThreshold(product.getId(), new StockDtos.StockThresholdRequest(2));
        assertThat(stock.getLowStockThreshold()).isEqualTo(2);
        verify(stockRepository).save(stock);
    }

    @Test
    void statusFlagsDriveLowStockAndOutOfStockResponses() {
        existingStock(5, 0);
        assertThat(service.getStock(product.getId()).status()).isEqualTo(StockStatus.LOW_STOCK);
        existingStock(3, 3);
        assertThat(service.getStock(product.getId()).status()).isEqualTo(StockStatus.OUT_OF_STOCK);
        existingStock(50, 0);
        assertThat(service.getStock(product.getId()).status()).isEqualTo(StockStatus.IN_STOCK);
    }

    @Test
    void applyOrderStockEventReservesThenReleasesWithIdempotencyKeys() {
        existingStock(10, 0);
        service.applyOrderStockEvent("ORDER:42:RESERVE",
                new StockDtos.OrderStockRequest(
                        List.of(new StockDtos.OrderStockLine("KM-C360I", 3))), "order-service");
        service.applyOrderStockEvent("ORDER:42:RELEASE",
                new StockDtos.OrderStockRequest(
                        List.of(new StockDtos.OrderStockLine("KM-C360I", 3))), "order-service");
        ArgumentCaptor<StockMovementEntity> captor = ArgumentCaptor.forClass(StockMovementEntity.class);
        verify(movementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(StockMovementEntity::getType)
                .containsExactly(StockMovementType.RESERVE, StockMovementType.RELEASE);
        assertThat(captor.getAllValues())
                .extracting(StockMovementEntity::getIdempotencyKey)
                .containsExactly("ORDER:42:RESERVE:KM-C360I", "ORDER:42:RELEASE:KM-C360I");
        assertThat(captor.getAllValues()).allSatisfy(movement -> assertThat(movement.getOnHandDelta()).isNull());
    }

    @Test
    void applyOrderStockEventDeductsOnHandForOutEvent() {
        StockEntity stock = existingStock(10, 0);
        service.applyOrderStockEvent("ORDER:42:OUT",
                new StockDtos.OrderStockRequest(
                        List.of(new StockDtos.OrderStockLine("KM-C360I", 4))), "order-service");
        assertThat(stock.getOnHandQuantity()).isEqualTo(6);
        assertThat(stock.getReservedQuantity()).isZero();
        ArgumentCaptor<StockMovementEntity> captor = ArgumentCaptor.forClass(StockMovementEntity.class);
        verify(movementRepository).save(captor.capture());
        assertThat(captor.getValue().getOnHandDelta()).isEqualTo(-4);
    }

    @Test
    void applyOrderStockEventIsIdempotentPerKey() {
        when(movementRepository.existsByIdempotencyKey("ORDER:42:RESERVE:KM-C360I")).thenReturn(true);
        service.applyOrderStockEvent("ORDER:42:RESERVE",
                new StockDtos.OrderStockRequest(
                        List.of(new StockDtos.OrderStockLine("KM-C360I", 3))), "order-service");
        verify(stockRepository, never()).findForUpdateByProductId(any(UUID.class));
        verify(movementRepository, never()).save(any(StockMovementEntity.class));
    }

    @Test
    void applyOrderStockEventRejectsUnknownReferenceAndAdjustType() {
        existingStock(10, 0);
        when(productRepository.findByProductReference("MISSING")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.applyOrderStockEvent("ORDER:1:OUT",
                new StockDtos.OrderStockRequest(
                        List.of(new StockDtos.OrderStockLine("MISSING", 1))), "order-service"))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.applyOrderStockEvent("ORDER:1:ADJUST",
                new StockDtos.OrderStockRequest(
                        List.of(new StockDtos.OrderStockLine("KM-C360I", 1))), "order-service"))
                .isInstanceOf(StockValidationException.class);
    }

    @Test
    void orderEventOnProductWithoutStockRowCreatesBalanceThenRejectsReserve() {
        noStockYet();
        assertThatThrownBy(() -> service.applyOrderStockEvent("ORDER:1:RESERVE",
                new StockDtos.OrderStockRequest(
                        List.of(new StockDtos.OrderStockLine("KM-C360I", 3))), "order-service"))
                .isInstanceOf(InsufficientStockException.class);
        verify(stockRepository).saveAndFlush(any(StockEntity.class));
        verify(movementRepository, never()).save(any(StockMovementEntity.class));
    }
}

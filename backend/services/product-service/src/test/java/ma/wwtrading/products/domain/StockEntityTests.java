package ma.wwtrading.products.domain;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class StockEntityTests {

    private final UUID productId = UUID.randomUUID();

    @Test
    void startsAtZeroAndComputesAvailableQuantityFromOnHandMinusReserved() {
        StockEntity stock = new StockEntity(productId, 0, StockEntity.DEFAULT_LOW_STOCK_THRESHOLD);
        assertThat(stock.getOnHandQuantity()).isZero();
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(stock.availableQuantity()).isZero();
        assertThat(stock.getLowStockThreshold()).isEqualTo(StockEntity.DEFAULT_LOW_STOCK_THRESHOLD);
        assertThat(stock.outOfStock()).isTrue();
        assertThat(stock.lowStock()).isFalse();
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThatThrownBy(() -> new StockEntity(null, 0, 5))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StockEntity(productId, -1, 5))
            .isInstanceOf(StockInvariantViolationException.class);
        assertThatThrownBy(() -> new StockEntity(productId, 0, -1))
            .isInstanceOf(StockInvariantViolationException.class);
    }

    @Test
    void increaseAddsOnHandStockAndRequiresPositiveQuantity() {
        StockEntity stock = new StockEntity(productId, 10, 5);
        stock.increase(15);
        assertThat(stock.getOnHandQuantity()).isEqualTo(25);
        assertThatThrownBy(() -> stock.increase(0))
            .isInstanceOf(StockInvariantViolationException.class);
        assertThatThrownBy(() -> stock.increase(-3))
            .isInstanceOf(StockInvariantViolationException.class);
    }

    @Test
    void decreaseRejectsMoreThanOnHandAndNegativeOnHandIsImpossible() {
        StockEntity stock = new StockEntity(productId, 10, 5);
        stock.decrease(10);
        assertThat(stock.getOnHandQuantity()).isZero();
        assertThatThrownBy(() -> stock.decrease(1))
            .isInstanceOf(InsufficientStockException.class);
        assertThatThrownBy(() -> stock.decrease(0))
            .isInstanceOf(StockInvariantViolationException.class);
    }

    @Test
    void reserveConsumesAvailableStockOnly() {
        StockEntity stock = new StockEntity(productId, 10, 5);
        stock.reserve(4);
        assertThat(stock.getReservedQuantity()).isEqualTo(4);
        assertThat(stock.availableQuantity()).isEqualTo(6);
        assertThatThrownBy(() -> stock.reserve(7))
            .isInstanceOf(InsufficientStockException.class);
        assertThatThrownBy(() -> stock.reserve(0))
            .isInstanceOf(StockInvariantViolationException.class);
    }

    @Test
    void releaseOnlyReleasesWhatIsReserved() {
        StockEntity stock = new StockEntity(productId, 10, 5);
        stock.reserve(6);
        stock.release(6);
        assertThat(stock.getReservedQuantity()).isZero();
        assertThatThrownBy(() -> stock.release(1))
            .isInstanceOf(StockInvariantViolationException.class);
        assertThatThrownBy(() -> stock.release(0))
            .isInstanceOf(StockInvariantViolationException.class);
    }

    @Test
    void adjustToTargetAppliesSignedDeltaAndGuardsInvariants() {
        StockEntity stock = new StockEntity(productId, 10, 5);
        assertThat(stock.adjustToTarget(7)).isEqualTo(-3);
        assertThat(stock.getOnHandQuantity()).isEqualTo(7);
        assertThat(stock.adjustToTarget(12)).isEqualTo(5);
        assertThat(stock.getOnHandQuantity()).isEqualTo(12);
        assertThatThrownBy(() -> stock.adjustToTarget(-1))
            .isInstanceOf(StockInvariantViolationException.class);
        stock.reserve(4);
        assertThatThrownBy(() -> stock.adjustToTarget(3))
            .isInstanceOf(StockInvariantViolationException.class);
    }

    @Test
    void thresholdUpdateAndStatusDerivation() {
        StockEntity stock = new StockEntity(productId, 3, 5);
        assertThat(stock.lowStock()).isTrue();
        assertThat(stock.outOfStock()).isFalse();
        stock.updateLowStockThreshold(0);
        assertThat(stock.getLowStockThreshold()).isZero();
        assertThat(stock.lowStock()).isFalse();
        assertThatThrownBy(() -> stock.updateLowStockThreshold(-1))
            .isInstanceOf(StockInvariantViolationException.class);
        stock.reserve(3);
        assertThat(stock.outOfStock()).isTrue();
    }
}

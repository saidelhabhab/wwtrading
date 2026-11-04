package ma.wwtrading.products.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import ma.wwtrading.products.domain.StockMovementType;
import ma.wwtrading.products.domain.StockStatus;

public final class StockDtos {

    private StockDtos() {
    }

    public record StockResponse(
            UUID productId,
            String productReference,
            String productName,
            int onHandQuantity,
            int reservedQuantity,
            int availableQuantity,
            StockStatus status,
            int lowStockThreshold,
            Instant updatedAt
            ) {

        /**
         * Compact variant used by list endpoints (product name omitted;
         * clients join/display it from catalogue data or the detail call).
         */
        public static StockResponse summary(StockResponse full) {
            return full;
        }
    }

    public record StockMovementResponse(
            UUID id,
            UUID productId,
            StockMovementType type,
            int quantity,
            Integer onHandDelta,
            String reference,
            String reason,
            String actor,
            Instant createdAt
            ) {

    }

    /**
     * IN / OUT request. Quantity is strictly positive.
     */
    public record StockOperationRequest(
            @NotNull
            @Positive int quantity,
            @Size(max = 150) String reference,
            @Size(max = 255) String reason,
            String idempotencyKey
            ) {

    }

    /**
     * ADJUST request with explicit target semantics: the on-hand quantity is
     * corrected to {@code targetQuantity} after a physical count. The applied
     * delta is derived and audited as a single ADJUST movement.
     */
    public record StockAdjustRequest(
            @NotNull
            @PositiveOrZero int targetQuantity,
            @Size(max = 150) String reference,
            @Size(max = 255) String reason
            ) {

    }

    public record StockThresholdRequest(
            @NotNull
            @PositiveOrZero int lowStockThreshold
            ) {

    }

    /**
     * Order-driven stock event line.
     */
    public record OrderStockLine(
            @NotBlank String productReference,
            @NotNull
            @Positive int quantity
            ) {

    }

    /**
     * Order-driven stock event (reserve / release / deduct). Every event is
     * idempotent per {@code orderId}: retries produce no duplicate movements or
     * balance changes.
     */
    public record OrderStockRequest(
            @NotEmpty List<@Valid OrderStockLine> items
            ) {

    }
}

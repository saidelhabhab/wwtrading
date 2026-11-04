package ma.wwtrading.products.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.wwtrading.products.api.StockDtos.OrderStockRequest;
import ma.wwtrading.products.api.StockDtos.StockAdjustRequest;
import ma.wwtrading.products.api.StockDtos.StockMovementResponse;
import ma.wwtrading.products.api.StockDtos.StockOperationRequest;
import ma.wwtrading.products.api.StockDtos.StockResponse;
import ma.wwtrading.products.api.StockDtos.StockThresholdRequest;
import ma.wwtrading.products.domain.ProductEntity;
import ma.wwtrading.products.domain.ProductRepository;
import ma.wwtrading.products.domain.StockEntity;
import ma.wwtrading.products.domain.StockMovementEntity;
import ma.wwtrading.products.domain.StockMovementRepository;
import ma.wwtrading.products.domain.StockMovementType;
import ma.wwtrading.products.domain.StockRepository;
import ma.wwtrading.products.domain.StockStatus;

/**
 * Application service owning every stock balance change.
 *
 * <p>
 * Every mutating operation loads the balance with a pessimistic write lock,
 * applies the change on the {@link StockEntity} invariant guard, and only then
 * appends an audit {@link StockMovementEntity}. If any step throws, the whole
 * transaction rolls back: no movement is ever recorded for a failed change.</p>
 */
@Service
public class StockApplicationService {

    private final StockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;

    public StockApplicationService(
            StockRepository stockRepository,
            StockMovementRepository movementRepository,
            ProductRepository productRepository
    ) {
        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public StockResponse getStock(UUID productId) {
        ProductEntity product = findProduct(productId);
        StockEntity stock = stockRepository.findByProductId(productId)
                .orElseGet(() -> new StockEntity(productId, 0, StockEntity.DEFAULT_LOW_STOCK_THRESHOLD));
        return toResponse(product, stock);
    }

    @Transactional(readOnly = true)
    public Page<StockResponse> listStock(Pageable pageable) {
        Page<ProductEntity> products = productRepository.findAll(pageable);
        return products.map(product -> {
            StockEntity stock = stockRepository.findByProductId(product.getId())
                    .orElseGet(() -> new StockEntity(product.getId(), 0, StockEntity.DEFAULT_LOW_STOCK_THRESHOLD));
            return toResponse(product, stock);
        });
    }

    @Transactional
    public StockResponse stockIn(UUID productId, StockOperationRequest request, String actor) {
        ProductEntity product = findProduct(productId);
        StockEntity stock = lockStock(productId);
        stock.increase(request.quantity());
        recordMovement(stock, productId, StockMovementType.IN, request.quantity(), request.quantity(),
                request.reference(), request.reason(), actor, null);
        return toResponse(product, stock);
    }

    @Transactional
    public StockResponse stockOut(UUID productId, StockOperationRequest request, String actor) {
        ProductEntity product = findProduct(productId);
        StockEntity stock = lockStock(productId);
        stock.decrease(request.quantity());
        recordMovement(stock, productId, StockMovementType.OUT, request.quantity(), -request.quantity(),
                request.reference(), request.reason(), actor, null);
        return toResponse(product, stock);
    }

    @Transactional
    public StockResponse adjust(UUID productId, StockAdjustRequest request, String actor) {
        ProductEntity product = findProduct(productId);
        StockEntity stock = lockStock(productId);
        int delta = stock.adjustToTarget(request.targetQuantity());
        recordMovement(stock, productId, StockMovementType.ADJUST, Math.abs(delta), delta,
                request.reference(), request.reason(), actor, null);
        return toResponse(product, stock);
    }

    @Transactional
    public StockResponse reserve(UUID productId, StockOperationRequest request, String actor) {
        ProductEntity product = findProduct(productId);
        StockEntity stock = lockStock(productId);
        stock.reserve(request.quantity());
        recordMovement(stock, productId, StockMovementType.RESERVE, request.quantity(), null,
                request.reference(), request.reason(), actor, null);
        return toResponse(product, stock);
    }

    @Transactional
    public StockResponse release(UUID productId, StockOperationRequest request, String actor) {
        ProductEntity product = findProduct(productId);
        StockEntity stock = lockStock(productId);
        stock.release(request.quantity());
        recordMovement(stock, productId, StockMovementType.RELEASE, request.quantity(), null,
                request.reference(), request.reason(), actor, null);
        return toResponse(product, stock);
    }

    @Transactional
    public void updateLowStockThreshold(UUID productId, StockThresholdRequest request) {
        StockEntity stock = lockStock(productId);
        stock.updateLowStockThreshold(request.lowStockThreshold());
        stockRepository.save(stock);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> getMovements(UUID productId, Pageable pageable) {
        findProduct(productId);
        return movementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable)
                .map(this::toMovementResponse);
    }

    /**
     * Order-driven idempotent stock event. Idempotency is enforced PER LINE:
     * the base {@code idempotencyKey} (ORDER:{orderId}:{TYPE}) is suffixed with
     * the product reference, so multi-line orders produce distinct deterministic
     * keys (ORDER:42:RESERVE:C554, ORDER:42:RESERVE:C258). A retry of an
     * already-applied line short-circuits without any balance change.
     */
    @Transactional
    public void applyOrderStockEvent(String idempotencyKey, OrderStockRequest request, String actor) {
        String[] parts = idempotencyKey.split(":");
        if (parts.length < 3) {
            throw new StockValidationException(
                    "Order stock idempotency key must follow ORDER:{orderId}:{TYPE}, got " + idempotencyKey);
        }
        StockMovementType type;
        try {
            type = StockMovementType.valueOf(parts[parts.length - 1]);
        } catch (IllegalArgumentException exception) {
            throw new StockValidationException("Unknown order stock event type in " + idempotencyKey);
        }
        if (type == StockMovementType.ADJUST) {
            throw new StockValidationException("ADJUST is not a valid order-driven stock event");
        }
        for (var line : request.items()) {
            String lineKey = idempotencyKey + ":" + line.productReference();
            if (movementRepository.existsByIdempotencyKey(lineKey)) {
                continue;
            }
            ProductEntity product = productRepository.findByProductReference(line.productReference())
                    .orElseThrow(() -> new ProductNotFoundException(line.productReference()));
            StockEntity stock = lockStock(product.getId());
            Integer onHandDelta = null;
            int quantity = line.quantity();
            switch (type) {
                case RESERVE ->
                    stock.reserve(quantity);
                case RELEASE ->
                    stock.release(quantity);
                case OUT -> {
                    stock.decrease(quantity);
                    onHandDelta = -quantity;
                }
                case IN -> {
                    stock.increase(quantity);
                    onHandDelta = quantity;
                }
                case ADJUST ->
                    throw new StockValidationException(
                            "ADJUST is not a valid order-driven stock event");
            }
            recordMovement(stock, product.getId(), type, quantity, onHandDelta,
                    lineKey, null, actor, lineKey);
        }
    }

    /**
     * Atomic fulfilment of a previously reserved quantity: reserved -= q,
     * onHand -= q in one step, recorded as a single OUT movement. Used by the
     * order completion (DEDUCT) flow so no intermediate release state exists.
     */
    @Transactional
    public void fulfillReserved(UUID productId, int quantity, String reference, String idempotencyKey, String actor) {
        if (idempotencyKey != null && movementRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }
        StockEntity stock = lockStock(productId);
        stock.fulfillReserved(quantity);
        recordMovement(stock, productId, StockMovementType.OUT, quantity, -quantity,
                reference, null, actor, idempotencyKey);
    }

    private StockEntity lockStock(UUID productId) {
        return stockRepository.findForUpdateByProductId(productId)
                .orElseGet(() -> {
                    StockEntity created = new StockEntity(productId, 0, StockEntity.DEFAULT_LOW_STOCK_THRESHOLD);
                    return stockRepository.saveAndFlush(created);
                });
    }

    private void recordMovement(
            StockEntity stock,
            UUID productId,
            StockMovementType type,
            int quantity,
            Integer onHandDelta,
            String reference,
            String reason,
            String actor,
            String idempotencyKey
    ) {
        StockMovementEntity movement = new StockMovementEntity(
                stock.getId(), productId, type, quantity, onHandDelta,
                reference, reason, actor, idempotencyKey
        );
        stockRepository.save(stock);
        movementRepository.save(movement);
    }

    private ProductEntity findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private StockResponse toResponse(ProductEntity product, StockEntity stock) {
        return new StockResponse(
                product.getId(),
                product.getProductReference(),
                product.getName(),
                stock.getOnHandQuantity(),
                stock.getReservedQuantity(),
                stock.availableQuantity(),
                stock.lowStock() ? StockStatus.LOW_STOCK
                : stock.outOfStock() ? StockStatus.OUT_OF_STOCK : StockStatus.IN_STOCK,
                stock.getLowStockThreshold(),
                stock.getUpdatedAt() == null ? Instant.now() : stock.getUpdatedAt()
        );
    }

    private StockMovementResponse toMovementResponse(StockMovementEntity movement) {
        return new StockMovementResponse(
                movement.getId(),
                movement.getProductId(),
                movement.getType(),
                movement.getQuantity(),
                movement.getOnHandDelta(),
                movement.getReference(),
                movement.getReason(),
                movement.getActor(),
                movement.getCreatedAt()
        );
    }
}

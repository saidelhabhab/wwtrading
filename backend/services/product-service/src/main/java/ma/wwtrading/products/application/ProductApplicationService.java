package ma.wwtrading.products.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.products.api.CreateProductRequest;
import ma.wwtrading.products.api.ProductResponse;
import ma.wwtrading.products.api.UpdateProductRequest;
import ma.wwtrading.products.domain.MachineCategory;
import ma.wwtrading.products.domain.ProductColorMode;
import ma.wwtrading.products.domain.ProductEntity;
import ma.wwtrading.products.domain.ProductRepository;
import ma.wwtrading.products.domain.ProductType;
import ma.wwtrading.products.domain.StockEntity;
import ma.wwtrading.products.domain.StockRepository;
import ma.wwtrading.products.infrastructure.kafka.ProductCreatedDomainEvent;

@Service
public class ProductApplicationService {

    private final ProductRepository repository;
    private final ProductMapper mapper;
    private final StockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ProductApplicationService(
            ProductRepository repository,
            ProductMapper mapper,
            StockRepository stockRepository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.stockRepository = stockRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String reference = request.reference().trim();
        rejectReferenceConflict(reference, null);
        ProductEntity product = mapper.toEntity(request);
        try {
            ProductEntity saved = repository.saveAndFlush(product);
            ensureStockRow(saved);
            publishProductCreatedEvent(saved);
            return withStockAvailability(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ProductReferenceConflictException(reference);
        }
    }

    private void publishProductCreatedEvent(ProductEntity saved) {
        if (eventPublisher == null) {
            return;
        }
        DomainEvent<EventPayloads.ProductCreated> domainEvent = new DomainEvent<>(
                UUID.randomUUID(),
                EventTypes.PRODUCT_CREATED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "product-service",
                "PRODUCT",
                saved.getId().toString(),
                null,
                new EventPayloads.ProductCreated(
                        saved.getId(),
                        saved.getProductReference(),
                        saved.getName(),
                        saved.getBrand(),
                        saved.getModel(),
                        saved.getType() != null ? saved.getType().name() : "OTHER"
                )
        );
        eventPublisher.publishEvent(new ProductCreatedDomainEvent(domainEvent));
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        return repository.findById(id)
                .map(this::withStockAvailability)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public ProductResponse getByReference(String reference) {
        return repository.findByProductReference(reference)
                .map(this::withStockAvailability)
                .orElseThrow(() -> new ProductNotFoundException(reference));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Pageable pageable) {
        return list(null, null, null, null, pageable);
    }

    /**
     * Lists products filtered in the database. Contradictory filter
     * combinations are rejected so a filter is never silently ignored.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(
            ProductType type,
            MachineCategory machineCategory,
            ProductColorMode colorMode,
            Pageable pageable
    ) {
        requireConsistentFilters(type, machineCategory, colorMode);
        return repository.findAllFiltered(type, machineCategory, colorMode, pageable)
                .map(this::withStockAvailability);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(
            String search,
            ProductType type,
            MachineCategory machineCategory,
            ProductColorMode colorMode,
            Pageable pageable
    ) {
        if (search == null || search.isBlank()) {
            return list(type, machineCategory, colorMode, pageable);
        }
        requireConsistentFilters(type, machineCategory, colorMode);
        return repository.findAllFiltered(search.trim(), type, machineCategory, colorMode, pageable)
                .map(this::withStockAvailability);
    }

    /**
     * Machine category and colour mode only exist for printers, mirroring the
     * domain and database constraints, so a filter that could never match is
     * rejected with 400 rather than returning a misleading empty page.
     */
    private void requireConsistentFilters(
            ProductType type,
            MachineCategory machineCategory,
            ProductColorMode colorMode
    ) {
        if (type != null && type != ProductType.PRINTER) {
            if (machineCategory != null) {
                throw new IllegalArgumentException("Machine category filter is only supported for printer products");
            }
            if (colorMode != null) {
                throw new IllegalArgumentException("Color mode filter is only supported for printer products");
            }
        }
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        ProductEntity product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        String reference = request.reference().trim();
        rejectReferenceConflict(reference, id);
        product.updateDetails(
                reference, request.name(), request.description(), request.price(), request.type(),
                request.brand(), request.model(), request.machineCategory(), request.colorMode(),
                request.warrantyMonths(), request.warrantyCopies()
        );
        try {
            return withStockAvailability(repository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new ProductReferenceConflictException(reference);
        }
    }

    @Transactional
    public ProductResponse activate(UUID id) {
        ProductEntity product = findProduct(id);
        product.activate();
        return withStockAvailability(repository.saveAndFlush(product));
    }

    @Transactional
    public ProductResponse deactivate(UUID id) {
        ProductEntity product = findProduct(id);
        product.deactivate();
        return withStockAvailability(repository.saveAndFlush(product));
    }

    private ProductEntity findProduct(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private void rejectReferenceConflict(String reference, UUID currentProductId) {
        repository.findByProductReference(reference)
                .filter(existing -> currentProductId == null || !existing.getId().equals(currentProductId))
                .ifPresent(existing -> {
                    throw new ProductReferenceConflictException(reference);
                });
    }

    private void ensureStockRow(ProductEntity product) {
        if (!stockRepository.existsByProductId(product.getId())) {
            stockRepository.save(new StockEntity(product.getId(), 0, StockEntity.DEFAULT_LOW_STOCK_THRESHOLD));
        }
    }

    private ProductResponse withStockAvailability(ProductEntity product) {
        ProductResponse response = mapper.toResponse(product);
        // StockEntity is the only source of truth. A missing row is an empty balance;
        // the legacy ProductEntity quantity is deliberately not used as a fallback.
        Integer available = stockRepository.findByProductId(product.getId())
                .map(StockEntity::availableQuantity)
                .orElse(0);
        if (available == null) {
            return response;
        }
        return new ProductResponse(
                response.id(), response.reference(), response.name(), response.description(),
                response.price(), response.type(), response.brand(), response.model(),
                response.machineCategory(), response.colorMode(), response.warrantyMonths(), response.warrantyCopies(),
                response.active(), response.createdAt(), response.updatedAt(), response.images(), available
        );
    }
}

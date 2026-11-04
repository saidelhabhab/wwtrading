package ma.wwtrading.products.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "product",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_reference",
                columnNames = "product_reference"
        )
)
public class ProductEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "product_reference", nullable = false, length = 64)
    private String productReference;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 32)
    private ProductType type = ProductType.PRINTER;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String brand;

    @Column(length = 150)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "machine_category", length = 50)
    private MachineCategory machineCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "color_mode", length = 16)
    private ProductColorMode colorMode;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "warranty_copies")
    private Integer warrantyCopies;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    /** Legacy column retained read-only for migration compatibility. StockEntity is authoritative. */
    @Deprecated
    @Column(name = "available_quantity", insertable = false, updatable = false)
    private Integer legacyAvailableQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ProductImageEntity> images = new ArrayList<>();

    protected ProductEntity() {
    }

    public ProductEntity(String productReference, String name, String description, BigDecimal price) {
        this(productReference, name, description, price, ProductType.PRINTER);
    }

    public ProductEntity(String productReference, String name, String description, BigDecimal price, ProductType type) {
        this(productReference, name, description, price, type, null, null, null, null, null);
    }

    public ProductEntity(
        String productReference,
        String name,
        String description,
        BigDecimal price,
        ProductType type,
        String brand,
        String model,
        MachineCategory machineCategory,
        Integer warrantyMonths,
        Integer warrantyCopies
    ) {
        this(productReference, name, description, price, type, brand, model,
            machineCategory, null, warrantyMonths, warrantyCopies);
    }

    public ProductEntity(
        String productReference,
        String name,
        String description,
        BigDecimal price,
        ProductType type,
        String brand,
        String model,
        MachineCategory machineCategory,
        ProductColorMode colorMode,
        Integer warrantyMonths,
        Integer warrantyCopies
    ) {
        updateDetails(productReference, name, description, price, type, brand, model,
            machineCategory, colorMode, warrantyMonths, warrantyCopies);
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void updateDetails(String productReference, String name, String description, BigDecimal price) {
        updateDetails(productReference, name, description, price, type, brand, model,
            machineCategory, colorMode, warrantyMonths, warrantyCopies);
    }

    public void updateDetails(String productReference, String name, String description, BigDecimal price, ProductType type) {
        updateDetails(productReference, name, description, price, type, brand, model,
            machineCategory, colorMode, warrantyMonths, warrantyCopies);
    }

    public void updateDetails(
        String productReference,
        String name,
        String description,
        BigDecimal price,
        ProductType type,
        String brand,
        String model,
        MachineCategory machineCategory,
        Integer warrantyMonths,
        Integer warrantyCopies
    ) {
        updateDetails(productReference, name, description, price, type, brand, model,
            machineCategory, colorMode, warrantyMonths, warrantyCopies);
    }

    public void updateDetails(
        String productReference,
        String name,
        String description,
        BigDecimal price,
        ProductType type,
        String brand,
        String model,
        MachineCategory machineCategory,
        ProductColorMode colorMode,
        Integer warrantyMonths,
        Integer warrantyCopies
    ) {
        String normalizedReference = normalizeRequired(productReference, "Product reference");
        String normalizedName = normalizeRequired(name, "Product name");
        BigDecimal validatedPrice = requireNonNegative(price);
        ProductType validatedType = requireType(type);
        MachineCategory validatedCategory = requireCompatibleCategory(validatedType, machineCategory);
        ProductColorMode validatedColorMode = requireCompatibleColorMode(validatedType, colorMode);
        Integer validatedWarrantyMonths = requireNonNegative(warrantyMonths, "Warranty months");
        Integer validatedWarrantyCopies = requireNonNegative(warrantyCopies, "Warranty copies");

        this.productReference = normalizedReference;
        this.name = normalizedName;
        this.description = description;
        this.price = validatedPrice;
        this.type = validatedType;
        this.brand = normalizeOptional(brand);
        this.model = normalizeOptional(model);
        this.machineCategory = validatedCategory;
        this.colorMode = validatedColorMode;
        this.warrantyMonths = validatedWarrantyMonths;
        this.warrantyCopies = validatedWarrantyCopies;
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Product price must be non-negative");
        }
        return value;
    }

    private static ProductType requireType(ProductType value) {
        if (value == null) {
            throw new IllegalArgumentException("Product type must not be null");
        }
        return value;
    }

    private static MachineCategory requireCompatibleCategory(ProductType type, MachineCategory category) {
        if (category != null && type != ProductType.PRINTER) {
            throw new IllegalArgumentException("Machine category is only supported for printer products");
        }
        return category;
    }

    private static ProductColorMode requireCompatibleColorMode(ProductType type, ProductColorMode colorMode) {
        if (colorMode != null && type != ProductType.PRINTER) {
            throw new IllegalArgumentException("Color mode is only supported for printer products");
        }
        return colorMode;
    }

    private static Integer requireNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID getId() { return id; }
    public String getProductReference() { return productReference; }
    public String getName() { return name; }
    public ProductType getType() { return type; }
    public String getDescription() { return description; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public MachineCategory getMachineCategory() { return machineCategory; }
    public ProductColorMode getColorMode() { return colorMode; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public Integer getWarrantyCopies() { return warrantyCopies; }
    /** @deprecated Read current availability from StockEntity instead. */
    @Deprecated
    public Integer getLegacyAvailableQuantity() { return legacyAvailableQuantity; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ProductImageEntity> getImages() { return images; }
}

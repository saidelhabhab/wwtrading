package ma.wwtrading.quotations.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quotation_line")
public class QuotationLineEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(optional = false)
    @JoinColumn(name = "quotation_id", nullable = false)
    private QuotationEntity quotation;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_reference", nullable = false, length = 64)
    private String productReference;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Column(name = "catalog_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal catalogUnitPrice;

    @Column(name = "proposed_quantity")
    private Integer proposedQuantity;

    @Column(name = "proposed_unit_price", precision = 19, scale = 2)
    private BigDecimal proposedUnitPrice;

    protected QuotationLineEntity() { }

    public QuotationLineEntity(UUID productId, String productReference, String productName,
            BigDecimal catalogUnitPrice, int requestedQuantity) {
        if (productId == null || productReference == null || productReference.isBlank()
                || productName == null || productName.isBlank() || requestedQuantity < 1
                || catalogUnitPrice == null || catalogUnitPrice.signum() < 0) {
            throw new IllegalArgumentException("Invalid quotation product line");
        }
        this.productId = productId;
        this.productReference = productReference;
        this.productName = productName;
        this.catalogUnitPrice = catalogUnitPrice;
        this.requestedQuantity = requestedQuantity;
    }

    public void propose(int quantity, BigDecimal unitPrice) {
        if (quantity < 1 || unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Invalid proposed product line");
        }
        proposedQuantity = quantity;
        proposedUnitPrice = unitPrice;
    }

    void attachTo(QuotationEntity quotation) { this.quotation = quotation; }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getProductReference() { return productReference; }
    public String getProductName() { return productName; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public BigDecimal getCatalogUnitPrice() { return catalogUnitPrice; }
    public Integer getProposedQuantity() { return proposedQuantity; }
    public BigDecimal getProposedUnitPrice() { return proposedUnitPrice; }
    public BigDecimal getProposedLineTotal() {
        return proposedQuantity == null || proposedUnitPrice == null ? null
            : proposedUnitPrice.multiply(BigDecimal.valueOf(proposedQuantity));
    }
}

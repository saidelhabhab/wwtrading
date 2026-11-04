package ma.wwtrading.quotations.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.wwtrading.quotations.domain.QuotationStatus;

public record UpdateQuotationStatusRequest(
    @NotNull QuotationStatus status,
    @DecimalMin(value = "0.01", inclusive = true) BigDecimal proposedAmount,
    @Size(max = 4000) String commercialNotes,
    @Valid @Size(max = 20) List<ProposedLine> proposedLines,
    @DecimalMin("0.00") BigDecimal discount,
    @DecimalMin("0.00") BigDecimal deliveryFee,
    LocalDate validUntil
) {
    public record ProposedLine(@NotNull UUID productId, @Min(1) int quantity,
            @NotNull @DecimalMin("0.00") BigDecimal proposedUnitPrice) { }

    public UpdateQuotationStatusRequest(QuotationStatus status, BigDecimal proposedAmount, String commercialNotes) {
        this(status, proposedAmount, commercialNotes, null, null, null, null);
    }
}

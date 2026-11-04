package ma.wwtrading.quotations.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.quotations.domain.QuotationStatus;

public record QuotationResponse(
    UUID id,
    String customerReference,
    String serviceType,
    String requestedEquipment,
    String brandModel,
    Integer quantity,
    String description,
    String fullName,
    String company,
    String phone,
    String email,
    String city,
    String desiredTimeline,
    BigDecimal estimatedBudget,
    String additionalMessage,
    QuotationStatus status,
    BigDecimal proposedAmount,
    String commercialNotes,
    Instant createdAt,
    Instant updatedAt,
    List<QuotationLineResponse> lines,
    BigDecimal subtotal,
    BigDecimal discount,
    BigDecimal deliveryFee,
    BigDecimal finalTotal,
    LocalDate validUntil
) {
    public record QuotationLineResponse(UUID productId, String productReference, String productName,
            BigDecimal catalogUnitPrice, int requestedQuantity, Integer proposedQuantity, BigDecimal proposedUnitPrice,
            BigDecimal proposedLineTotal) { }

    public QuotationResponse(UUID id, String customerReference, String serviceType, String requestedEquipment,
            String brandModel, Integer quantity, String description, String fullName, String company,
            String phone, String email, String city, String desiredTimeline, BigDecimal estimatedBudget,
            String additionalMessage, QuotationStatus status, BigDecimal proposedAmount,
            String commercialNotes, Instant createdAt, Instant updatedAt) {
        this(id, customerReference, serviceType, requestedEquipment, brandModel, quantity, description,
            fullName, company, phone, email, city, desiredTimeline, estimatedBudget, additionalMessage,
            status, proposedAmount, commercialNotes, createdAt, updatedAt, List.of(), null, null, null,
            proposedAmount, null);
    }
}

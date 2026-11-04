package ma.wwtrading.quotations.application;

import ma.wwtrading.quotations.api.CreateQuotationRequest;
import ma.wwtrading.quotations.api.QuotationResponse;
import ma.wwtrading.quotations.domain.QuotationEntity;
import ma.wwtrading.quotations.domain.QuotationStatus;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface QuotationMapper {

    default QuotationResponse toResponse(QuotationEntity quotation) {
        var lines = quotation.getLines().stream().map(line -> new QuotationResponse.QuotationLineResponse(
            line.getProductId(), line.getProductReference(), line.getProductName(),
            line.getCatalogUnitPrice(), line.getRequestedQuantity(), line.getProposedQuantity(), line.getProposedUnitPrice(),
            line.getProposedLineTotal()
        )).toList();
        return new QuotationResponse(quotation.getId(), quotation.getCustomerReference(),
            quotation.getServiceType(), quotation.getRequestedEquipment(), quotation.getBrandModel(),
            quotation.getQuantity(), quotation.getDescription(), quotation.getFullName(),
            quotation.getCompany(), quotation.getPhone(), quotation.getEmail(), quotation.getCity(),
            quotation.getDesiredTimeline(), quotation.getEstimatedBudget(), quotation.getAdditionalMessage(),
            quotation.getStatus(), quotation.getProposedAmount(), quotation.getCommercialNotes(),
            quotation.getCreatedAt(), quotation.getUpdatedAt(), lines,
            lines.isEmpty() ? null : quotation.getSubtotal(), quotation.getDiscount(),
            quotation.getDeliveryFee(), quotation.getProposedAmount(), quotation.getValidUntil());
    }

    default QuotationEntity toEntity(String customerReference, CreateQuotationRequest request) {
        return new QuotationEntity(
            customerReference,
            QuotationStatus.PENDING,
            request.serviceType(),
            request.requestedEquipment(),
            request.brandModel(),
            request.quantity(),
            request.description(),
            request.fullName(),
            request.company(),
            request.phone(),
            request.email(),
            request.city(),
            request.desiredTimeline(),
            request.estimatedBudget(),
            request.additionalMessage()
        );
    }
}

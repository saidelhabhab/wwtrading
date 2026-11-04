package ma.wwtrading.quotations.api;

import jakarta.validation.constraints.NotNull;
import ma.wwtrading.quotations.domain.QuotationStatus;

public record QuotationDecisionRequest(@NotNull QuotationStatus status) { }

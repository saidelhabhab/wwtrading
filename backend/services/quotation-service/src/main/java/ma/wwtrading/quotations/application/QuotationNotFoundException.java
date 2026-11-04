package ma.wwtrading.quotations.application;

import java.util.UUID;

public class QuotationNotFoundException extends RuntimeException {

    public QuotationNotFoundException(UUID id) {
        super("Quotation not found: " + id);
    }
}

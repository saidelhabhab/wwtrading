package ma.wwtrading.orders.api;

import java.time.Instant;
import java.util.UUID;

public record PaymentBankResponse(
    UUID id,
    String reference,
    String name,
    String rib,
    String beneficiary,
    String logoUrl,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public PaymentBankResponse(String reference, String name, String rib, String beneficiary, String logoUrl) {
        this(null, reference, name, rib, beneficiary, logoUrl, true, null, null);
    }
}
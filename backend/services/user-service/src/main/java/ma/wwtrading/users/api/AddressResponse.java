package ma.wwtrading.users.api;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
    UUID id,
    String firstName,
    String lastName,
    String company,
    String phone,
    String addressLine1,
    String addressLine2,
    String city,
    String postalCode,
    String country,
    boolean defaultAddress,
    Instant createdAt,
    Instant updatedAt
) {
}

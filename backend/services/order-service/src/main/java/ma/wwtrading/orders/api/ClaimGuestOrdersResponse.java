package ma.wwtrading.orders.api;

import java.util.List;
import java.util.UUID;

public record ClaimGuestOrdersResponse(
    int linkedCount,
    List<UUID> linkedOrderIds,
    List<String> linkedOrderNumbers
) {
}


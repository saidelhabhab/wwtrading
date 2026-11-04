package ma.wwtrading.users.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String locale,
    boolean enabled,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt,
    String phone
) {
}

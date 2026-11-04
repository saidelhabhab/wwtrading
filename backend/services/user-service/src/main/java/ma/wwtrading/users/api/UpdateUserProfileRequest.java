package ma.wwtrading.users.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Pattern(regexp = "^[a-zA-Z]{2,3}([_-][a-zA-Z]{2})?$", message = "must be a valid locale") String locale,
    @Size(max = 32)
    @Pattern(regexp = "^$|^\\+?[0-9\\s.\\-()]{6,32}$", message = "must be a valid phone number") String phone
) {
}

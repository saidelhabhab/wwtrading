package ma.wwtrading.users.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Size(max = 200) String company,
    @Size(max = 32) String phone,
    @NotBlank @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2,
    @NotBlank @Size(max = 120) String city,
    @Size(max = 32) String postalCode,
    @NotBlank @Size(max = 100) String country
) {
}

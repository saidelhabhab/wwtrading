package ma.wwtrading.users.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffUserRequest(
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 12, max = 128)
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
        message = "must contain upper-case, lower-case, digit and special characters"
    ) String password,
    @NotNull StaffRole role,
    @Size(max = 32)
    @Pattern(
        regexp = "^$|^\\+?[0-9\\s.\\-()]{6,32}$",
        message = "must be a valid phone number"
    ) String phone
) {
    public enum StaffRole {
        ADMIN,
        MANAGER,
        COMMERCIAL,
        TECHNICIAN,
        CLIENT
    }
}

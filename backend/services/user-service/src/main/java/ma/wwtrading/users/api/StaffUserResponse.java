package ma.wwtrading.users.api;

public record StaffUserResponse(
    String id,
    String firstName,
    String lastName,
    String email,
    CreateStaffUserRequest.StaffRole role,
    boolean enabled,
    String phone
) {
}

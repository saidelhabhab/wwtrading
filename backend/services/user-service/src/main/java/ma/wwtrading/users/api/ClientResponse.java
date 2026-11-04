package ma.wwtrading.users.api;

public record ClientResponse(
    String id,
    String firstName,
    String lastName,
    String email,
    String phone,
    boolean enabled
) {
}


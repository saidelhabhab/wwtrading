package ma.wwtrading.users.api;

public record AdminResetPasswordResponse(
    boolean success,
    String message
) {
}


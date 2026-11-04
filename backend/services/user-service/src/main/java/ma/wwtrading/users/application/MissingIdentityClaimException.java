package ma.wwtrading.users.application;

public class MissingIdentityClaimException extends RuntimeException {
    public MissingIdentityClaimException(String message) {
        super(message);
    }
}

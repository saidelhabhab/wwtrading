package ma.wwtrading.users.application;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("The authenticated user profile has not been synchronized");
    }
}

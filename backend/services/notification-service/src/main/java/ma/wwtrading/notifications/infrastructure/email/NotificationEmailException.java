package ma.wwtrading.notifications.infrastructure.email;

public class NotificationEmailException extends RuntimeException {
    public NotificationEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}

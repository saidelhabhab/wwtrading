package ma.wwtrading.notifications.domain;

public enum EmailDeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    RETRYING,
    DEAD_LETTER
}

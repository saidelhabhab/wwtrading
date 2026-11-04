package ma.wwtrading.notifications.domain;

public enum WhatsAppDeliveryStatus {
    DISABLED,
    PENDING,
    SENT,
    FAILED,
    RETRYING,
    DEAD_LETTER
}


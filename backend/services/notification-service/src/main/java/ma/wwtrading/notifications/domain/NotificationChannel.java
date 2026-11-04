package ma.wwtrading.notifications.domain;

public enum NotificationChannel {
    IN_APP,
    EMAIL,
    WHATSAPP,
    BOTH,
    ALL;

    public boolean includesEmail() {
        return this == EMAIL || this == BOTH || this == ALL;
    }

    public boolean includesWhatsApp() {
        return this == WHATSAPP || this == ALL;
    }
}

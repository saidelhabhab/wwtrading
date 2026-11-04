package ma.wwtrading.notifications.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_email_delivery")
public class NotificationEmailDeliveryEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false, unique = true)
    private NotificationEntity notification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailDeliveryStatus status = EmailDeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationEmailDeliveryEntity() {}

    public NotificationEmailDeliveryEntity(NotificationEntity notification, Instant now) {
        this.notification = java.util.Objects.requireNonNull(notification);
        this.nextAttemptAt = java.util.Objects.requireNonNull(now);
    }

    @PrePersist void beforeInsert() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void beforeUpdate() { updatedAt = Instant.now(); }

    public void beginAttempt(Instant now) {
        status = EmailDeliveryStatus.RETRYING;
        attemptCount++;
        lastAttemptAt = now;
        lastError = null;
    }

    public void markSent(Instant now) {
        status = EmailDeliveryStatus.SENT;
        sentAt = now;
        nextAttemptAt = now;
        lastError = null;
    }

    public void markFailed(Instant now, int maxAttempts, Duration delay, String error) {
        lastError = truncate(error);
        if (attemptCount >= maxAttempts) {
            status = EmailDeliveryStatus.DEAD_LETTER;
            nextAttemptAt = now;
        } else {
            status = EmailDeliveryStatus.FAILED;
            nextAttemptAt = now.plus(delay.multipliedBy(Math.max(1, attemptCount)));
        }
    }

    public boolean resetForRetry(Instant now) {
        if (status == EmailDeliveryStatus.SENT) {
            return false;
        }
        status = EmailDeliveryStatus.PENDING;
        attemptCount = 0;
        nextAttemptAt = now != null ? now : Instant.now();
        return true;
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) return "Unknown email delivery failure";
        String safe = value.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= 1000 ? safe : safe.substring(0, 1000);
    }

    public UUID getId() { return id; }
    public NotificationEntity getNotification() { return notification; }
    public EmailDeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastError() { return lastError; }
}

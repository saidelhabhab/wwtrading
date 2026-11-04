package ma.wwtrading.notifications.infrastructure.email;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import ma.wwtrading.notifications.domain.NotificationEmailDeliveryEntity;
import ma.wwtrading.notifications.domain.NotificationEmailDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "notification.email.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEmailDeliveryProcessor {
    private static final Logger log = LoggerFactory.getLogger(NotificationEmailDeliveryProcessor.class);
    private final NotificationEmailDeliveryRepository repository;
    private final NotificationEmailService emailService;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;
    private final Duration retryDelay;

    @Autowired
    public NotificationEmailDeliveryProcessor(NotificationEmailDeliveryRepository repository,
            NotificationEmailService emailService,
            @Value("${notification.email.max-attempts:5}") int maxAttempts,
            @Value("${notification.email.batch-size:20}") int batchSize,
            @Value("${notification.email.retry-delay:PT30S}") Duration retryDelay) {
        this(repository, emailService, Clock.systemUTC(), maxAttempts, batchSize, retryDelay);
    }

    NotificationEmailDeliveryProcessor(NotificationEmailDeliveryRepository repository,
            NotificationEmailService emailService, Clock clock, int maxAttempts,
            int batchSize, Duration retryDelay) {
        this.repository = repository; this.emailService = emailService; this.clock = clock;
        this.maxAttempts = maxAttempts; this.batchSize = batchSize; this.retryDelay = retryDelay;
    }

    @Scheduled(fixedDelayString = "${notification.email.dispatch-delay-ms:5000}")
    @Transactional
    public void dispatchDueEmails() {
        Instant now = Instant.now(clock);
        for (NotificationEmailDeliveryEntity delivery : repository.lockDueDeliveries(now, batchSize)) {
            delivery.beginAttempt(now);
            try {
                emailService.sendNotificationEmail(delivery.getNotification());
                delivery.markSent(Instant.now(clock));
            } catch (RuntimeException failure) {
                delivery.markFailed(Instant.now(clock), maxAttempts, retryDelay,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
                log.warn("Notification email delivery failed notificationId={} attempt={} status={}",
                    delivery.getNotification().getId(), delivery.getAttemptCount(), delivery.getStatus());
            }
        }
    }
}

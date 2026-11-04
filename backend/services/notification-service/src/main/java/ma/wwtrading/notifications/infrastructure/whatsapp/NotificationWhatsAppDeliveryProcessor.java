package ma.wwtrading.notifications.infrastructure.whatsapp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ma.wwtrading.notifications.domain.NotificationWhatsAppDeliveryEntity;
import ma.wwtrading.notifications.domain.NotificationWhatsAppDeliveryRepository;

@Component
@ConditionalOnProperty(name = "notification.whatsapp.dispatch-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWhatsAppDeliveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(NotificationWhatsAppDeliveryProcessor.class);

    private final NotificationWhatsAppDeliveryRepository repository;
    private final NotificationWhatsAppService whatsAppService;
    private final Clock clock;
    private final int maxAttempts;
    private final int batchSize;
    private final Duration retryDelay;

    @Autowired
    public NotificationWhatsAppDeliveryProcessor(
            NotificationWhatsAppDeliveryRepository repository,
            NotificationWhatsAppService whatsAppService,
            @Value("${notification.whatsapp.max-attempts:3}") int maxAttempts,
            @Value("${notification.whatsapp.batch-size:20}") int batchSize,
            @Value("${notification.whatsapp.retry-delay:PT60S}") Duration retryDelay) {
        this(repository, whatsAppService, Clock.systemUTC(), maxAttempts, batchSize, retryDelay);
    }

    public NotificationWhatsAppDeliveryProcessor(
            NotificationWhatsAppDeliveryRepository repository,
            NotificationWhatsAppService whatsAppService,
            Clock clock,
            int maxAttempts,
            int batchSize,
            Duration retryDelay) {
        this.repository = repository;
        this.whatsAppService = whatsAppService;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.retryDelay = retryDelay;
    }

    @Scheduled(fixedDelayString = "${notification.whatsapp.dispatch-delay-ms:5000}")
    @Transactional
    public void dispatchDueWhatsAppMessages() {
        Instant now = Instant.now(clock);
        for (NotificationWhatsAppDeliveryEntity delivery : repository.lockDueDeliveries(now, batchSize)) {
            if (!whatsAppService.isEnabledAndConfigured()) {
                delivery.markDisabled(now, whatsAppService.unconfiguredReason());
                continue;
            }
            delivery.beginAttempt(now);
            try {
                String providerMessageId = whatsAppService.sendWhatsAppNotification(delivery.getNotification());
                delivery.markSent(Instant.now(clock), providerMessageId);
            } catch (RuntimeException failure) {
                delivery.markFailed(
                        Instant.now(clock),
                        maxAttempts,
                        retryDelay,
                        failure.getClass().getSimpleName() + ": " + failure.getMessage());
                log.warn("Notification WhatsApp delivery failed notificationId={} attempt={} status={}",
                        delivery.getNotification().getId(), delivery.getAttemptCount(), delivery.getStatus());
            }
        }
    }
}


package ma.wwtrading.notifications.infrastructure.kafka;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTopics;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.notifications.application.CreateNotificationCommand;
import ma.wwtrading.notifications.application.NotificationApplicationService;
import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.domain.ProcessedEventRepository;

/**
 * Consumes user domain events (topic wwtrading.user.events.v1, already
 * published by the user-service outbox) and sends the idempotent welcome
 * notification to newly registered users.
 */
@Component
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationApplicationService notificationService;
    private final ObjectMapper objectMapper;

    public UserEventListener(
            ProcessedEventRepository processedEventRepository,
            NotificationApplicationService notificationService) {
        this.processedEventRepository = processedEventRepository;
        this.notificationService = notificationService;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @KafkaListener(topics = EventTopics.USER_EVENTS_V1, groupId = "notification-service")
    @Transactional
    public void onUserEvent(String messageJson) {
        try {
            DomainEvent<?> rawEvent = objectMapper.readValue(messageJson, DomainEvent.class);
            if (rawEvent == null || rawEvent.eventId() == null) {
                log.warn("Received null or invalid Kafka user event payload");
                return;
            }

            if (processedEventRepository.claim(rawEvent.eventId(), rawEvent.eventType(), Instant.now()) == 0) {
                log.info("Duplicate event ID {} already processed. Skipping notification dispatch.", rawEvent.eventId());
                return;
            }

            if (!EventTypes.USER_CREATED.equals(rawEvent.eventType())) {
                log.debug("Skipping unhandled user event type: {}", rawEvent.eventType());
                return;
            }

            EventPayloads.UserCreated payload = objectMapper.convertValue(
                    rawEvent.payload(), EventPayloads.UserCreated.class);

            processUserCreated(rawEvent.eventId(), payload);

        } catch (Exception e) {
            log.error("Failed to process user Kafka event: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka user event processing failed", e);
        }
    }

    private void processUserCreated(UUID eventId, EventPayloads.UserCreated payload) {
        if (payload.subject() == null || payload.subject().isBlank()) {
            log.warn("USER_CREATED event {} has no subject; skipping welcome notification", eventId);
            return;
        }
        notificationService.createNotification(new CreateNotificationCommand(
                payload.subject(),
                payload.email(),
                NotificationType.WELCOME,
                NotificationChannel.BOTH,
                "Bienvenue chez WWTRADING",
                "Votre compte WWTRADING a été créé avec succès. Vous pouvez dès à présent découvrir nos machines, consommables, pièces de rechange et services d'impression professionnelle.",
                "/products",
                "USER",
                payload.subject(),
                null,
                null,
                eventId
        ));
        log.info("Dispatched WELCOME notification to subject {}", payload.subject());
    }
}

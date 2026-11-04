package ma.wwtrading.notifications.infrastructure.kafka;

import java.time.Instant;
import java.util.List;
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
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient;

@Component
public class ProductEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationApplicationService notificationService;
    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;

    public ProductEventListener(
            ProcessedEventRepository processedEventRepository,
            NotificationApplicationService notificationService,
            UserServiceClient userServiceClient) {
        this.processedEventRepository = processedEventRepository;
        this.notificationService = notificationService;
        this.userServiceClient = userServiceClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @KafkaListener(topics = EventTopics.PRODUCT_EVENTS_V1, groupId = "notification-service")
    @Transactional
    public void onProductEvent(String messageJson) {
        try {
            DomainEvent<?> rawEvent = objectMapper.readValue(messageJson, DomainEvent.class);
            if (rawEvent == null || rawEvent.eventId() == null) {
                log.warn("Received null or invalid Kafka event payload");
                return;
            }

            if (processedEventRepository.claim(rawEvent.eventId(), rawEvent.eventType(), Instant.now()) == 0) {
                log.info("Duplicate event ID {} already processed. Skipping notification dispatch.", rawEvent.eventId());
                return;
            }

            if (!EventTypes.PRODUCT_CREATED.equals(rawEvent.eventType())) {
                log.debug("Skipping unhandled product event type: {}", rawEvent.eventType());
                return;
            }

            EventPayloads.ProductCreated payload = objectMapper.convertValue(
                    rawEvent.payload(), EventPayloads.ProductCreated.class);

            processProductCreatedEvent(rawEvent.eventId(), payload);

        } catch (Exception e) {
            log.error("Failed to process product Kafka event: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka event processing failed", e);
        }
    }

    public void processProductCreatedEvent(UUID eventId, EventPayloads.ProductCreated payload) {
        String brand = payload.brand() != null ? payload.brand() : "";
        String model = payload.model() != null ? payload.model() : "";
        String title = "Nouveau produit disponible";
        String message = ("Un nouveau produit a été ajouté au catalogue : " + brand + " " + model).trim();
        String actionUrl = "/products/" + payload.productId();

        List<UserServiceClient.ClientRecipient> clients = userServiceClient.getClientRecipients();
        if (clients.isEmpty()) {
            log.warn("No client recipients found when processing PRODUCT_CREATED event ID {}", eventId);
            return;
        }

        for (UserServiceClient.ClientRecipient client : clients) {
            if (!client.enabled()) {
                continue;
            }
            CreateNotificationCommand command = new CreateNotificationCommand(
                    client.keycloakSubject(),
                    client.email(),
                    NotificationType.PRODUCT_CREATED,
                    NotificationChannel.BOTH,
                    title,
                    message,
                    actionUrl,
                    "PRODUCT",
                    payload.productId().toString(),
                    null,
                    null,
                    eventId
            );

            notificationService.createNotification(command);
            log.info("Dispatched PRODUCT_CREATED notification to client subject {}", client.keycloakSubject());
        }
    }
}

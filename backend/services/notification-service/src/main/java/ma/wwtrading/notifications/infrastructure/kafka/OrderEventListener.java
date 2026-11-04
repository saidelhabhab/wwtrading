package ma.wwtrading.notifications.infrastructure.kafka;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTopics;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.notifications.application.NotificationApplicationService;
import ma.wwtrading.notifications.application.NotificationPolicy;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.domain.ProcessedEventRepository;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient;

/**
 * Consumes order domain events from Kafka and dispatches notifications
 * through the central application service for both registered customers and guests.
 * Duplicate events are discarded via the database-backed processed_events claim.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationApplicationService notificationService;
    private final ObjectMapper objectMapper;
    private final UserServiceClient users;
    private final NotificationPolicy policy;

    public OrderEventListener(
            ProcessedEventRepository processedEventRepository,
            NotificationApplicationService notificationService, UserServiceClient users,
            NotificationPolicy policy) {
        this.processedEventRepository = processedEventRepository;
        this.notificationService = notificationService;
        this.users = users;
        this.policy = policy;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @KafkaListener(topics = EventTopics.ORDER_EVENTS_V1, groupId = "notification-service")
    @Transactional
    public void onOrderEvent(String messageJson) {
        try {
            DomainEvent<?> rawEvent = objectMapper.readValue(messageJson, DomainEvent.class);
            if (rawEvent == null || rawEvent.eventId() == null) {
                log.warn("Received null or invalid Kafka order event payload");
                return;
            }

            if (processedEventRepository.claim(rawEvent.eventId(), rawEvent.eventType(), Instant.now()) == 0) {
                log.info("Duplicate event ID {} already processed. Skipping notification dispatch.", rawEvent.eventId());
                return;
            }

            switch (rawEvent.eventType()) {
                case EventTypes.ORDER_CREATED ->
                    processOrderCreated(
                            rawEvent.eventId(), objectMapper.convertValue(rawEvent.payload(), EventPayloads.OrderCreated.class));
                case EventTypes.ORDER_STATUS_CHANGED ->
                    processOrderStatusChanged(
                            rawEvent.eventId(), objectMapper.convertValue(rawEvent.payload(), EventPayloads.OrderStatusChanged.class));
                case EventTypes.PAYMENT_STATUS_CHANGED ->
                    processPaymentStatusChanged(
                            rawEvent.eventId(), objectMapper.convertValue(rawEvent.payload(), EventPayloads.PaymentStatusChanged.class));
                default ->
                    log.debug("Skipping unhandled order event type: {}", rawEvent.eventType());
            }

        } catch (Exception e) {
            log.error("Failed to process order Kafka event: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka order event processing failed", e);
        }
    }

    private void processOrderCreated(UUID eventId, EventPayloads.OrderCreated payload) {
        boolean hasCustomerSubject = payload.customerSubject() != null && !payload.customerSubject().isBlank();
        boolean hasGuestEmail = payload.customerEmail() != null && !payload.customerEmail().isBlank();

        if (!hasCustomerSubject && !payload.guestOrder() && !hasGuestEmail) {
            log.warn("ORDER_CREATED event {} has no customer subject or guest email; skipping notification", eventId);
            return;
        }

        if (hasCustomerSubject) {
            users.findEnabledClient(payload.customerSubject()).ifPresentOrElse(recipient -> {
                String phone = payload.customerPhone() != null && !payload.customerPhone().isBlank()
                        ? payload.customerPhone()
                        : recipient.phone();
                notificationService.createNotification(policy.orderCreatedForCustomer(
                        eventId,
                        payload,
                        recipient.keycloakSubject(),
                        recipient.email(),
                        phone));
            }, () -> {
                if (hasGuestEmail) {
                    notificationService.createNotification(policy.orderCreatedForCustomer(
                            eventId,
                            payload,
                            payload.customerSubject(),
                            payload.customerEmail(),
                            payload.customerPhone()));
                } else {
                    throw new IllegalStateException("Order client recipient could not be resolved");
                }
            });
        } else {
            String guestSubject = "guest:" + payload.orderId();
            notificationService.createNotification(policy.orderCreatedForCustomer(
                    eventId,
                    payload,
                    guestSubject,
                    payload.customerEmail(),
                    payload.customerPhone()));
        }

        users.getRecipients(Set.of("ADMIN", "MANAGER"), null).forEach(recipient ->
                notificationService.createNotification(policy.internal(eventId, NotificationType.ORDER_CREATED,
                        recipient.keycloakSubject(), recipient.email(), "Nouvelle commande",
                        "Une nouvelle commande a été enregistrée.", "/orders/" + payload.orderId(),
                        "ORDER", payload.orderId())));
        log.info("Dispatched ORDER_CREATED notification for order {} (guest={})",
                payload.orderId(), payload.guestOrder());
    }

    private void processOrderStatusChanged(UUID eventId, EventPayloads.OrderStatusChanged payload) {
        boolean hasCustomerSubject = payload.customerSubject() != null && !payload.customerSubject().isBlank();
        boolean hasGuestEmail = payload.customerEmail() != null && !payload.customerEmail().isBlank();
        if (!hasCustomerSubject && !payload.guestOrder() && !hasGuestEmail) {
            return;
        }
        String humanStatus = humanLabel(payload.status());
        if (hasCustomerSubject) {
            users.findEnabledClient(payload.customerSubject()).ifPresentOrElse(recipient -> {
                String phone = payload.customerPhone() != null && !payload.customerPhone().isBlank()
                        ? payload.customerPhone()
                        : recipient.phone();
                notificationService.createNotification(policy.orderStatusChangedForCustomer(
                        eventId,
                        payload,
                        recipient.keycloakSubject(),
                        recipient.email(),
                        phone,
                        humanStatus));
            }, () -> {
                if (hasGuestEmail) {
                    notificationService.createNotification(policy.orderStatusChangedForCustomer(
                            eventId,
                            payload,
                            payload.customerSubject(),
                            payload.customerEmail(),
                            payload.customerPhone(),
                            humanStatus));
                } else {
                    throw new IllegalStateException("Order client recipient could not be resolved");
                }
            });
        } else {
            notificationService.createNotification(policy.orderStatusChangedForCustomer(
                    eventId,
                    payload,
                    "guest:" + payload.orderId(),
                    payload.customerEmail(),
                    payload.customerPhone(),
                    humanStatus));
        }
        log.info("Dispatched ORDER_STATUS_CHANGED notification for order {} (guest={})",
                payload.orderId(), payload.guestOrder());
    }

    private void processPaymentStatusChanged(UUID eventId, EventPayloads.PaymentStatusChanged payload) {
        boolean hasCustomerSubject = payload.customerSubject() != null && !payload.customerSubject().isBlank();
        boolean hasGuestEmail = payload.customerEmail() != null && !payload.customerEmail().isBlank();
        if (!hasCustomerSubject && !payload.guestOrder() && !hasGuestEmail) {
            return;
        }
        String humanPaymentStatus = humanLabel(payload.paymentStatus());
        if (hasCustomerSubject) {
            users.findEnabledClient(payload.customerSubject()).ifPresentOrElse(recipient -> {
                String phone = payload.customerPhone() != null && !payload.customerPhone().isBlank()
                        ? payload.customerPhone()
                        : recipient.phone();
                notificationService.createNotification(policy.paymentStatusChangedForCustomer(
                        eventId,
                        payload,
                        recipient.keycloakSubject(),
                        recipient.email(),
                        phone,
                        humanPaymentStatus));
            }, () -> {
                if (hasGuestEmail) {
                    notificationService.createNotification(policy.paymentStatusChangedForCustomer(
                            eventId,
                            payload,
                            payload.customerSubject(),
                            payload.customerEmail(),
                            payload.customerPhone(),
                            humanPaymentStatus));
                } else {
                    throw new IllegalStateException("Order client recipient could not be resolved");
                }
            });
        } else {
            notificationService.createNotification(policy.paymentStatusChangedForCustomer(
                    eventId,
                    payload,
                    "guest:" + payload.orderId(),
                    payload.customerEmail(),
                    payload.customerPhone(),
                    humanPaymentStatus));
        }
        log.info("Dispatched PAYMENT_STATUS_CHANGED notification for order {} (guest={})",
                payload.orderId(), payload.guestOrder());
    }

    static String humanLabel(String technicalStatus) {
        return switch (technicalStatus == null ? "" : technicalStatus) {
            case "DRAFT" ->
                "Brouillon";
            case "CONFIRMED" ->
                "Confirmée";
            case "PROCESSING" ->
                "En préparation";
            case "READY" ->
                "Prête";
            case "COMPLETED" ->
                "Terminée";
            case "CANCELLED" ->
                "Annulée";
            case "UNPAID" ->
                "En attente de paiement";
            case "PARTIALLY_PAID" ->
                "Acompte de 50 % confirmé (partiellement payée)";
            case "PAID" ->
                "Paiement confirmé";
            case "REFUNDED" ->
                "Remboursée";
            default ->
                technicalStatus == null ? "" : technicalStatus;
        };
    }
}

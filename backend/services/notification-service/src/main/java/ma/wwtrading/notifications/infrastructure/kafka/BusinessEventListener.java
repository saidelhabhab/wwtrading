package ma.wwtrading.notifications.infrastructure.kafka;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
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
import ma.wwtrading.notifications.application.NotificationApplicationService;
import ma.wwtrading.notifications.application.NotificationPolicy;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.domain.ProcessedEventRepository;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient.ClientRecipient;

@Component
public class BusinessEventListener {
    private static final Logger log = LoggerFactory.getLogger(BusinessEventListener.class);
    private final ProcessedEventRepository processed;
    private final NotificationApplicationService notifications;
    private final NotificationPolicy policy;
    private final UserServiceClient users;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public BusinessEventListener(ProcessedEventRepository processed, NotificationApplicationService notifications,
            NotificationPolicy policy, UserServiceClient users) {
        this.processed = processed; this.notifications = notifications; this.policy = policy; this.users = users;
    }

    @KafkaListener(topics = { EventTopics.QUOTATION_EVENTS_V1, EventTopics.MAINTENANCE_EVENTS_V1,
            EventTopics.SUPPORT_EVENTS_V1 }, groupId = "notification-service")
    @Transactional
    public void onEvent(String json) {
        try {
            DomainEvent<?> event = mapper.readValue(json, DomainEvent.class);
            if (event == null || event.eventId() == null) return;
            if (processed.claim(event.eventId(), event.eventType(), Instant.now()) == 0) return;
            switch (event.eventType()) {
                case EventTypes.QUOTATION_CREATED -> quotationCreated(event);
                case EventTypes.QUOTATION_STATUS_CHANGED -> quotationStatusChanged(event);
                case EventTypes.MAINTENANCE_CREATED -> maintenanceCreated(event);
                case EventTypes.MAINTENANCE_ASSIGNED -> maintenanceAssigned(event);
                case EventTypes.MAINTENANCE_STATUS_CHANGED -> maintenanceStatusChanged(event);
                case EventTypes.MAINTENANCE_COMPLETED -> maintenanceCompleted(event);
                case EventTypes.SUPPORT_MESSAGE_CREATED -> supportMessage(event);
                case EventTypes.SUPPORT_REPLY_CREATED -> supportReply(event);
                default -> log.debug("Ignoring unsupported event type {}", event.eventType());
            }
        } catch (Exception failure) {
            log.error("Central notification event processing failed", failure);
            throw new IllegalStateException("Central notification event processing failed", failure);
        }
    }

    private void quotationCreated(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.QuotationCreated.class);
        recipient(payload.customerSubject()).ifPresent(value -> notifications.createNotification(
                policy.quotationCreated(event.eventId(), payload, value)));
        users.getRecipients(Set.of("ADMIN", "MANAGER", "COMMERCIAL"), null).forEach(value ->
                notifications.createNotification(policy.internal(event.eventId(), NotificationType.QUOTATION_CREATED,
                        value.keycloakSubject(), value.email(), "Nouvelle demande de devis",
                        "Une nouvelle demande de devis requiert l’attention de l’équipe commerciale.",
                        "/devis/" + payload.quotationId(), "QUOTATION", payload.quotationId())));
    }
    private void quotationStatusChanged(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.QuotationStatusChanged.class);
        recipient(payload.customerSubject()).ifPresent(value -> notifications.createNotification(
                policy.quotationStatusChanged(event.eventId(), payload, value)));
    }
    private void maintenanceCreated(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.MaintenanceCreated.class);
        recipient(payload.customerSubject()).ifPresent(value -> notifications.createNotification(policy.maintenanceClient(
                event.eventId(), payload.maintenanceId(), value.keycloakSubject(), value.email(),
                NotificationType.MAINTENANCE_CREATED, "Demande de maintenance reçue",
                "Votre demande de maintenance a bien été enregistrée.")));
    }
    private void maintenanceAssigned(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.MaintenanceAssigned.class);
        if (present(payload.technicianSubject())) {
            var technicians = users.getRecipients(Set.of("TECHNICIAN"), payload.technicianSubject());
            if (technicians.isEmpty()) throw new IllegalStateException("Assigned technician recipient not found");
            technicians.forEach(value -> notifications.createNotification(policy.client(event.eventId(),
                    NotificationType.MAINTENANCE_ASSIGNED, value, "Nouvelle intervention assignée",
                    "Une demande de maintenance vous a été assignée.",
                    "/technician/maintenance/" + payload.maintenanceId(), "MAINTENANCE", payload.maintenanceId())));
        }
        recipient(payload.customerSubject()).ifPresent(value -> notifications.createNotification(policy.maintenanceClient(
                event.eventId(), payload.maintenanceId(), value.keycloakSubject(), value.email(),
                NotificationType.MAINTENANCE_ASSIGNED, "Intervention assignée",
                "Votre demande de maintenance a été assignée à un technicien.")));
    }
    private void maintenanceStatusChanged(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.MaintenanceStatusChanged.class);
        recipient(payload.customerSubject()).ifPresent(value -> notifications.createNotification(policy.maintenanceClient(
                event.eventId(), payload.maintenanceId(), value.keycloakSubject(), value.email(),
                NotificationType.MAINTENANCE_STATUS_CHANGED, "Maintenance mise à jour",
                "Le statut de votre demande de maintenance a changé.")));
    }
    private void maintenanceCompleted(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.MaintenanceCompleted.class);
        recipient(payload.customerSubject()).ifPresent(value -> notifications.createNotification(policy.maintenanceClient(
                event.eventId(), payload.maintenanceId(), value.keycloakSubject(), value.email(),
                NotificationType.MAINTENANCE_COMPLETED, "Maintenance terminée",
                "Votre intervention de maintenance est terminée.")));
    }
    private void supportMessage(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.SupportMessageCreated.class);
        // A client message must reach every authorized staff member (ADMIN, MANAGER,
        // COMMERCIAL) independently, regardless of any conversation assignment, with a
        // recipient-aware action URL. Recipients are resolved from staff roles via the
        // protected internal user-service endpoint, never from the sender's role.
        var recipients = users.getRecipients(Set.of("ADMIN", "MANAGER", "COMMERCIAL"), null);
        if (recipients.isEmpty()) {
            throw new IllegalStateException("No authorized support recipient available");
        }
        recipients.forEach(value -> notifications.createNotification(policy.supportMessageForStaff(
                event.eventId(), payload.conversationId(), value.keycloakSubject(), staffRole(value))));
    }

    private String staffRole(ClientRecipient recipient) {
        return recipient.roles().stream()
                .filter(role -> Set.of("ADMIN", "MANAGER", "COMMERCIAL").contains(role.toUpperCase()))
                .findFirst()
                .orElse("COMMERCIAL");
    }
    private void supportReply(DomainEvent<?> event) {
        var payload = mapper.convertValue(event.payload(), EventPayloads.SupportReplyCreated.class);
        recipient(payload.recipientSubject()).ifPresent(value -> notifications.createNotification(
                policy.supportReply(event.eventId(), payload.conversationId(), value)));
    }
    private Optional<ClientRecipient> recipient(String subject) {
        if (!present(subject)) return Optional.empty();
        Optional<ClientRecipient> result = users.findEnabledClient(subject);
        if (result.isEmpty()) throw new IllegalStateException("Enabled client recipient not found");
        return result;
    }
    private boolean present(String value) { return value != null && !value.isBlank(); }
}

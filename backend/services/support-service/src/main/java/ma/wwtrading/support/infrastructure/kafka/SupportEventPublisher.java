package ma.wwtrading.support.infrastructure.kafka;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTopics;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.support.domain.SupportConversationEntity;
import ma.wwtrading.support.domain.SupportMessageEntity;
import ma.wwtrading.support.domain.SupportSenderRole;

@Component
public class SupportEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(SupportEventPublisher.class);
    private final KafkaTemplate<String, DomainEvent<?>> kafka;
    public SupportEventPublisher(KafkaTemplate<String, DomainEvent<?>> kafka) { this.kafka = kafka; }

    public void messageCreated(SupportConversationEntity conversation, SupportMessageEntity message) {
        boolean clientMessage = message.getSenderRole() == SupportSenderRole.CLIENT;
        String recipient = clientMessage ? conversation.getAssignedAgentKeycloakSubject() : conversation.getClientKeycloakSubject();
        String type = clientMessage ? EventTypes.SUPPORT_MESSAGE_CREATED : EventTypes.SUPPORT_REPLY_CREATED;
        Object payload = clientMessage
                ? new EventPayloads.SupportMessageCreated(conversation.getId(), message.getId(), message.getSenderKeycloakSubject(), recipient, message.getSenderRole().name())
                : new EventPayloads.SupportReplyCreated(conversation.getId(), message.getId(), message.getSenderKeycloakSubject(), recipient, message.getSenderRole().name());
        DomainEvent<Object> event = new DomainEvent<>(UUID.randomUUID(), type, EventTypes.VERSION_1, Instant.now(), null,
                "support-service", "SUPPORT_CONVERSATION", conversation.getId().toString(), message.getSenderKeycloakSubject(), payload);
        Runnable send = () -> kafka.send(EventTopics.SUPPORT_EVENTS_V1, event.aggregateId(), event)
                .whenComplete((result, failure) -> { if (failure != null) log.error("Failed to publish support event {}", event.eventId(), failure); });
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() { @Override public void afterCommit() { send.run(); } });
        else send.run();
    }
}

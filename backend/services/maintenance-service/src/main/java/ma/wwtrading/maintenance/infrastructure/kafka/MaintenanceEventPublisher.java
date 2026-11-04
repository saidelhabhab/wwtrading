package ma.wwtrading.maintenance.infrastructure.kafka;

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
import ma.wwtrading.maintenance.domain.MaintenanceEntity;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;

@Component
public class MaintenanceEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceEventPublisher.class);
    private final KafkaTemplate<String, DomainEvent<?>> kafka;
    public MaintenanceEventPublisher(KafkaTemplate<String, DomainEvent<?>> kafka) { this.kafka = kafka; }

    public void created(MaintenanceEntity item) {
        publish(EventTypes.MAINTENANCE_CREATED, item, new EventPayloads.MaintenanceCreated(
                item.getId(), item.getCustomerSubject(), item.getStatus().name()));
    }
    public void assigned(MaintenanceEntity item) {
        publish(EventTypes.MAINTENANCE_ASSIGNED, item, new EventPayloads.MaintenanceAssigned(
                item.getId(), item.getCustomerSubject(), subject(item.getTechnicianId()), item.getStatus().name()));
    }
    public void statusChanged(MaintenanceEntity item, MaintenanceStatus previous) {
        String type = item.getStatus() == MaintenanceStatus.COMPLETED
                ? EventTypes.MAINTENANCE_COMPLETED : EventTypes.MAINTENANCE_STATUS_CHANGED;
        Object payload = item.getStatus() == MaintenanceStatus.COMPLETED
                ? new EventPayloads.MaintenanceCompleted(item.getId(), item.getCustomerSubject(), subject(item.getTechnicianId()), item.getStatus().name())
                : new EventPayloads.MaintenanceStatusChanged(item.getId(), item.getCustomerSubject(), subject(item.getTechnicianId()), previous.name(), item.getStatus().name());
        publish(type, item, payload);
    }
    private void publish(String type, MaintenanceEntity item, Object payload) {
        DomainEvent<Object> event = new DomainEvent<>(UUID.randomUUID(), type, EventTypes.VERSION_1, Instant.now(),
                null, "maintenance-service", "MAINTENANCE", item.getId().toString(), null, payload);
        Runnable send = () -> kafka.send(EventTopics.MAINTENANCE_EVENTS_V1, event.aggregateId(), event)
                .whenComplete((result, failure) -> { if (failure != null) log.error("Failed to publish maintenance event {}", event.eventId(), failure); });
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() { @Override public void afterCommit() { send.run(); } });
        else send.run();
    }
    private String subject(UUID value) { return value == null ? null : value.toString(); }
}

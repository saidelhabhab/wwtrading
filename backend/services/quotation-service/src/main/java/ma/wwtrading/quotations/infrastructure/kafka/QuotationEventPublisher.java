package ma.wwtrading.quotations.infrastructure.kafka;

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
import ma.wwtrading.quotations.domain.QuotationEntity;
import ma.wwtrading.quotations.domain.QuotationStatus;

@Component
public class QuotationEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(QuotationEventPublisher.class);
    private final KafkaTemplate<String, DomainEvent<?>> kafka;

    public QuotationEventPublisher(KafkaTemplate<String, DomainEvent<?>> kafka) { this.kafka = kafka; }

    public void quotationCreated(QuotationEntity quotation) {
        publish(new DomainEvent<>(UUID.randomUUID(), EventTypes.QUOTATION_CREATED, EventTypes.VERSION_1,
                Instant.now(), null, "quotation-service", "QUOTATION", quotation.getId().toString(),
                quotation.getCustomerSubject(), new EventPayloads.QuotationCreated(
                    quotation.getId(), quotation.getCustomerSubject(), quotation.getStatus().name())));
    }

    public void quotationStatusChanged(QuotationEntity quotation, QuotationStatus previous) {
        publish(new DomainEvent<>(UUID.randomUUID(), EventTypes.QUOTATION_STATUS_CHANGED, EventTypes.VERSION_1,
                Instant.now(), null, "quotation-service", "QUOTATION", quotation.getId().toString(), null,
                new EventPayloads.QuotationStatusChanged(quotation.getId(), quotation.getCustomerSubject(),
                    previous.name(), quotation.getStatus().name())));
    }

    private void publish(DomainEvent<?> event) {
        Runnable send = () -> kafka.send(EventTopics.QUOTATION_EVENTS_V1, event.aggregateId(), event)
                .whenComplete((result, failure) -> {
                    if (failure != null) log.error("Failed to publish quotation event {}", event.eventId(), failure);
                    else log.info("Published {} event {}", event.eventType(), event.eventId());
                });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else send.run();
    }
}

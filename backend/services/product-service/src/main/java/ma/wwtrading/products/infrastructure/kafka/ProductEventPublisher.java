package ma.wwtrading.products.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventTopics;

@Component
public class ProductEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProductEventPublisher.class);

    private final KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;

    public ProductEventPublisher(KafkaTemplate<String, DomainEvent<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductCreated(ProductCreatedDomainEvent wrapper) {
        DomainEvent<?> event = wrapper.event();
        try {
            kafkaTemplate.send(EventTopics.PRODUCT_EVENTS_V1, event.aggregateId(), event);
            log.info("Successfully published PRODUCT_CREATED event ID {} to topic {} for product ID {}",
                    event.eventId(), EventTopics.PRODUCT_EVENTS_V1, event.aggregateId());
        } catch (Exception e) {
            log.error("Failed to publish PRODUCT_CREATED event ID {} to topic {}: {}",
                    event.eventId(), EventTopics.PRODUCT_EVENTS_V1, e.getMessage(), e);
        }
    }
}

package ma.wwtrading.orders.infrastructure.kafka;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventTopics;

/**
 * Publishes order domain events to Kafka after the business transaction
 * commits.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, DomainEvent<?>> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, DomainEvent<?>> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(DomainEvent<?> event) {
        if (event == null) {
            return;
        }
        handle(new OrderDomainEvent(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderDomainEvent wrapper) {
        DomainEvent<?> event = wrapper.event();
        try {
            kafkaTemplate.send(EventTopics.ORDER_EVENTS_V1, event.aggregateId(), event)
                    .whenComplete((result, failure) -> {
                        if (failure != null) {
                            log.error("Failed to publish order event ID {} type {} for order ID {}",
                                    event.eventId(), event.eventType(), event.aggregateId(), failure);
                            return;
                        }
                        log.info("Published order event ID {} type {} for order ID {}",
                                event.eventId(), event.eventType(), event.aggregateId());
                    });
        } catch (Exception e) {
            log.error("Failed to publish order event ID {} type {} for order ID {}",
                    event.eventId(), event.eventType(), event.aggregateId(), e);
        }
    }

    /**
     * Wraps an event so Spring can deliver it to the AFTER_COMMIT listener.
     */
    public record OrderDomainEvent(DomainEvent<?> event) {

        public static OrderDomainEvent of(Supplier<DomainEvent<?>> supplier) {
            return new OrderDomainEvent(supplier.get());
        }
    }
}

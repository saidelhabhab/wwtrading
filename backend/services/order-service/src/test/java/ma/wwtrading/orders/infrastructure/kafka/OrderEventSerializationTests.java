package ma.wwtrading.orders.infrastructure.kafka;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTopics;
import ma.wwtrading.events.EventTypes;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the fixed order-service Kafka producer serialization: the existing
 * {@link DomainEvent} contract must be serialized as JSON (never pushed through
 * the String value serializer) while keeping the payload contract unchanged.
 */
class OrderEventSerializationTests {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private DomainEvent<EventPayloads.OrderStatusChanged> sampleEvent() {
        UUID orderId = UUID.randomUUID();
        return new DomainEvent<>(
                UUID.randomUUID(),
                EventTypes.ORDER_STATUS_CHANGED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                orderId.toString(),
                "client-subject",
                new EventPayloads.OrderStatusChanged(orderId, "client-subject", "DRAFT", "CONFIRMED"));
    }

    @Test
    void serializesDomainEventAsJsonWithoutSerializationException() throws Exception {
        DomainEvent<EventPayloads.OrderStatusChanged> event = sampleEvent();

        byte[] bytes;
        try (var serializer = new JsonSerializer<DomainEvent<?>>()) {
            bytes = serializer.serialize("wwtrading.order.events.v1", null, event);
        }
        assertThat(bytes).isNotNull().isNotEmpty();

        JsonNode json = objectMapper.readTree(bytes);
        assertThat(json.isObject()).isTrue();
    }

    @Test
    void serializedJsonKeepsExistingEventContractFields() throws Exception {
        DomainEvent<EventPayloads.OrderStatusChanged> event = sampleEvent();

        try (var serializer = new JsonSerializer<DomainEvent<?>>()) {
            byte[] bytes = serializer.serialize("wwtrading.order.events.v1", null, event);
            JsonNode json = objectMapper.readTree(bytes);

            assertThat(json.get("eventId").asText()).isEqualTo(event.eventId().toString());
            assertThat(json.get("eventType").asText()).isEqualTo("ORDER_STATUS_CHANGED");
            assertThat(json.get("eventVersion").asText()).isEqualTo("v1");
            assertThat(json.has("occurredAt")).isTrue();
            assertThat(json.get("producer").asText()).isEqualTo("order-service");
            assertThat(json.get("aggregateType").asText()).isEqualTo("ORDER");
            assertThat(json.get("aggregateId").asText()).isEqualTo(event.aggregateId());
            assertThat(json.get("actorSubject").asText()).isEqualTo("client-subject");

            JsonNode payload = json.get("payload");
            assertThat(payload.get("orderId").asText()).isEqualTo(event.payload().orderId().toString());
            assertThat(payload.get("customerSubject").asText()).isEqualTo("client-subject");
            assertThat(payload.get("previousStatus").asText()).isEqualTo("DRAFT");
            assertThat(payload.get("status").asText()).isEqualTo("CONFIRMED");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void publisherSendsTheExistingDomainEventContract() {
        DomainEvent<EventPayloads.OrderStatusChanged> event = sampleEvent();
        KafkaTemplate<String, DomainEvent<?>> template = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, DomainEvent<?>>> result = CompletableFuture.completedFuture(null);
        when(template.send(EventTopics.ORDER_EVENTS_V1, event.aggregateId(), event)).thenReturn(result);
        OrderEventPublisher publisher = new OrderEventPublisher(template);

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
        verify(template).send(EventTopics.ORDER_EVENTS_V1, event.aggregateId(), event);
    }
}

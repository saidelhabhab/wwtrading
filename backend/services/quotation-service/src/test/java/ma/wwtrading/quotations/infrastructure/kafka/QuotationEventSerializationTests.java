package ma.wwtrading.quotations.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventTypes;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuotationEventSerializationTests {

    @Test
    void serializesDomainEventEnvelopeAsJson() throws Exception {
        UUID eventId = UUID.randomUUID();
        DomainEvent<Map<String, Object>> event = new DomainEvent<>(eventId, EventTypes.QUOTATION_CREATED,
            EventTypes.VERSION_1, Instant.parse("2026-01-01T00:00:00Z"), "correlation-id",
            "quotation-service", "QUOTATION", "quotation-id", "commercial-subject",
            Map.of("customerSubject", "client-subject", "quotationNumber", "DEV-001"));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        byte[] serialized;
        try (JsonSerializer<DomainEvent<?>> serializer = new JsonSerializer<>(objectMapper)) {
            serialized = serializer.serialize("wwtrading.quotation.events.v1", event);
        }
        JsonNode json = objectMapper.readTree(serialized);

        assertThat(json.path("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(json.path("eventType").asText()).isEqualTo(EventTypes.QUOTATION_CREATED);
        assertThat(json.path("eventVersion").asText()).isEqualTo(EventTypes.VERSION_1);
        JsonNode occurredAt = json.path("occurredAt");
        assertThat(occurredAt.isMissingNode()).isFalse();
        assertThat(objectMapper.treeToValue(occurredAt, Instant.class)).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(json.path("producer").asText()).isEqualTo("quotation-service");
        assertThat(json.path("aggregateType").asText()).isEqualTo("QUOTATION");
        assertThat(json.path("payload").path("customerSubject").asText()).isEqualTo("client-subject");
    }
}

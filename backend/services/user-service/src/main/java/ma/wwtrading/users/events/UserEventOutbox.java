package ma.wwtrading.users.events;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads.UserCreated;
import ma.wwtrading.events.EventTopics;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.users.domain.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserEventOutbox {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public UserEventOutbox(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void appendUserCreated(UserEntity user, String correlationId) {
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();
        DomainEvent<UserCreated> envelope = new DomainEvent<>(
            eventId,
            EventTypes.USER_CREATED,
            EventTypes.VERSION_1,
            occurredAt,
            correlationId,
            "user-service",
            "USER",
            user.getId().toString(),
            user.getKeycloakSubject(),
            new UserCreated(user.getKeycloakSubject(), user.getEmail(), displayName(user))
        );
        try {
            repository.save(OutboxEventEntity.pending(
                eventId,
                user.getId(),
                EventTopics.USER_EVENTS_V1,
                user.getId().toString(),
                objectMapper.writeValueAsString(envelope),
                occurredAt
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize user event", exception);
        }
    }

    private String displayName(UserEntity user) {
        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();
        String displayName = (firstName + " " + lastName).trim();
        return displayName.isEmpty() ? null : displayName;
    }
}

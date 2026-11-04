package ma.wwtrading.support.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "support_conversation")
public class SupportConversationEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "client_keycloak_subject", nullable = false, length = 64)
    private String clientKeycloakSubject;

    @Column(name = "assigned_agent_keycloak_subject", length = 64)
    private String assignedAgentKeycloakSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportStatus status = SupportStatus.OPEN;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SupportConversationEntity() {}

    public SupportConversationEntity(String clientKeycloakSubject, String subject) {
        this.clientKeycloakSubject = required(clientKeycloakSubject, "Client subject");
        this.subject = required(subject, "Conversation subject");
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public void assign(String agentKeycloakSubject) {
        assignedAgentKeycloakSubject = required(agentKeycloakSubject, "Agent subject");
    }

    public void changeStatus(SupportStatus target) {
        if (target == null) throw new IllegalArgumentException("Support status is required");
        if (status == target) return;
        boolean allowed = (status == SupportStatus.OPEN && (target == SupportStatus.IN_PROGRESS || target == SupportStatus.CLOSED))
                || (status == SupportStatus.IN_PROGRESS && target == SupportStatus.CLOSED)
                || (status == SupportStatus.CLOSED && target == SupportStatus.OPEN);
        if (!allowed) throw new IllegalStateException("Invalid support status transition");
        status = target;
        closedAt = target == SupportStatus.CLOSED ? Instant.now() : null;
    }

    public void touch() { updatedAt = Instant.now(); }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public UUID getId() { return id; }
    public String getClientKeycloakSubject() { return clientKeycloakSubject; }
    public String getAssignedAgentKeycloakSubject() { return assignedAgentKeycloakSubject; }
    public SupportStatus getStatus() { return status; }
    public String getSubject() { return subject; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
}

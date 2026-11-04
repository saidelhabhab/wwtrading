package ma.wwtrading.support.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_message")
public class SupportMessageEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "sender_keycloak_subject", nullable = false, length = 64)
    private String senderKeycloakSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 20)
    private SupportSenderRole senderRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportMessageEntity() {}

    public SupportMessageEntity(UUID conversationId, String senderKeycloakSubject,
                                SupportSenderRole senderRole, String content) {
        this.conversationId = conversationId;
        this.senderKeycloakSubject = senderKeycloakSubject;
        this.senderRole = senderRole;
        this.content = content.trim();
    }

    @PrePersist
    void beforeInsert() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public String getSenderKeycloakSubject() { return senderKeycloakSubject; }
    public SupportSenderRole getSenderRole() { return senderRole; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}

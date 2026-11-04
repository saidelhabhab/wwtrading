package ma.wwtrading.support.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.wwtrading.support.domain.SupportSenderRole;
import ma.wwtrading.support.domain.SupportStatus;

public final class SupportDtos {
    private SupportDtos() {}

    public record CreateConversationRequest(
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 10_000) String message) {}

    public record SendMessageRequest(@NotBlank @Size(max = 10_000) String content) {}

    public record AssignConversationRequest(
            @NotBlank @Size(max = 64) String agentKeycloakSubject) {}

    public record ChangeStatusRequest(@NotNull SupportStatus status) {}

    public record ConversationResponse(
            UUID id,
            String subject,
            SupportStatus status,
            String clientKeycloakSubject,
            String assignedAgentKeycloakSubject,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt) {}

    public record MessageResponse(
            UUID id,
            UUID conversationId,
            String senderKeycloakSubject,
            SupportSenderRole senderRole,
            String content,
            Instant createdAt,
            List<AttachmentResponse> attachments) {}

    public record AttachmentResponse(
            UUID id,
            UUID messageId,
            String originalFilename,
            String contentType,
            long size,
            String contentUrl,
            Instant createdAt) {}
}

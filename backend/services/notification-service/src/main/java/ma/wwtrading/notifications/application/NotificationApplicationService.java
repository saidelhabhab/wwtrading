package ma.wwtrading.notifications.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.wwtrading.notifications.api.NotificationResponse;
import ma.wwtrading.notifications.api.OrderNotificationDeliveryResponse;
import ma.wwtrading.notifications.api.UnreadCountResponse;
import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationEmailDeliveryEntity;
import ma.wwtrading.notifications.domain.NotificationEmailDeliveryRepository;
import ma.wwtrading.notifications.domain.NotificationEntity;
import ma.wwtrading.notifications.domain.NotificationRepository;
import ma.wwtrading.notifications.domain.NotificationWhatsAppDeliveryEntity;
import ma.wwtrading.notifications.domain.NotificationWhatsAppDeliveryRepository;
import ma.wwtrading.notifications.domain.WhatsAppDeliveryStatus;
import ma.wwtrading.notifications.infrastructure.whatsapp.NotificationWhatsAppService;

@Service
public class NotificationApplicationService {

    private final NotificationRepository repository;
    private final NotificationEmailDeliveryRepository emailDeliveryRepository;
    private NotificationWhatsAppDeliveryRepository whatsAppDeliveryRepository;
    private NotificationWhatsAppService whatsAppService;
    private final Clock clock;

    @Autowired
    public NotificationApplicationService(
            NotificationRepository repository,
            NotificationEmailDeliveryRepository emailDeliveryRepository) {
        this(repository, emailDeliveryRepository, Clock.systemUTC());
    }

    NotificationApplicationService(
            NotificationRepository repository,
            NotificationEmailDeliveryRepository emailDeliveryRepository,
            Clock clock) {
        this.repository = repository;
        this.emailDeliveryRepository = emailDeliveryRepository;
        this.clock = clock;
    }

    @Autowired(required = false)
    public void setWhatsAppDeliveryRepository(NotificationWhatsAppDeliveryRepository whatsAppDeliveryRepository) {
        this.whatsAppDeliveryRepository = whatsAppDeliveryRepository;
    }

    @Autowired(required = false)
    public void setWhatsAppService(NotificationWhatsAppService whatsAppService) {
        this.whatsAppService = whatsAppService;
    }

    @Transactional
    public NotificationResponse createNotification(CreateNotificationCommand command) {
        NotificationEntity entity = new NotificationEntity(
                command.recipientSubject(),
                command.recipientEmail(),
                command.recipientPhone(),
                command.type(),
                command.channel(),
                command.title(),
                command.message(),
                command.actionUrl(),
                command.entityType(),
                command.entityId(),
                command.expiresAt(),
                command.metadata(),
                command.sourceEventId()
        );

        NotificationEntity saved = repository.saveAndFlush(entity);
        Instant now = Instant.now(clock);

        if (saved.getChannel() != null && saved.getChannel().includesEmail()) {
            emailDeliveryRepository.save(new NotificationEmailDeliveryEntity(saved, now));
        }

        if (whatsAppDeliveryRepository != null
                && saved.getChannel() != null
                && saved.getChannel().includesWhatsApp()
                && saved.getRecipientPhone() != null
                && !saved.getRecipientPhone().isBlank()) {
            if (whatsAppService != null && !whatsAppService.isEnabledAndConfigured()) {
                whatsAppDeliveryRepository.save(new NotificationWhatsAppDeliveryEntity(
                        saved,
                        now,
                        WhatsAppDeliveryStatus.DISABLED,
                        whatsAppService.unconfiguredReason()));
            } else {
                whatsAppDeliveryRepository.save(new NotificationWhatsAppDeliveryEntity(saved, now));
            }
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrderNotificationDeliveryResponse getOrderDeliveries(UUID orderId) {
        List<NotificationEntity> notifications = repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "ORDER", orderId.toString());
        if (notifications == null) {
            notifications = List.of();
        }
        List<OrderNotificationDeliveryResponse.NotificationDeliveryItem> items = new ArrayList<>();
        String overallEmailStatus = "NONE";
        String overallWhatsAppStatus = "NONE";
        String lastEmailError = null;
        String lastWhatsAppError = null;

        for (NotificationEntity notification : notifications) {
            if (notification.getRecipientSubject() != null
                    && !notification.getRecipientSubject().startsWith("guest:")
                    && notification.getChannel() == NotificationChannel.IN_APP) {
                continue;
            }
            var emailOpt = emailDeliveryRepository.findByNotificationId(notification.getId());
            var waOpt = whatsAppDeliveryRepository != null
                    ? whatsAppDeliveryRepository.findByNotificationId(notification.getId())
                    : java.util.Optional.<NotificationWhatsAppDeliveryEntity>empty();

            String emailStatus = emailOpt.map(e -> e.getStatus().name()).orElse(null);
            int emailAttempts = emailOpt.map(NotificationEmailDeliveryEntity::getAttemptCount).orElse(0);
            Instant emailSentAt = emailOpt.map(NotificationEmailDeliveryEntity::getSentAt).orElse(null);
            String emailError = emailOpt.map(NotificationEmailDeliveryEntity::getLastError).orElse(null);

            String waStatus = waOpt.map(w -> w.getStatus().name()).orElse(null);
            int waAttempts = waOpt.map(NotificationWhatsAppDeliveryEntity::getAttemptCount).orElse(0);
            String waProviderId = waOpt.map(NotificationWhatsAppDeliveryEntity::getProviderMessageId).orElse(null);
            Instant waSentAt = waOpt.map(NotificationWhatsAppDeliveryEntity::getSentAt).orElse(null);
            String waError = waOpt.map(NotificationWhatsAppDeliveryEntity::getLastError).orElse(null);

            if ("NONE".equals(overallEmailStatus) && emailStatus != null) {
                overallEmailStatus = emailStatus;
            }
            if (lastEmailError == null && emailError != null && !emailError.isBlank()) {
                lastEmailError = emailError;
            }
            if ("NONE".equals(overallWhatsAppStatus) && waStatus != null) {
                overallWhatsAppStatus = waStatus;
            }
            if (lastWhatsAppError == null && waError != null && !waError.isBlank()) {
                lastWhatsAppError = waError;
            }

            items.add(new OrderNotificationDeliveryResponse.NotificationDeliveryItem(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getRecipientEmail(),
                    notification.getRecipientPhone(),
                    notification.getCreatedAt(),
                    emailStatus,
                    emailAttempts,
                    emailSentAt,
                    emailError,
                    waStatus,
                    waAttempts,
                    waProviderId,
                    waSentAt,
                    waError));
        }

        return new OrderNotificationDeliveryResponse(
                orderId,
                overallEmailStatus,
                overallWhatsAppStatus,
                lastEmailError,
                lastWhatsAppError,
                items);
    }

    @Transactional
    public OrderNotificationDeliveryResponse retryOrderDeliveries(UUID orderId, String channelFilter) {
        List<NotificationEntity> notifications = repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "ORDER", orderId.toString());
        if (notifications == null || notifications.isEmpty()) {
            return getOrderDeliveries(orderId);
        }
        Instant now = Instant.now(clock);
        boolean retryEmail = channelFilter == null || channelFilter.isBlank()
                || "ALL".equalsIgnoreCase(channelFilter) || "EMAIL".equalsIgnoreCase(channelFilter);
        boolean retryWhatsApp = channelFilter == null || channelFilter.isBlank()
                || "ALL".equalsIgnoreCase(channelFilter) || "WHATSAPP".equalsIgnoreCase(channelFilter);

        for (NotificationEntity notification : notifications) {
            if (retryEmail) {
                emailDeliveryRepository.findByNotificationId(notification.getId())
                        .ifPresent(delivery -> delivery.resetForRetry(now));
            }
            if (retryWhatsApp && whatsAppDeliveryRepository != null) {
                whatsAppDeliveryRepository.findByNotificationId(notification.getId())
                        .ifPresent(delivery -> {
                            if (whatsAppService != null && !whatsAppService.isEnabledAndConfigured()) {
                                delivery.markDisabled(now, whatsAppService.unconfiguredReason());
                            } else {
                                delivery.resetForRetry(now);
                            }
                        });
            }
        }
        return getOrderDeliveries(orderId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listOwn(String recipientSubject, Pageable pageable) {
        return repository.findByRecipientSubject(requiredSubject(recipientSubject), pageable)
                .map(NotificationApplicationService::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String recipientSubject) {
        return new UnreadCountResponse(
                repository.countByRecipientSubjectAndReadAtIsNull(requiredSubject(recipientSubject)));
    }

    @Transactional
    public NotificationResponse markRead(String recipientSubject, UUID notificationId) {
        NotificationEntity notification = repository
                .findByIdAndRecipientSubject(notificationId, requiredSubject(recipientSubject))
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));
        notification.markRead(Instant.now(clock));
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead(String recipientSubject) {
        repository.markAllUnreadAsRead(requiredSubject(recipientSubject), Instant.now(clock));
    }

    private static String requiredSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Authenticated JWT subject is required");
        }
        return subject;
    }

    private static NotificationResponse toResponse(NotificationEntity notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getChannel(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getActionUrl(),
                notification.getEntityType(),
                notification.getEntityId(),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}

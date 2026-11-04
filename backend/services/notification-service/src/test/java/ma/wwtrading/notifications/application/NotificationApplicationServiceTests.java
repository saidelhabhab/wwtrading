package ma.wwtrading.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationEntity;
import ma.wwtrading.notifications.domain.NotificationRepository;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.domain.NotificationEmailDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NotificationApplicationServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationEmailDeliveryRepository emailDeliveryRepository;

    private NotificationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationApplicationService(repository, emailDeliveryRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNotificationPersistsPendingEmailDeliveryWhenChannelIsEmail() {
        CreateNotificationCommand cmd = new CreateNotificationCommand(
                "user1", "user1@example.com", NotificationType.QUOTATION_CREATED, NotificationChannel.EMAIL,
                "Title", "Message", "/quotes/123", "QUOTATION", "123", null, null);

        NotificationEntity entity = new NotificationEntity(
                "user1", "user1@example.com", NotificationType.QUOTATION_CREATED, NotificationChannel.EMAIL,
                "Title", "Message", "/quotes/123", "QUOTATION", "123", null, null);

        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenReturn(entity);

        var response = service.createNotification(cmd);

        assertThat(response.title()).isEqualTo("Title");
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(emailDeliveryRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createNotificationDoesNotDispatchEmailWhenChannelIsInApp() {
        CreateNotificationCommand cmd = new CreateNotificationCommand(
                "user1", "user1@example.com", NotificationType.SYSTEM, NotificationChannel.IN_APP,
                "Title", "Message", null, null, null, null, null);

        NotificationEntity entity = new NotificationEntity(
                "user1", "user1@example.com", NotificationType.SYSTEM, NotificationChannel.IN_APP,
                "Title", "Message", null, null, null, null, null);

        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenReturn(entity);

        service.createNotification(cmd);

        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verify(emailDeliveryRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createNotificationRollsBackWhenDeliveryRecordCannotBePersisted() {
        CreateNotificationCommand cmd = new CreateNotificationCommand(
                "user1", "user1@example.com", NotificationType.QUOTATION_CREATED, NotificationChannel.BOTH,
                "Title", "Message", null, null, null, null, null);

        NotificationEntity entity = new NotificationEntity(
                "user1", "user1@example.com", NotificationType.QUOTATION_CREATED, NotificationChannel.BOTH,
                "Title", "Message", null, null, null, null, null);

        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenReturn(entity);
        org.mockito.Mockito.doThrow(new RuntimeException("database unavailable"))
                .when(emailDeliveryRepository).save(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.createNotification(cmd))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void listsOnlyTheAuthenticatedSubjectsNotificationsUsingRequestedPage() {
        PageRequest page = PageRequest.of(1, 10);
        when(repository.findByRecipientSubject("client-a", page)).thenReturn(new PageImpl<>(java.util.List.of()));

        assertThat(service.listOwn("client-a", page).getContent()).isEmpty();

        verify(repository).findByRecipientSubject("client-a", page);
    }

    @Test
    void countsUnreadNotificationsInTheDatabaseForOnlyTheOwner() {
        when(repository.countByRecipientSubjectAndReadAtIsNull("client-a")).thenReturn(3L);

        assertThat(service.unreadCount("client-a").unreadCount()).isEqualTo(3L);
        verify(repository).countByRecipientSubjectAndReadAtIsNull("client-a");
    }

    @Test
    void marksOnlyAnOwnershipScopedNotificationAsRead() {
        NotificationEntity notification = notification("client-a", "Own notification");
        when(repository.findByIdAndRecipientSubject(notification.getId(), "client-a"))
                .thenReturn(Optional.of(notification));

        var response = service.markRead("client-a", notification.getId());

        assertThat(response.readAt()).isEqualTo(NOW);
        verify(repository).findByIdAndRecipientSubject(notification.getId(), "client-a");
    }

    @Test
    void hidesAnotherUsersNotificationBehindNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndRecipientSubject(id, "client-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead("client-a", id))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void marksAllUnreadNotificationsForOnlyTheAuthenticatedSubject() {
        service.markAllRead("client-a");

        verify(repository).markAllUnreadAsRead("client-a", NOW);
    }

    private NotificationEntity notification(String owner, String title) {
        return new NotificationEntity(
                owner, null, NotificationType.SYSTEM, NotificationChannel.IN_APP,
                title, "Message", null, null, null, null, null);
    }
}

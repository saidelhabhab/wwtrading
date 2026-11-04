package ma.wwtrading.notifications.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import ma.wwtrading.notifications.domain.EmailDeliveryStatus;
import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationEmailDeliveryEntity;
import ma.wwtrading.notifications.domain.NotificationEmailDeliveryRepository;
import ma.wwtrading.notifications.domain.NotificationEntity;
import ma.wwtrading.notifications.domain.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEmailDeliveryProcessorTests {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
    @Mock NotificationEmailDeliveryRepository repository;
    @Mock NotificationEmailService emailService;
    private NotificationEmailDeliveryProcessor processor;

    @BeforeEach void setUp() {
        processor = new NotificationEmailDeliveryProcessor(repository, emailService,
            Clock.fixed(NOW, ZoneOffset.UTC), 3, 10, Duration.ofSeconds(30));
    }

    @Test void pendingDeliveryBecomesSent() {
        var delivery = delivery();
        when(repository.lockDueDeliveries(NOW, 10)).thenReturn(List.of(delivery));
        processor.dispatchDueEmails();
        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        verify(emailService).sendNotificationEmail(delivery.getNotification());
    }

    @Test void smtpFailureIsPersistedAndCanBeRetried() {
        var delivery = delivery();
        when(repository.lockDueDeliveries(NOW, 10)).thenReturn(List.of(delivery));
        doThrow(new NotificationEmailException("SMTP delivery failed", new RuntimeException()))
            .doNothing().when(emailService).sendNotificationEmail(delivery.getNotification());

        processor.dispatchDueEmails();
        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(delivery.getLastError()).contains("SMTP delivery failed");

        Instant retryAt = NOW.plusSeconds(30);
        reset(repository);
        when(repository.lockDueDeliveries(retryAt, 10)).thenReturn(List.of(delivery));
        var retryProcessor = new NotificationEmailDeliveryProcessor(repository, emailService,
            Clock.fixed(retryAt, ZoneOffset.UTC), 3, 10, Duration.ofSeconds(30));
        retryProcessor.dispatchDueEmails();
        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
    }

    @Test void repeatedFailureReachesDeadLetter() {
        var delivery = delivery();
        delivery.beginAttempt(NOW.minusSeconds(60));
        delivery.markFailed(NOW.minusSeconds(60), 3, Duration.ZERO, "failure one");
        delivery.beginAttempt(NOW.minusSeconds(30));
        delivery.markFailed(NOW.minusSeconds(30), 3, Duration.ZERO, "failure two");
        when(repository.lockDueDeliveries(NOW, 10)).thenReturn(List.of(delivery));
        doThrow(new NotificationEmailException("final failure", new RuntimeException()))
            .when(emailService).sendNotificationEmail(delivery.getNotification());
        processor.dispatchDueEmails();
        assertThat(delivery.getStatus()).isEqualTo(EmailDeliveryStatus.DEAD_LETTER);
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
    }

    private NotificationEmailDeliveryEntity delivery() {
        var notification = new NotificationEntity("client-a", "client@example.com",
            NotificationType.SYSTEM, NotificationChannel.BOTH, "Title", "Message",
            null, null, null, null, null);
        return new NotificationEmailDeliveryEntity(notification, NOW);
    }
}

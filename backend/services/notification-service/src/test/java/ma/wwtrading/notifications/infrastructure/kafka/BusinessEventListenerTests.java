package ma.wwtrading.notifications.infrastructure.kafka;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.notifications.application.CreateNotificationCommand;
import ma.wwtrading.notifications.application.NotificationApplicationService;
import ma.wwtrading.notifications.application.NotificationPolicy;
import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.domain.ProcessedEventRepository;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient.ClientRecipient;

@ExtendWith(MockitoExtension.class)
class BusinessEventListenerTests {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private UserServiceClient users;

    private BusinessEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        listener = new BusinessEventListener(processedEventRepository, notificationService,
                new NotificationPolicy(), users);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private DomainEvent<EventPayloads.SupportMessageCreated> supportMessageEvent(UUID eventId, String recipientSubject) {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        return new DomainEvent<>(eventId, EventTypes.SUPPORT_MESSAGE_CREATED, EventTypes.VERSION_1, Instant.now(),
                null, "support-service", "SUPPORT_CONVERSATION", conversationId.toString(), "client-subject",
                new EventPayloads.SupportMessageCreated(conversationId, messageId, "client-subject",
                        recipientSubject, "CLIENT"));
    }

    private ClientRecipient staff(String subject, String role) {
        return new ClientRecipient(subject, "First", "Last", subject + "@wwtrading.ma", null, true, Set.of(role));
    }

    @Test
    void supportMessageCreatedIsDeliveredIndependentlyToAdminManagerAndCommercialWithRoleSafeUrls() throws Exception {
        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);
        when(users.getRecipients(Set.of("ADMIN", "MANAGER", "COMMERCIAL"), null)).thenReturn(List.of(
                staff("admin-subject", "ADMIN"),
                staff("manager-subject", "MANAGER"),
                staff("commercial-subject", "COMMERCIAL")));

        listener.onEvent(objectMapper.writeValueAsString(supportMessageEvent(UUID.randomUUID(), null)));

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService, times(3)).createNotification(captor.capture());
        List<CreateNotificationCommand> commands = captor.getAllValues();

        assertThat(commands).extracting(CreateNotificationCommand::recipientSubject)
                .containsExactlyInAnyOrder("admin-subject", "manager-subject", "commercial-subject");
        assertThat(commands).extracting(CreateNotificationCommand::type)
                .containsOnly(NotificationType.SUPPORT_MESSAGE);
        assertThat(commands).extracting(CreateNotificationCommand::channel)
                .containsOnly(NotificationChannel.IN_APP);

        assertThat(commands.get(0).actionUrl())
                .isEqualTo("/admin/support?conversation=" + commands.get(0).entityId());
        assertThat(commands.get(1).actionUrl())
                .isEqualTo("/manager/support?conversation=" + commands.get(1).entityId());
        assertThat(commands.get(2).actionUrl())
                .isEqualTo("/commercial/support?conversation=" + commands.get(2).entityId());

        // Never targeted at the sender's own subject and never at client/technician routes
        assertThat(commands).extracting(CreateNotificationCommand::recipientSubject)
                .doesNotContain("client-subject");
        assertThat(commands).extracting(CreateNotificationCommand::actionUrl)
                .allSatisfy(url -> assertThat(url).doesNotContain("/client/", "/technician/"));
    }

    @Test
    void supportMessageCreatedIsDeliveredToAllStaffRolesEvenWhenConversationAlreadyAssigned() throws Exception {
        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);
        when(users.getRecipients(Set.of("ADMIN", "MANAGER", "COMMERCIAL"), null)).thenReturn(List.of(
                staff("admin-subject", "ADMIN"),
                staff("manager-subject", "MANAGER"),
                staff("commercial-subject", "COMMERCIAL")));

        listener.onEvent(objectMapper.writeValueAsString(supportMessageEvent(UUID.randomUUID(), "commercial-subject")));

        verify(notificationService, times(3)).createNotification(any(CreateNotificationCommand.class));
        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService, times(3)).createNotification(captor.capture());
        assertThat(captor.getAllValues()).extracting(CreateNotificationCommand::recipientSubject)
                .containsExactlyInAnyOrder("admin-subject", "manager-subject", "commercial-subject");
    }

    @Test
    void duplicateSupportMessageEventProcessingDoesNotCreateDuplicates() throws Exception {
        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1).thenReturn(0);
        when(users.getRecipients(Set.of("ADMIN", "MANAGER", "COMMERCIAL"), null)).thenReturn(List.of(
                staff("admin-subject", "ADMIN"),
                staff("manager-subject", "MANAGER"),
                staff("commercial-subject", "COMMERCIAL")));

        String json = objectMapper.writeValueAsString(supportMessageEvent(UUID.randomUUID(), null));
        listener.onEvent(json);
        listener.onEvent(json);

        verify(notificationService, times(3)).createNotification(any(CreateNotificationCommand.class));
    }

    @Test
    void supportMessageEventWithoutAnyAuthorizedStaffRecipientFailsLoudly() {
        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);
        when(users.getRecipients(Set.of("ADMIN", "MANAGER", "COMMERCIAL"), null)).thenReturn(List.of());

        UUID eventId = UUID.randomUUID();
        DomainEvent<EventPayloads.SupportMessageCreated> event = supportMessageEvent(eventId, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> listener.onEvent(objectMapper.valueToTree(event).toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Central notification event processing failed");

        verify(notificationService, never()).createNotification(any(CreateNotificationCommand.class));
    }

    @Test
    void supportUrlRejectsUnauthorizedRolesAndInvalidIds() {
        UUID id = UUID.randomUUID();
        assertThat(NotificationPolicy.supportUrl("ADMIN", id)).isEqualTo("/admin/support?conversation=" + id);
        assertThat(NotificationPolicy.supportUrl("MANAGER", id)).isEqualTo("/manager/support?conversation=" + id);
        assertThat(NotificationPolicy.supportUrl("COMMERCIAL", id)).isEqualTo("/commercial/support?conversation=" + id);
        assertThat(NotificationPolicy.supportUrl("commercial", id)).startsWith("/commercial/support?conversation=");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> NotificationPolicy.supportUrl("CLIENT", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> NotificationPolicy.supportUrl("TECHNICIAN", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

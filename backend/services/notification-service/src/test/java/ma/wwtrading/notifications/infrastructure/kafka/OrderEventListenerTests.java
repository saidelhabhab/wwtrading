package ma.wwtrading.notifications.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.notifications.application.CreateNotificationCommand;
import ma.wwtrading.notifications.application.NotificationApplicationService;
import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.domain.ProcessedEventRepository;
import ma.wwtrading.notifications.application.NotificationPolicy;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTests {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private UserServiceClient users;

    private OrderEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        listener = new OrderEventListener(processedEventRepository, notificationService, users, new NotificationPolicy());
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private DomainEvent<EventPayloads.OrderCreated> orderCreatedEvent(UUID eventId) {
        UUID orderId = UUID.randomUUID();
        return new DomainEvent<>(
                eventId,
                EventTypes.ORDER_CREATED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                orderId.toString(),
                null,
                new EventPayloads.OrderCreated(orderId, "client-sub-1", "DRAFT", "UNPAID", new BigDecimal("2499.00"))
        );
    }

    @Test
    void processesOrderCreatedEventAndCreatesNotificationForCustomer() throws Exception {
        UUID eventId = UUID.randomUUID();
        String json = objectMapper.writeValueAsString(orderCreatedEvent(eventId));

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);
        clientRecipient();

        listener.onOrderEvent(json);

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().recipientSubject()).isEqualTo("client-sub-1");
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.ORDER_CREATED);
        assertThat(captor.getValue().channel()).isEqualTo(NotificationChannel.BOTH);
        assertThat(captor.getValue().entityType()).isEqualTo("ORDER");
        assertThat(captor.getValue().sourceEventId()).isEqualTo(eventId);
        verify(processedEventRepository).claim(any(), any(), any());
    }

    @Test
    void processesOrderStatusChangedEventAndCreatesNotificationForCustomer() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DomainEvent<EventPayloads.OrderStatusChanged> event = new DomainEvent<>(
                eventId,
                EventTypes.ORDER_STATUS_CHANGED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                orderId.toString(),
                null,
                new EventPayloads.OrderStatusChanged(orderId, "client-sub-1", "DRAFT", "CONFIRMED")
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);
        clientRecipient();

        listener.onOrderEvent(json);

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().recipientSubject()).isEqualTo("client-sub-1");
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.ORDER_STATUS_CHANGED);
        assertThat(captor.getValue().message()).contains("Confirmée");
        assertThat(captor.getValue().sourceEventId()).isEqualTo(eventId);
    }

    @Test
    void processesPaymentStatusChangedEventAndCreatesNotificationForCustomer() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DomainEvent<EventPayloads.PaymentStatusChanged> event = new DomainEvent<>(
                eventId,
                EventTypes.PAYMENT_STATUS_CHANGED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                orderId.toString(),
                null,
                new EventPayloads.PaymentStatusChanged(orderId, "client-sub-1", "UNPAID", "PAID")
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);
        clientRecipient();

        listener.onOrderEvent(json);

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService).createNotification(captor.capture());
        assertThat(captor.getValue().recipientSubject()).isEqualTo("client-sub-1");
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.PAYMENT_STATUS_CHANGED);
        assertThat(captor.getValue().message()).contains("Paiement confirmé");
        assertThat(captor.getValue().sourceEventId()).isEqualTo(eventId);
    }

    @Test
    void skipsDuplicateEventIdWhenAlreadyProcessed() throws Exception {
        String json = objectMapper.writeValueAsString(orderCreatedEvent(UUID.randomUUID()));

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(0);

        listener.onOrderEvent(json);

        verify(notificationService, never()).createNotification(any(CreateNotificationCommand.class));
        verify(processedEventRepository).claim(any(), any(), any());
    }

    @Test
    void skipsPayloadWithoutCustomerSubject() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        DomainEvent<EventPayloads.OrderCreated> event = new DomainEvent<>(
                eventId,
                EventTypes.ORDER_CREATED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                orderId.toString(),
                null,
                new EventPayloads.OrderCreated(orderId, null, "DRAFT", "UNPAID", new BigDecimal("10.00"))
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);

        listener.onOrderEvent(json);

        verify(notificationService, never()).createNotification(any(CreateNotificationCommand.class));
    }

    @Test
    void skipsUnhandledEventTypeWithoutNotification() throws Exception {
        UUID eventId = UUID.randomUUID();
        DomainEvent<EventPayloads.OrderCreated> event = new DomainEvent<>(
                eventId,
                "SOMETHING_ELSE",
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                UUID.randomUUID().toString(),
                null,
                new EventPayloads.OrderCreated(UUID.randomUUID(), "client-sub-1", "DRAFT", "UNPAID", BigDecimal.ONE)
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);

        listener.onOrderEvent(json);

        verify(notificationService, never()).createNotification(any(CreateNotificationCommand.class));
    }

    @Test
    void humanLabelMapsKnownStatusesAndKeepsUnknownOnes() {
        assertThat(OrderEventListener.humanLabel("DRAFT")).isEqualTo("Brouillon");
        assertThat(OrderEventListener.humanLabel("CONFIRMED")).isEqualTo("Confirmée");
        assertThat(OrderEventListener.humanLabel("PROCESSING")).isEqualTo("En préparation");
        assertThat(OrderEventListener.humanLabel("READY")).isEqualTo("Prête");
        assertThat(OrderEventListener.humanLabel("COMPLETED")).isEqualTo("Terminée");
        assertThat(OrderEventListener.humanLabel("CANCELLED")).isEqualTo("Annulée");
        assertThat(OrderEventListener.humanLabel("UNPAID")).isEqualTo("En attente de paiement");
        assertThat(OrderEventListener.humanLabel("PAID")).isEqualTo("Paiement confirmé");
        assertThat(OrderEventListener.humanLabel("REFUNDED")).isEqualTo("Remboursée");
        assertThat(OrderEventListener.humanLabel("UNKNOWN")).isEqualTo("UNKNOWN");
        assertThat(OrderEventListener.humanLabel(null)).isEmpty();
    }

    @Test
    void processesGuestOrderCreatedEventWithRichEmailAndWhatsAppDetails() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        EventPayloads.OrderCreated guestPayload = new EventPayloads.OrderCreated(
            orderId,
            null,
            "DRAFT",
            "UNPAID",
            new BigDecimal("10300.00"),
            "WWT-20261026-0001",
            true,
            "karim.bennani@example.ma",
            "Karim Bennani",
            "0661234567",
            Instant.now(),
            new BigDecimal("10000.00"),
            BigDecimal.ZERO,
            new BigDecimal("300.00"),
            "DELIVERY",
            "12 Avenue Mohammed V, Kénitra",
            "BANK_TRANSFER",
            "FULL_BANK_TRANSFER",
            new BigDecimal("10300.00"),
            BigDecimal.ZERO,
            "CIH Bank",
            "MA640001111122223333444455",
            "WWTRADING SARL",
            "http://localhost:4201/suivi-commande?token=abc123token",
            java.util.List.of(
                new EventPayloads.OrderLineItem("REF-G1", "Échographe Portable", 2, new BigDecimal("5000.00"), new BigDecimal("10000.00"))
            )
        );
        DomainEvent<EventPayloads.OrderCreated> event = new DomainEvent<>(
            eventId,
            EventTypes.ORDER_CREATED,
            EventTypes.VERSION_1,
            Instant.now(),
            null,
            "order-service",
            "ORDER",
            orderId.toString(),
            null,
            guestPayload
        );
        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);

        listener.onOrderEvent(json);

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationService).createNotification(captor.capture());
        CreateNotificationCommand cmd = captor.getValue();
        assertThat(cmd.recipientSubject()).isEqualTo("guest:" + orderId);
        assertThat(cmd.recipientEmail()).isEqualTo("karim.bennani@example.ma");
        assertThat(cmd.recipientPhone()).isEqualTo("0661234567");
        assertThat(cmd.channel()).isEqualTo(NotificationChannel.ALL);
        assertThat(cmd.title()).contains("WWT-20261026-0001");
        assertThat(cmd.message()).contains("Échographe Portable");
        assertThat(cmd.message()).contains("MA640001111122223333444455");
        assertThat(cmd.actionUrl()).isEqualTo("http://localhost:4201/suivi-commande?token=abc123token");
    }

    private void clientRecipient() {
        when(users.findEnabledClient("client-sub-1")).thenReturn(Optional.of(
                new UserServiceClient.ClientRecipient("client-sub-1", "Client", "Test",
                        "client@example.test", null, true, Set.of("CLIENT"))));
    }
}

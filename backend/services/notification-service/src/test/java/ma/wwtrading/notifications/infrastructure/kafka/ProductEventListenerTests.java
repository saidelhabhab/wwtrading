package ma.wwtrading.notifications.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.notifications.application.CreateNotificationCommand;
import ma.wwtrading.notifications.application.NotificationApplicationService;
import ma.wwtrading.notifications.domain.ProcessedEventRepository;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient;

@ExtendWith(MockitoExtension.class)
class ProductEventListenerTests {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationApplicationService notificationService;

    @Mock
    private UserServiceClient userServiceClient;

    private ProductEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        listener = new ProductEventListener(processedEventRepository, notificationService, userServiceClient);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void processesProductCreatedEventAndCreatesNotificationForClients() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        DomainEvent<EventPayloads.ProductCreated> event = new DomainEvent<>(
                eventId,
                EventTypes.PRODUCT_CREATED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "product-service",
                "PRODUCT",
                productId.toString(),
                null,
                new EventPayloads.ProductCreated(productId, "KM-C250i", "Konica Minolta C250i", "Konica Minolta", "C250i", "PRINTER")
        );

        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(1);
        when(userServiceClient.getClientRecipients()).thenReturn(List.of(
                new UserServiceClient.ClientRecipient("client-sub-1", "Client", "One", "client1@example.com", "0600000001", true, java.util.Set.of("CLIENT"))
        ));

        listener.onProductEvent(json);

        verify(notificationService).createNotification(any(CreateNotificationCommand.class));
        verify(processedEventRepository).claim(any(), any(), any());
    }

    @Test
    void skipsDuplicateEventIdWhenAlreadyProcessed() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        DomainEvent<EventPayloads.ProductCreated> event = new DomainEvent<>(
                eventId,
                EventTypes.PRODUCT_CREATED,
                EventTypes.VERSION_1,
                Instant.now(),
                null,
                "product-service",
                "PRODUCT",
                productId.toString(),
                null,
                new EventPayloads.ProductCreated(productId, "KM-C250i", "Konica Minolta C250i", "Konica Minolta", "C250i", "PRINTER")
        );

        String json = objectMapper.writeValueAsString(event);

        when(processedEventRepository.claim(any(), any(), any())).thenReturn(0);

        listener.onProductEvent(json);

        verify(notificationService, never()).createNotification(any(CreateNotificationCommand.class));
        verify(processedEventRepository).claim(any(), any(), any());
    }
}

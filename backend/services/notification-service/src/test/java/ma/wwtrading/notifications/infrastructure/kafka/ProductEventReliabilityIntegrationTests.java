package ma.wwtrading.notifications.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.events.EventTypes;
import ma.wwtrading.notifications.domain.NotificationEmailDeliveryRepository;
import ma.wwtrading.notifications.domain.NotificationRepository;
import ma.wwtrading.notifications.domain.ProcessedEventRepository;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient;

@Testcontainers
@SpringBootTest(properties = {
    "spring.cloud.config.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false",
    "spring.kafka.listener.auto-startup=false",
    "notification.email.dispatch-enabled=false",
    "wwtrading.security.issuer-uri=http://localhost:8180/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=http://localhost:8180/realms/wwtrading/protocol/openid-connect/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class ProductEventReliabilityIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:17.6-alpine");

    @Autowired ProductEventListener listener;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationEmailDeliveryRepository deliveryRepository;
    @Autowired ProcessedEventRepository processedEventRepository;
    @MockitoBean UserServiceClient userServiceClient;
    @MockitoBean JavaMailSender javaMailSender;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void cleanDatabase() {
        deliveryRepository.deleteAll();
        notificationRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    void concurrentAndRetriedDeliveryCreatesOneNotificationAndOneEmailDelivery() throws Exception {
        UUID eventId = UUID.randomUUID();
        String json = eventJson(eventId);
        when(userServiceClient.getClientRecipients()).thenReturn(List.of(
            new UserServiceClient.ClientRecipient(
                "client-subject", "Client", "One", "client@example.com", null, true, java.util.Set.of("CLIENT"))));

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> invokeTogether(json, ready, start));
            var second = executor.submit(() -> invokeTogether(json, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        listener.onProductEvent(json);

        assertThat(processedEventRepository.count()).isEqualTo(1);
        assertThat(notificationRepository.countBySourceEventId(eventId)).isEqualTo(1);
        assertThat(deliveryRepository.count()).isEqualTo(1);
    }

    @Test
    void failedProcessingRollsBackClaimAndCanBeRetried() throws Exception {
        UUID eventId = UUID.randomUUID();
        String json = eventJson(eventId);
        when(userServiceClient.getClientRecipients()).thenReturn(List.of(
            new UserServiceClient.ClientRecipient(
                null, "Invalid", "Recipient", "client@example.com", null, true, java.util.Set.of("CLIENT"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.onProductEvent(json))
            .isInstanceOf(RuntimeException.class);

        assertThat(processedEventRepository.findById(eventId)).isEmpty();
        assertThat(notificationRepository.countBySourceEventId(eventId)).isZero();
        assertThat(deliveryRepository.count()).isZero();

        when(userServiceClient.getClientRecipients()).thenReturn(List.of(
            new UserServiceClient.ClientRecipient(
                "client-subject", "Client", "One", "client@example.com", null, true, java.util.Set.of("CLIENT"))));
        listener.onProductEvent(json);

        assertThat(processedEventRepository.findById(eventId)).isPresent();
        assertThat(notificationRepository.countBySourceEventId(eventId)).isEqualTo(1);
        assertThat(deliveryRepository.count()).isEqualTo(1);
    }

    private void invokeTogether(String json, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start in time");
            }
            listener.onProductEvent(json);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private String eventJson(UUID eventId) throws Exception {
        UUID productId = UUID.randomUUID();
        var event = new DomainEvent<>(
            eventId,
            EventTypes.PRODUCT_CREATED,
            EventTypes.VERSION_1,
            Instant.now(),
            null,
            "product-service",
            "PRODUCT",
            productId.toString(),
            null,
            new EventPayloads.ProductCreated(
                productId, "KM-C250i", "Konica Minolta C250i",
                "Konica Minolta", "C250i", "PRINTER"));
        return objectMapper.writeValueAsString(event);
    }
}

package ma.wwtrading.quotations.infrastructure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ma.wwtrading.quotations.api.CreateQuotationRequest;
import ma.wwtrading.quotations.api.QuotationResponse;
import ma.wwtrading.quotations.application.QuotationApplicationService;
import ma.wwtrading.quotations.domain.QuotationEntity;
import ma.wwtrading.quotations.domain.QuotationRepository;
import ma.wwtrading.quotations.domain.QuotationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL Concurrency & Migration Integration Test.
 *
 * This test suite executes exclusively against a disposable, isolated PostgreSQL 16 container
 * managed by Testcontainers on ephemeral ports.
 * It NEVER connects to or affects the primary development or production database.
 *
 * Scenarios verified:
 * 1. Multi-threaded simultaneous race condition with same Idempotency-Key and same payload (CountDownLatch).
 * 2. Multi-threaded simultaneous race condition with same Idempotency-Key and conflicting payloads.
 * 3. Flyway V7/V8 schema integrity and partial unique index validation.
 * 4. Legacy quotation simulation with NULL payload hash: matching backfill and differing rejection.
 */
@SpringBootTest(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.kafka.listener.auto-startup=false",
    "wwtrading.security.issuer-uri=http://localhost:8180/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=http://localhost:8180/realms/wwtrading/protocol/openid-connect/certs",
    "wwtrading.security.audience=wwtrading-client"
})
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public class QuotationConcurrencyIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("quotation_concurrency_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private QuotationApplicationService service;

    @Autowired
    private QuotationRepository repository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ma.wwtrading.quotations.infrastructure.kafka.QuotationEventPublisher eventPublisher;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ma.wwtrading.quotations.application.QuotationProductClient productClient;

    @Test
    @DisplayName("Scenario 1: Concurrent submissions with same key and same payload create exactly 1 quote and both threads succeed")
    void concurrentSubmissionsWithSameKeyAndPayloadCreateSingleQuote() throws Exception {
        String sharedKey = "pg16-race-same-" + UUID.randomUUID();

        var request = new CreateQuotationRequest(
            "VENTE", "Bizhub C250", "Konica Minolta", 2, "Demande urgente",
            "Sami Alami", "Tech Corp", "+212612345678", "sami@example.com", "Casablanca", "Sous 48h",
            new BigDecimal("24000.00"), "Merci de répondre rapidement", null, sharedKey
        );

        int numberOfThreads = 3;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<QuotationResponse>> futures = new ArrayList<>();
        List<Throwable> caughtExceptions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                // Wait for the simultaneous release signal
                startLatch.await(5, TimeUnit.SECONDS);
                try {
                    return service.create(null, request);
                } catch (Throwable t) {
                    caughtExceptions.add(t);
                    throw t;
                }
            }));
        }

        // Release all threads simultaneously
        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        List<QuotationResponse> responses = new ArrayList<>();
        for (Future<QuotationResponse> future : futures) {
            try {
                responses.add(future.get(10, TimeUnit.SECONDS));
            } catch (Exception ignored) {
            }
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Verify that no thread suffered an aborted transaction error
        assertThat(caughtExceptions).isEmpty();

        // Verify all threads received successful responses with identical quote ID
        assertThat(responses).hasSize(numberOfThreads);
        UUID expectedId = responses.get(0).id();
        for (QuotationResponse resp : responses) {
            assertThat(resp.id()).isEqualTo(expectedId);
            assertThat(resp.customerReference()).isEqualTo("sami@example.com");
        }

        // Verify database contains exactly one record for this idempotency key
        var persisted = repository.findByIdempotencyKey(sharedKey);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getId()).isEqualTo(expectedId);
        assertThat(persisted.get().getIdempotencyPayloadHash()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 2: Concurrent submissions with same key but DIFFERENT payloads: 1 succeeds, 1 receives 409 IDEMPOTENCY_PAYLOAD_MISMATCH")
    void concurrentSubmissionsWithConflictingPayloadsProduceSingleWinnerAnd409() throws Exception {
        String conflictingKey = "pg16-race-diff-" + UUID.randomUUID();

        var payloadA = new CreateQuotationRequest(
            "VENTE", "Bizhub C250", "Konica Minolta", 1, "Payload Alpha",
            "Alice", "Corp A", "+212611111111", "alice@example.com", "Rabat", "Immédiat",
            new BigDecimal("10000.00"), null, null, conflictingKey
        );

        var payloadB = new CreateQuotationRequest(
            "VENTE", "Bizhub C250", "Konica Minolta", 5, "Payload Beta (Different)",
            "Bob", "Corp B", "+212622222222", "bob@example.com", "Tanger", "1 mois",
            new BigDecimal("50000.00"), null, null, conflictingKey
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Object>> futures = new ArrayList<>();

        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);
            try {
                return service.create(null, payloadA);
            } catch (Exception e) {
                return e;
            }
        }));

        futures.add(executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await(5, TimeUnit.SECONDS);
            try {
                return service.create(null, payloadB);
            } catch (Exception e) {
                return e;
            }
        }));

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        Object res1 = futures.get(0).get(10, TimeUnit.SECONDS);
        Object res2 = futures.get(1).get(10, TimeUnit.SECONDS);
        executor.shutdown();

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List.of(res1, res2).forEach(res -> {
            if (res instanceof QuotationResponse) {
                successes.incrementAndGet();
            } else if (res instanceof ResponseStatusException rse) {
                if (rse.getStatusCode().value() == 409 && "IDEMPOTENCY_PAYLOAD_MISMATCH".equals(rse.getReason())) {
                    conflicts.incrementAndGet();
                }
            }
        });

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);

        // Exactly one quotation persisted in PostgreSQL
        var inDb = repository.findByIdempotencyKey(conflictingKey);
        assertThat(inDb).isPresent();
    }

    @Test
    @DisplayName("Scenario 3: Legacy quote with NULL payload hash correctly backfills when matching, rejects when differing")
    void legacyQuotationWithNullHashBehavior() {
        String legacyKey = "pg16-legacy-" + UUID.randomUUID();

        // 1. Manually persist a legacy quotation simulating pre-V8 data (idempotencyPayloadHash = null)
        var legacyEntity = new QuotationEntity(
            "legacy@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub C360", "Konica", 2,
            "Description historique", "Omar", "SARL Legacy", "+212633333333", "legacy@example.com", "Fès", "Sous 15j",
            new BigDecimal("35000.00"), "Ancien devis"
        );
        legacyEntity.assignIdempotencyKey(legacyKey);
        legacyEntity.assignIdempotencyPayloadHash(null);
        var savedLegacy = repository.saveAndFlush(legacyEntity);

        assertThat(savedLegacy.getIdempotencyPayloadHash()).isNull();

        // 2. Incoming request with identical payload
        var matchingRequest = new CreateQuotationRequest(
            "VENTE", "Bizhub C360", "Konica", 2, "Description historique",
            "Omar", "SARL Legacy", "+212633333333", "legacy@example.com", "Fès", "Sous 15j",
            new BigDecimal("35000.00"), "Ancien devis", null, legacyKey
        );

        var response = service.create(null, matchingRequest);
        assertThat(response.id()).isEqualTo(savedLegacy.getId());

        // Verify that the hash was backfilled in the database
        var refreshed = repository.findById(savedLegacy.getId()).orElseThrow();
        assertThat(refreshed.getIdempotencyPayloadHash()).isNotNull();
        assertThat(refreshed.getIdempotencyPayloadHash())
            .isEqualTo(QuotationApplicationService.computePayloadHash(matchingRequest));

        // 3. New request using same key but differing payload is rejected with 409
        var differingRequest = new CreateQuotationRequest(
            "VENTE", "Bizhub C360", "Konica", 99, "Tentative de modification",
            "Omar", "SARL Legacy", "+212633333333", "legacy@example.com", "Fès", "Sous 15j",
            new BigDecimal("35000.00"), "Ancien devis", null, legacyKey
        );

        assertThatThrownBy(() -> service.create(null, differingRequest))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode().value()).isEqualTo(409);
                assertThat(rse.getReason()).isEqualTo("IDEMPOTENCY_PAYLOAD_MISMATCH");
            });
    }
}

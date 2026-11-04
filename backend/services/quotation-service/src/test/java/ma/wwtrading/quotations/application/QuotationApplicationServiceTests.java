package ma.wwtrading.quotations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.quotations.api.CreateQuotationRequest;
import ma.wwtrading.quotations.api.UpdateQuotationStatusRequest;
import ma.wwtrading.quotations.domain.QuotationEntity;
import ma.wwtrading.quotations.domain.QuotationRepository;
import ma.wwtrading.quotations.domain.QuotationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class QuotationApplicationServiceTests {

    private final QuotationRepository repository = mock(QuotationRepository.class);
    private final QuotationProductClient productClient = mock(QuotationProductClient.class);
    private final QuotationApplicationService service = new QuotationApplicationService(
        repository,
        Mappers.getMapper(QuotationMapper.class)
    );

    @BeforeEach
    void setUp() {
        service.setProductClient(productClient);
    }

    private JwtAuthenticationToken mockJwt(String email, String role) {
        return mockJwt(email, role, true, "sub-12345");
    }

    private JwtAuthenticationToken mockJwt(String email, String role, boolean emailVerified) {
        return mockJwt(email, role, emailVerified, "sub-12345");
    }

    private JwtAuthenticationToken mockJwt(String email, String role, boolean emailVerified, String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("email")).thenReturn(email);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaimAsBoolean("email_verified")).thenReturn(emailVerified);
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void createsCatalogQuotationForGuest() {
        when(repository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId)).thenReturn(
            new QuotationProductClient.CatalogProduct(productId, "KM-01", "Bizhub C250i", new BigDecimal("15000.00"), true)
        );

        var request = new CreateQuotationRequest(
            "VENTE", null, null, null, null,
            "Guest User", "ACME", "0612345678", "guest@example.com", "Kénitra",
            "ASAP", null, "Guest request",
            List.of(new CreateQuotationRequest.RequestedLine(productId, 2))
        );

        var response = service.create(null, request);

        assertThat(response.customerReference()).isEqualTo("guest@example.com");
        assertThat(response.status()).isEqualTo(QuotationStatus.PENDING);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).productReference()).isEqualTo("KM-01");
        assertThat(response.lines().get(0).requestedQuantity()).isEqualTo(2);
    }

    @Test
    void createsCatalogQuotationForCommercialOnBehalfOfCustomer() {
        when(repository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId)).thenReturn(
            new QuotationProductClient.CatalogProduct(productId, "KM-01", "Bizhub C250i", new BigDecimal("15000.00"), true)
        );

        var auth = mockJwt("commercial@example.com", "COMMERCIAL");
        var request = new CreateQuotationRequest(
            "VENTE", null, null, null, null,
            "Prospect Client", "Client Corp", "0612345678", "prospect@example.com", "Casablanca",
            "ASAP", null, "Commercial inquiry",
            List.of(new CreateQuotationRequest.RequestedLine(productId, 1))
        );

        var response = service.create(auth, request);

        assertThat(response.customerReference()).isEqualTo("prospect@example.com");
        assertThat(response.status()).isEqualTo(QuotationStatus.PENDING);
        assertThat(response.lines()).hasSize(1);
    }

    @Test
    void createsCatalogQuotationForAdmin() {
        when(repository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId)).thenReturn(
            new QuotationProductClient.CatalogProduct(productId, "KM-02", "Bizhub 367", new BigDecimal("20000.00"), true)
        );

        var auth = mockJwt("admin@example.com", "ADMIN");
        var request = new CreateQuotationRequest(
            "VENTE", null, null, null, null,
            "Admin On Behalf", "Admin Corp", "0612345678", "target@example.com", "Rabat",
            "ASAP", null, "Admin inquiry",
            List.of(new CreateQuotationRequest.RequestedLine(productId, 3))
        );

        var response = service.create(auth, request);

        assertThat(response.customerReference()).isEqualTo("target@example.com");
        assertThat(response.status()).isEqualTo(QuotationStatus.PENDING);
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).requestedQuantity()).isEqualTo(3);
    }

    @Test
    void createsCustomQuotationWithDescription() {
        when(repository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateQuotationRequest(
            "MAINTENANCE", null, null, 1, "Intervention imprimante urgente",
            "Fatima", "Bureau", "0612345678", "fatima@example.com", "Fès",
            "ASAP", null, null
        );

        var response = service.create(null, request);

        assertThat(response.status()).isEqualTo(QuotationStatus.PENDING);
        assertThat(response.serviceType()).isEqualTo("MAINTENANCE");
        assertThat(response.description()).isEqualTo("Intervention imprimante urgente");
    }

    @Test
    void rejectsCustomQuotationWithoutDescription() {
        var request = new CreateQuotationRequest(
            "MAINTENANCE", null, null, 1, "   ",
            "Fatima", "Bureau", "0612345678", "fatima@example.com", "Fès",
            "ASAP", null, null
        );

        assertThatThrownBy(() -> service.create(null, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("A request description is required");
    }

    @Test
    void rejectsEmptyCatalogLinesWhenListProvided() {
        var request = new CreateQuotationRequest(
            "VENTE", null, null, null, null,
            "User", "Company", "0612345678", "user@example.com", "Tanger",
            "ASAP", null, null,
            List.of()
        );

        assertThatThrownBy(() -> service.create(null, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Select at least one product");
    }

    @Test
    void rejectsDuplicateProductLines() {
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId)).thenReturn(
            new QuotationProductClient.CatalogProduct(productId, "KM-01", "Bizhub", new BigDecimal("1000.00"), true)
        );

        var request = new CreateQuotationRequest(
            "VENTE", null, null, null, null,
            "User", "Company", "0612345678", "user@example.com", "Tanger",
            "ASAP", null, null,
            List.of(
                new CreateQuotationRequest.RequestedLine(productId, 1),
                new CreateQuotationRequest.RequestedLine(productId, 2)
            )
        );

        assertThatThrownBy(() -> service.create(null, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Invalid or duplicate product line");
    }

    @Test
    @DisplayName("Returns existing quotation when Idempotency-Key and payload hash match")
    void returnsExistingQuotationByIdempotencyKey() {
        var request = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Desc",
            "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null,
            null, "idemp-unique-123"
        );
        String expectedHash = QuotationApplicationService.computePayloadHash(request);

        var existing = new QuotationEntity(
            "client@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null
        );
        existing.assignIdempotencyKey("idemp-unique-123");
        existing.assignIdempotencyPayloadHash(expectedHash);

        when(repository.findByIdempotencyKey("idemp-unique-123")).thenReturn(Optional.of(existing));

        var response = service.create(null, request);

        assertThat(response.customerReference()).isEqualTo("client@example.com");
        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Rejects request with 409 Conflict and IDEMPOTENCY_PAYLOAD_MISMATCH when key matches but payload differs")
    void rejectsSameIdempotencyKeyWithDifferentPayload() {
        var originalRequest = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Original",
            "Alice", "Corp A", "0612345678", "alice@example.com", "Rabat", null, null, null,
            null, "shared-key-999"
        );
        String originalHash = QuotationApplicationService.computePayloadHash(originalRequest);

        var existing = new QuotationEntity(
            "alice@example.com", QuotationStatus.PENDING, "VENTE", null, null, 1,
            "Original", "Alice", "Corp A", "0612345678", "alice@example.com", "Rabat", null, null, null
        );
        existing.assignIdempotencyKey("shared-key-999");
        existing.assignIdempotencyPayloadHash(originalHash);

        when(repository.findByIdempotencyKey("shared-key-999")).thenReturn(Optional.of(existing));

        // Different user / different content using the exact same Idempotency-Key
        var differentRequest = new CreateQuotationRequest(
            "VENTE", null, null, 5, "Different inquiry",
            "Bob", "Corp B", "0687654321", "bob@example.com", "Casablanca", null, null, null,
            null, "shared-key-999"
        );

        assertThatThrownBy(() -> service.create(null, differentRequest))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode().value()).isEqualTo(409);
                assertThat(rse.getReason()).isEqualTo("IDEMPOTENCY_PAYLOAD_MISMATCH");
            });

        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Legacy quote without hash returns existing quotation if reconstructed payload matches")
    void legacyQuotationWithoutHash_matchesIfReconstructedPayloadMatches() {
        var request = new CreateQuotationRequest(
            "VENTE", null, null, 2, "Description legacy",
            "Nadia", "SARL Tech", "0612345678", "nadia@example.com", "Fès", "ASAP", null, null,
            null, "legacy-key-101"
        );

        // Legacy quote in DB with NULL idempotency_payload_hash
        var legacyEntity = new QuotationEntity(
            "nadia@example.com", QuotationStatus.PENDING, "VENTE", null, null, 2,
            "Description legacy", "Nadia", "SARL Tech", "0612345678", "nadia@example.com", "Fès", "ASAP", null, null
        );
        legacyEntity.assignIdempotencyKey("legacy-key-101");
        // No hash assigned: idempotencyPayloadHash is null!
        assertThat(legacyEntity.getIdempotencyPayloadHash()).isNull();

        when(repository.findByIdempotencyKey("legacy-key-101")).thenReturn(Optional.of(legacyEntity));

        var response = service.create(null, request);

        assertThat(response).isNotNull();
        assertThat(response.customerReference()).isEqualTo("nadia@example.com");
        // Verify that the hash was backfilled on the legacy entity
        assertThat(legacyEntity.getIdempotencyPayloadHash()).isNotNull();
        assertThat(legacyEntity.getIdempotencyPayloadHash()).isEqualTo(QuotationApplicationService.computePayloadHash(request));
    }

    @Test
    @DisplayName("Legacy quote without hash REJECTS with 409 IDEMPOTENCY_PAYLOAD_MISMATCH if payload differs even if email matches")
    void legacyQuotationWithoutHash_rejectsIfPayloadDiffersEvenWithSameEmail() {
        // Legacy quote in DB with NULL idempotency_payload_hash for "nadia@example.com" with quantity=1
        var legacyEntity = new QuotationEntity(
            "nadia@example.com", QuotationStatus.PENDING, "VENTE", null, null, 1,
            "Description legacy", "Nadia", "SARL Tech", "0612345678", "nadia@example.com", "Fès", "ASAP", null, null
        );
        legacyEntity.assignIdempotencyKey("legacy-key-102");
        assertThat(legacyEntity.getIdempotencyPayloadHash()).isNull();

        when(repository.findByIdempotencyKey("legacy-key-102")).thenReturn(Optional.of(legacyEntity));

        // New request using same key and same email, but DIFFERENT quantity (quantity=5)
        var differentRequest = new CreateQuotationRequest(
            "VENTE", null, null, 5, "Description legacy",
            "Nadia", "SARL Tech", "0612345678", "nadia@example.com", "Fès", "ASAP", null, null,
            null, "legacy-key-102"
        );

        assertThatThrownBy(() -> service.create(null, differentRequest))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode().value()).isEqualTo(409);
                assertThat(rse.getReason()).isEqualTo("IDEMPOTENCY_PAYLOAD_MISMATCH");
            });

        verify(repository, never()).save(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Handles concurrent duplicate submission race condition without creating second devis")
    void handlesConcurrentDuplicateSubmissionWithSameKeyGracefully() {
        var request = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Desc",
            "Karim", "SARL", "0612345678", "karim@example.com", "Tanger", null, null, null,
            null, "race-key-456"
        );
        String hash = QuotationApplicationService.computePayloadHash(request);

        // First findByIdempotencyKey during create() sees nothing
        when(repository.findByIdempotencyKey("race-key-456"))
            .thenReturn(Optional.empty()) // Initial check before saveAndFlush
            .thenReturn(Optional.of(new QuotationEntity(
                "karim@example.com", QuotationStatus.PENDING, "VENTE", null, null, 1,
                "Desc", "Karim", "SARL", "0612345678", "karim@example.com", "Tanger", null, null, null
            ) {{
                assignIdempotencyKey("race-key-456");
                assignIdempotencyPayloadHash(hash);
            }})); // Check inside catch(DataIntegrityViolationException)

        // Simulate unique constraint collision in PostgreSQL
        when(repository.saveAndFlush(any(QuotationEntity.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint"));

        var response = service.create(null, request);

        assertThat(response).isNotNull();
        assertThat(response.customerReference()).isEqualTo("karim@example.com");
    }

    @Test
    @DisplayName("Handles concurrent race condition with different payload by rejecting with 409 IDEMPOTENCY_PAYLOAD_MISMATCH")
    void handlesConcurrentRaceConditionWithDifferentPayload() {
        var request = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Desc",
            "Karim", "SARL", "0612345678", "karim@example.com", "Tanger", null, null, null,
            null, "race-key-789"
        );

        when(repository.findByIdempotencyKey("race-key-789"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(new QuotationEntity(
                "other@example.com", QuotationStatus.PENDING, "VENTE", null, null, 99,
                "Other", "Other", "Corp", "0600000000", "other@example.com", "Rabat", null, null, null
            ) {{
                assignIdempotencyKey("race-key-789");
                assignIdempotencyPayloadHash("different-hash-1234567890abcdef1234567890abcdef1234567890abcdef12345678");
            }}));

        when(repository.saveAndFlush(any(QuotationEntity.class)))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> service.create(null, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode().value()).isEqualTo(409);
                assertThat(rse.getReason()).isEqualTo("IDEMPOTENCY_PAYLOAD_MISMATCH");
            });
    }

    @Test
    @DisplayName("SHA-256: Product line order does not affect hash (deterministic catalog lines)")
    void sha256_productLineOrderDoesNotAffectHash() {
        UUID prodA = UUID.randomUUID();
        UUID prodB = UUID.randomUUID();

        var req1 = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Desc",
            "Jean", "ACME", "0612345678", "jean@example.com", "Kénitra", null, null, null,
            List.of(
                new CreateQuotationRequest.RequestedLine(prodA, 2),
                new CreateQuotationRequest.RequestedLine(prodB, 1)
            ),
            "key-1"
        );

        var req2 = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Desc",
            "Jean", "ACME", "0612345678", "jean@example.com", "Kénitra", null, null, null,
            List.of(
                new CreateQuotationRequest.RequestedLine(prodB, 1),
                new CreateQuotationRequest.RequestedLine(prodA, 2)
            ),
            "key-2"
        );

        String hash1 = QuotationApplicationService.computePayloadHash(req1);
        String hash2 = QuotationApplicationService.computePayloadHash(req2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("SHA-256: Normalized data produces identical hash")
    void sha256_normalizedDataProducesSameHash() {
        var req1 = new CreateQuotationRequest(
            "VENTE", "Bizhub C250", "Konica", 2, "Besoin urgent",
            "Jean DUPONT", "ACME Maroc", "+212 6 12 34 56 78", "Jean.Dupont@Example.COM", "Kénitra", "Immédiat",
            new BigDecimal("15000.00"), "Merci", null, "key-alpha"
        );

        var req2 = new CreateQuotationRequest(
            "vente", "  bizhub c250  ", "konica", 2, "besoin urgent",
            "jean dupont", "acme maroc", "+212612345678", "jean.dupont@example.com", "kénitra", "immédiat",
            new BigDecimal("15000.0"), "  merci  ", null, "key-beta"
        );

        String hash1 = QuotationApplicationService.computePayloadHash(req1);
        String hash2 = QuotationApplicationService.computePayloadHash(req2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("SHA-256: Different business fields produce different hash")
    void sha256_differentBusinessFieldsProduceDifferentHash() {
        var baseReq = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Base description",
            "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null,
            null, "key"
        );
        String baseHash = QuotationApplicationService.computePayloadHash(baseReq);

        // Different quantity
        var reqDiffQty = new CreateQuotationRequest(
            "VENTE", null, null, 2, "Base description",
            "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null,
            null, "key"
        );
        assertThat(QuotationApplicationService.computePayloadHash(reqDiffQty)).isNotEqualTo(baseHash);

        // Different service type
        var reqDiffService = new CreateQuotationRequest(
            "MAINTENANCE", null, null, 1, "Base description",
            "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null,
            null, "key"
        );
        assertThat(QuotationApplicationService.computePayloadHash(reqDiffService)).isNotEqualTo(baseHash);

        // Different city
        var reqDiffCity = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Base description",
            "Jean", "ACME", "0612345678", "client@example.com", "Rabat", "ASAP", null, null,
            null, "key"
        );
        assertThat(QuotationApplicationService.computePayloadHash(reqDiffCity)).isNotEqualTo(baseHash);
    }

    @Test
    @DisplayName("SHA-256: Amounts and nulls are treated deterministically")
    void sha256_amountsAndNullsAreDeterministic() {
        var reqWithDecimals = new CreateQuotationRequest(
            "VENTE", null, null, 1, null,
            "Jean", null, null, "client@example.com", null, null,
            new BigDecimal("250.00"), null, null, null
        );

        var reqWithoutDecimals = new CreateQuotationRequest(
            "VENTE", null, null, 1, "",
            "Jean", "", "", "client@example.com", "", "",
            new BigDecimal("250"), "", null, null
        );

        String hash1 = QuotationApplicationService.computePayloadHash(reqWithDecimals);
        String hash2 = QuotationApplicationService.computePayloadHash(reqWithoutDecimals);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("SHA-256: Idempotency-Key itself is excluded from hash calculation")
    void sha256_idempotencyKeyIsNotPartOfHash() {
        var reqA = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Desc",
            "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null,
            null, "idemp-key-AAA"
        );

        var reqB = new CreateQuotationRequest(
            "VENTE", null, null, 1, "Desc",
            "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null,
            null, "idemp-key-BBB"
        );

        assertThat(QuotationApplicationService.computePayloadHash(reqA))
            .isEqualTo(QuotationApplicationService.computePayloadHash(reqB));
    }

    @Test
    @DisplayName("Rapid duplicate submission within 30 seconds returns previously created quotation")
    void rapidDuplicateSubmissionReturnsExisting() {
        UUID productId = UUID.randomUUID();
        when(productClient.getProduct(productId)).thenReturn(
            new QuotationProductClient.CatalogProduct(productId, "KM-01", "Bizhub", new BigDecimal("1000.00"), true)
        );
        when(repository.save(any(QuotationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var request = new CreateQuotationRequest(
            "VENTE", null, null, null, null,
            "Jean", "ACME", "0612345678", "rapid@example.com", "Kénitra", "ASAP", null, null,
            List.of(new CreateQuotationRequest.RequestedLine(productId, 1))
        );

        var firstResponse = service.create(null, request);
        assertThat(firstResponse).isNotNull();

        // Simulate lookup by ID for the rapid submission cache
        when(repository.findById(firstResponse.id())).thenReturn(Optional.of(
            new QuotationEntity("rapid@example.com", QuotationStatus.PENDING, "VENTE", null, null, 1,
                null, "Jean", "ACME", "0612345678", "rapid@example.com", "Kénitra", "ASAP", null, null)
        ));

        // Immediate replay of identical payload
        var secondResponse = service.create(null, request);
        assertThat(secondResponse.customerReference()).isEqualTo("rapid@example.com");
    }

    @Test
    @DisplayName("Unverified email user CANNOT access or claim a guest quotation (SEC-02)")
    void unverifiedEmailUserCannotAccessGuestQuotation() {
        UUID id = UUID.randomUUID();
        // Guest created quote (customerSubject is null, customerReference is email)
        var guestQuotation = new QuotationEntity(
            "victim@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Victim", "ACME", "0612345678", "victim@example.com", "Kénitra", "ASAP", null, null
        );
        when(repository.findById(id)).thenReturn(Optional.of(guestQuotation));

        // Attacker creates account in Keycloak with victim@example.com, but email_verified = false
        var attackerAuth = mockJwt("victim@example.com", "CLIENT", false, "sub-attacker");

        assertThatThrownBy(() -> service.getQuotationById(attackerAuth, id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Access denied to this quotation request");
    }

    @Test
    @DisplayName("Verified email user CAN access and automatically claim their guest quotation")
    void verifiedEmailUserCanAccessAndLinkGuestQuotation() {
        UUID id = UUID.randomUUID();
        var guestQuotation = new QuotationEntity(
            "legit@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Legit", "ACME", "0612345678", "legit@example.com", "Kénitra", "ASAP", null, null
        );
        when(repository.findById(id)).thenReturn(Optional.of(guestQuotation));

        // User logs in with email_verified = true
        var legitAuth = mockJwt("legit@example.com", "CLIENT", true, "sub-legit-user");

        var response = service.getQuotationById(legitAuth, id);
        assertThat(response).isNotNull();
        assertThat(guestQuotation.getCustomerSubject()).isEqualTo("sub-legit-user");
    }

    @Test
    @DisplayName("Client CANNOT access another client's quotation with different subject (IDOR protection)")
    void clientCannotAccessAnotherClientsQuotation() {
        UUID id = UUID.randomUUID();
        var entity = new QuotationEntity(
            "userA@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub", "KM", 1,
            "Desc", "User A", "Corp", "0612345678", "userA@example.com", "Kénitra", "ASAP", null, null
        );
        entity.assignCustomerSubject("sub-user-A");
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        var userBAuth = mockJwt("userB@example.com", "CLIENT", true, "sub-user-B");

        assertThatThrownBy(() -> service.getQuotationById(userBAuth, id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Access denied to this quotation request");
    }

    @Test
    @DisplayName("Staff can access any quotation regardless of ownership")
    void staffCanAccessAnyQuotation() {
        UUID id = UUID.randomUUID();
        var entity = new QuotationEntity(
            "userA@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub", "KM", 1,
            "Desc", "User A", "Corp", "0612345678", "userA@example.com", "Kénitra", "ASAP", null, null
        );
        entity.assignCustomerSubject("sub-user-A");
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        var commercialAuth = mockJwt("commercial@example.com", "COMMERCIAL", true, "sub-comm");

        var response = service.getQuotationById(commercialAuth, id);
        assertThat(response).isNotNull();
        assertThat(response.customerReference()).isEqualTo("userA@example.com");
    }

    @Test
    void updatesQuotationStatusToQuoted() {
        UUID id = UUID.randomUUID();
        var auth = mockJwt("commercial@example.com", "COMMERCIAL");
        var entity = new QuotationEntity(
            "client@example.com", QuotationStatus.IN_REVIEW, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null
        );
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.updateQuotationStatus(auth, id, new UpdateQuotationStatusRequest(QuotationStatus.QUOTED, new BigDecimal("25000.00"), "Offre valide 30 jours"));

        assertThat(response.status()).isEqualTo(QuotationStatus.QUOTED);
        assertThat(response.proposedAmount()).isEqualTo(new BigDecimal("25000.00"));
    }

    @Test
    void rejectsQuotedStatusWithoutAmount() {
        UUID id = UUID.randomUUID();
        var auth = mockJwt("commercial@example.com", "COMMERCIAL");
        var entity = new QuotationEntity(
            "client@example.com", QuotationStatus.IN_REVIEW, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null
        );
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateQuotationStatus(auth, id, new UpdateQuotationStatusRequest(QuotationStatus.QUOTED, null, "Notes")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("positive proposed amount is required");
    }

    @Test
    void cancelsPendingQuotationByClient() {
        UUID id = UUID.randomUUID();
        var auth = mockJwt("client@example.com", "CLIENT", true, "sub-client");
        var entity = new QuotationEntity(
            "client@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null
        );
        entity.assignCustomerSubject("sub-client");
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = service.cancelQuotation(auth, id);

        assertThat(response.status()).isEqualTo(QuotationStatus.CANCELLED);
    }

    @Test
    void rejectsCancellationAfterInReview() {
        UUID id = UUID.randomUUID();
        var auth = mockJwt("client@example.com", "CLIENT", true, "sub-client");
        var entity = new QuotationEntity(
            "client@example.com", QuotationStatus.IN_REVIEW, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null
        );
        entity.assignCustomerSubject("sub-client");
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.cancelQuotation(auth, id))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Only PENDING quotation requests can be cancelled");
    }

    @Test
    void rejectsStaffSettingCancelledStatus() {
        UUID id = UUID.randomUUID();
        var auth = mockJwt("commercial@example.com", "COMMERCIAL");
        var entity = new QuotationEntity(
            "client@example.com", QuotationStatus.PENDING, "VENTE", "Bizhub", "KM", 1,
            "Desc", "Jean", "ACME", "0612345678", "client@example.com", "Kénitra", "ASAP", null, null
        );
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateQuotationStatus(auth, id, new UpdateQuotationStatusRequest(QuotationStatus.CANCELLED, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Staff cannot set quotation status to CANCELLED");
    }
}

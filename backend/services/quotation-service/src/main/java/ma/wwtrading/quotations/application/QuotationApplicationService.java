package ma.wwtrading.quotations.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import ma.wwtrading.quotations.api.CreateQuotationRequest;
import ma.wwtrading.quotations.api.QuotationResponse;
import ma.wwtrading.quotations.api.UpdateQuotationStatusRequest;
import ma.wwtrading.quotations.domain.QuotationEntity;
import ma.wwtrading.quotations.domain.QuotationLineEntity;
import ma.wwtrading.quotations.domain.QuotationRepository;
import ma.wwtrading.quotations.domain.QuotationStatus;
import ma.wwtrading.quotations.infrastructure.kafka.QuotationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuotationApplicationService {

    private static final int MAX_TRACKED_SUBMISSIONS = 5_000;
    private static final long DEBOUNCE_WINDOW_MILLIS = 30_000L;

    private static final class SubmissionRecord {
        final UUID quotationId;
        final long timestampMillis;

        SubmissionRecord(UUID quotationId, long timestampMillis) {
            this.quotationId = quotationId;
            this.timestampMillis = timestampMillis;
        }
    }

    private final Map<String, SubmissionRecord> rapidSubmissions = new ConcurrentHashMap<>();

    private final QuotationRepository repository;
    private final QuotationMapper mapper;
    private final QuotationTransactionHelper transactionHelper;
    private QuotationEventPublisher eventPublisher;
    private QuotationProductClient productClient;

    public QuotationApplicationService(QuotationRepository repository, QuotationMapper mapper) {
        this(repository, mapper, new QuotationTransactionHelper(repository));
    }

    @Autowired
    public QuotationApplicationService(
        QuotationRepository repository,
        QuotationMapper mapper,
        QuotationTransactionHelper transactionHelper
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.transactionHelper = transactionHelper != null ? transactionHelper : new QuotationTransactionHelper(repository);
    }

    @Autowired
    void setEventPublisher(QuotationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    void setProductClient(QuotationProductClient productClient) {
        this.productClient = productClient;
    }

    public QuotationResponse create(JwtAuthenticationToken authentication, CreateQuotationRequest request) {
        return create(authentication, null, request);
    }

    public QuotationResponse create(
        JwtAuthenticationToken authentication,
        String idempotencyKeyHeader,
        CreateQuotationRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        String effectiveIdempotencyKey = resolveIdempotencyKey(
            idempotencyKeyHeader,
            request.idempotencyKey()
        );
        String currentPayloadHash = computePayloadHash(request);

        // 1. Persistent Idempotency Key deduplication in clean transaction
        if (effectiveIdempotencyKey != null) {
            var existing = transactionHelper.findByIdempotencyKeyInCleanTransaction(effectiveIdempotencyKey);
            if (existing.isPresent()) {
                QuotationEntity existingQuotation = existing.get();
                if (matchesPayload(existingQuotation, currentPayloadHash, request)) {
                    return mapper.toResponse(existingQuotation);
                }
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "IDEMPOTENCY_PAYLOAD_MISMATCH"
                );
            }
        }

        // 2. Short-term rapid double-submission deduplication (30 seconds in-memory cache)
        long now = System.currentTimeMillis();
        SubmissionRecord recent = rapidSubmissions.get(currentPayloadHash);
        if (recent != null && (now - recent.timestampMillis) < DEBOUNCE_WINDOW_MILLIS) {
            var previous = repository.findById(recent.quotationId);
            if (previous.isPresent()) {
                return mapper.toResponse(previous.get());
            }
        }

        List<CreateQuotationRequest.RequestedLine> requestedLines = request.requestedLines();
        boolean catalogRequest = requestedLines != null && !requestedLines.isEmpty();
        if (requestedLines != null && requestedLines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one product");
        }
        if (!catalogRequest && (request.description() == null || request.description().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A request description is required");
        }
        String customerRef = resolveCustomerReference(authentication, request.email());
        QuotationEntity entity = mapper.toEntity(customerRef, request);
        if (authentication != null && authentication.getToken() != null && !isStaff(authentication)) {
            entity.assignCustomerSubject(authentication.getToken().getSubject());
        }
        if (effectiveIdempotencyKey != null) {
            entity.assignIdempotencyKey(effectiveIdempotencyKey);
            entity.assignIdempotencyPayloadHash(currentPayloadHash);
        }

        if (catalogRequest) {
            var seen = new HashSet<UUID>();
            for (var requested : requestedLines) {
                if (requested == null || requested.productId() == null || requested.quantity() < 1
                        || requested.quantity() > 9999
                        || !seen.add(requested.productId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or duplicate product line");
                }
                var product = productClient.getProduct(requested.productId());
                entity.addRequestedLine(new QuotationLineEntity(product.id(), product.reference(),
                    product.name(), product.price(), requested.quantity()));
            }
        }

        QuotationEntity saved;
        try {
            saved = transactionHelper.insertInNewTransaction(entity);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition handling:
            // Since insertInNewTransaction ran in an isolated REQUIRES_NEW transaction that is now rolled back,
            // the PostgreSQL connection is clean and unaborted. We can execute a recovery lookup safely.
            if (effectiveIdempotencyKey != null) {
                var concurrentWinner = transactionHelper.findByIdempotencyKeyInCleanTransaction(effectiveIdempotencyKey);
                if (concurrentWinner.isPresent()) {
                    QuotationEntity winner = concurrentWinner.get();
                    if (matchesPayload(winner, currentPayloadHash, request)) {
                        return mapper.toResponse(winner);
                    }
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_PAYLOAD_MISMATCH"
                    );
                }
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation request conflict: unique constraint violated");
        }

        // Record in rapid submission cache
        if (rapidSubmissions.size() > MAX_TRACKED_SUBMISSIONS) {
            rapidSubmissions.entrySet().removeIf(e -> (now - e.getValue().timestampMillis) > DEBOUNCE_WINDOW_MILLIS);
        }
        rapidSubmissions.put(currentPayloadHash, new SubmissionRecord(saved.getId(), now));

        if (eventPublisher != null) eventPublisher.quotationCreated(saved);
        return mapper.toResponse(saved);
    }

    @Transactional
    public Page<QuotationResponse> getMyQuotations(JwtAuthenticationToken authentication, Pageable pageable) {
        String customerRef = requireCustomerReference(authentication);
        String jwtSubject = authentication != null && authentication.getToken() != null
            ? authentication.getToken().getSubject()
            : null;

        // If email is verified in Keycloak, link any unclaimed guest quotes matching this email
        if (jwtSubject != null && isEmailVerified(authentication)) {
            List<QuotationEntity> unclaimed = repository.findUnclaimedByEmail(customerRef);
            if (unclaimed != null && !unclaimed.isEmpty()) {
                for (QuotationEntity quotation : unclaimed) {
                    quotation.assignCustomerSubject(jwtSubject);
                }
            }
        }

        // Return quotations owned by the authenticated subject
        if (jwtSubject != null) {
            return repository.findByCustomerSubjectOrderByCreatedAtDesc(jwtSubject, pageable)
                .map(mapper::toResponse);
        }
        return repository.findByCustomerReferenceOrderByCreatedAtDesc(customerRef, pageable)
            .map(mapper::toResponse);
    }

    @Transactional
    public QuotationResponse getQuotationById(JwtAuthenticationToken authentication, UUID id) {
        QuotationEntity quotation = repository.findById(id)
            .orElseThrow(() -> new QuotationNotFoundException(id));

        if (!isStaff(authentication)) {
            if (!isOwner(quotation, authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this quotation request");
            }
        }

        return mapper.toResponse(quotation);
    }

    @Transactional(readOnly = true)
    public Page<QuotationResponse> getAllQuotations(JwtAuthenticationToken authentication, QuotationStatus status, Pageable pageable) {
        if (!isStaff(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff access required");
        }

        if (status != null) {
            return repository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(mapper::toResponse);
        }
        return repository.findAllByOrderByCreatedAtDesc(pageable)
            .map(mapper::toResponse);
    }

    @Transactional
    public QuotationResponse updateQuotationStatus(JwtAuthenticationToken authentication, UUID id, UpdateQuotationStatusRequest request) {
        if (!isStaff(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff access required");
        }

        QuotationEntity quotation = repository.findById(id)
            .orElseThrow(() -> new QuotationNotFoundException(id));

        if (request.status() == QuotationStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff cannot set quotation status to CANCELLED");
        }

        QuotationStatus previousStatus = quotation.getStatus();
        boolean catalogQuotation = !quotation.getLines().isEmpty();
        if (catalogQuotation && request.status() == QuotationStatus.QUOTED) {
            if (quotation.getStatus() != QuotationStatus.IN_REVIEW
                    || request.proposedLines() == null
                    || request.proposedLines().size() != quotation.getLines().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete all requested products before proposing");
            }
            var proposals = new java.util.HashMap<UUID, UpdateQuotationStatusRequest.ProposedLine>();
            for (var line : request.proposedLines()) {
                if (line == null || line.productId() == null || line.quantity() < 1
                        || line.proposedUnitPrice() == null || line.proposedUnitPrice().signum() < 0
                        || proposals.putIfAbsent(line.productId(), line) != null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid proposed product line");
                }
            }
            for (var line : quotation.getLines()) {
                var proposal = proposals.get(line.getProductId());
                if (proposal == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown or missing proposed product");
                }
                line.propose(proposal.quantity(), proposal.proposedUnitPrice());
            }
            try {
                quotation.propose(request.discount() == null ? BigDecimal.ZERO : request.discount(),
                    request.deliveryFee() == null ? BigDecimal.ZERO : request.deliveryFee(),
                    request.validUntil(), request.commercialNotes());
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid quotation totals or validity");
            }
        } else if (request.status() == QuotationStatus.QUOTED) {
            if (request.proposedAmount() == null || request.proposedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive proposed amount is required for QUOTED status");
            }
        }

        if (!catalogQuotation || request.status() != QuotationStatus.QUOTED) {
            quotation.updateStatus(request.status(), request.proposedAmount(), request.commercialNotes());
        }
        QuotationEntity saved = repository.save(quotation);
        if (previousStatus != saved.getStatus()) {
            notifyStatusChanged(saved, previousStatus);
        }
        return mapper.toResponse(saved);
    }

    @Transactional
    public QuotationResponse cancelQuotation(JwtAuthenticationToken authentication, UUID id) {
        QuotationEntity quotation = repository.findById(id)
            .orElseThrow(() -> new QuotationNotFoundException(id));

        if (!isStaff(authentication)) {
            if (!isOwner(quotation, authentication)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this quotation request");
            }
        }

        if (quotation.getStatus() != QuotationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING quotation requests can be cancelled");
        }

        QuotationStatus previousStatus = quotation.getStatus();
        quotation.cancel();
        QuotationEntity saved = repository.save(quotation);
        notifyStatusChanged(saved, previousStatus);
        return mapper.toResponse(saved);
    }

    @Transactional
    public QuotationResponse decide(JwtAuthenticationToken authentication, UUID id, QuotationStatus decision) {
        QuotationEntity quotation = repository.findById(id)
            .orElseThrow(() -> new QuotationNotFoundException(id));
        if (!isOwner(quotation, authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to this quotation request");
        }
        if (decision == QuotationStatus.ACCEPTED && quotation.getStatus() == QuotationStatus.ACCEPTED) {
            return mapper.toResponse(quotation);
        }
        QuotationStatus previousStatus = quotation.getStatus();
        try {
            quotation.decide(decision);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quotation is not awaiting a decision");
        }
        QuotationEntity saved = repository.save(quotation);
        notifyStatusChanged(saved, previousStatus);
        return mapper.toResponse(saved);
    }

    private boolean isOwner(QuotationEntity quotation, JwtAuthenticationToken authentication) {
        if (quotation == null || authentication == null || authentication.getToken() == null) {
            return false;
        }
        String jwtSubject = authentication.getToken().getSubject();
        if (quotation.getCustomerSubject() != null && !quotation.getCustomerSubject().isBlank()) {
            return jwtSubject != null && jwtSubject.equals(quotation.getCustomerSubject());
        }

        // For guest quotes (customerSubject == null):
        // Only grant ownership if the caller's email is verified in Keycloak
        if (isEmailVerified(authentication)) {
            String email = authentication.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                boolean match = email.equalsIgnoreCase(quotation.getCustomerReference())
                    || (quotation.getEmail() != null && email.equalsIgnoreCase(quotation.getEmail()));
                if (match && jwtSubject != null) {
                    quotation.assignCustomerSubject(jwtSubject);
                }
                return match;
            }
        }
        return false;
    }

    private boolean isEmailVerified(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            return false;
        }
        Boolean verified = authentication.getToken().getClaimAsBoolean("email_verified");
        return Boolean.TRUE.equals(verified);
    }

    private String resolveIdempotencyKey(String headerKey, String bodyKey) {
        String candidate = headerKey != null && !headerKey.isBlank() ? headerKey.trim()
                : (bodyKey != null && !bodyKey.isBlank() ? bodyKey.trim() : null);
        if (candidate == null) {
            return null;
        }
        return candidate.length() > 128 ? candidate.substring(0, 128) : candidate;
    }

    private boolean matchesPayload(
        QuotationEntity existing,
        String currentPayloadHash,
        CreateQuotationRequest request
    ) {
        if (existing == null) {
            return false;
        }
        String existingHash = existing.getIdempotencyPayloadHash();
        if (existingHash != null && !existingHash.isBlank()) {
            return existingHash.equalsIgnoreCase(currentPayloadHash);
        }

        // Legacy quotation where hash was not persisted (pre-V8)
        // Compare by reconstructing the canonical payload hash from existing entity fields
        try {
            String reconstructedHash = computeEntityPayloadHash(existing);
            if (reconstructedHash != null && !reconstructedHash.isBlank()) {
                boolean matches = reconstructedHash.equalsIgnoreCase(currentPayloadHash);
                if (matches) {
                    // Backfill hash on legacy entity in an isolated transaction
                    existing.assignIdempotencyPayloadHash(reconstructedHash);
                    try {
                        transactionHelper.updateInNewTransaction(existing);
                    } catch (Exception ignored) {
                        // Non-blocking backfill
                    }
                }
                return matches;
            }
        } catch (Exception ignored) {
            // Conservative fallback: If reconstruction fails, do not assume a match
        }

        return false;
    }

    /**
     * Computes a canonical, deterministic SHA-256 fingerprint of the quotation request payload.
     * Excludes volatile transport fields (idempotencyKey) while preserving all semantic request fields.
     */
    public static String computePayloadHash(CreateQuotationRequest request) {
        if (request == null) {
            return "";
        }
        return computeCanonicalHash(
            request.serviceType(),
            request.fullName(),
            request.company(),
            request.phone(),
            request.email(),
            request.city(),
            request.quantity(),
            request.requestedEquipment(),
            request.brandModel(),
            request.description(),
            request.desiredTimeline(),
            request.estimatedBudget(),
            request.additionalMessage(),
            request.requestedLines()
        );
    }

    /**
     * Computes the canonical SHA-256 fingerprint from an existing QuotationEntity,
     * allowing safe comparison with legacy records where idempotency_payload_hash was NULL.
     */
    public static String computeEntityPayloadHash(QuotationEntity entity) {
        if (entity == null) {
            return "";
        }
        List<CreateQuotationRequest.RequestedLine> lines = null;
        if (entity.getLines() != null && !entity.getLines().isEmpty()) {
            lines = entity.getLines().stream()
                .filter(Objects::nonNull)
                .map(line -> new CreateQuotationRequest.RequestedLine(line.getProductId(), line.getRequestedQuantity()))
                .toList();
        }
        return computeCanonicalHash(
            entity.getServiceType(),
            entity.getFullName(),
            entity.getCompany(),
            entity.getPhone(),
            entity.getEmail(),
            entity.getCity(),
            entity.getQuantity(),
            entity.getRequestedEquipment(),
            entity.getBrandModel(),
            entity.getDescription(),
            entity.getDesiredTimeline(),
            entity.getEstimatedBudget(),
            entity.getAdditionalMessage(),
            lines
        );
    }

    private static String computeCanonicalHash(
        String serviceType,
        String fullName,
        String company,
        String phone,
        String email,
        String city,
        Integer quantity,
        String requestedEquipment,
        String brandModel,
        String description,
        String desiredTimeline,
        BigDecimal estimatedBudget,
        String additionalMessage,
        List<CreateQuotationRequest.RequestedLine> lines
    ) {
        StringBuilder canonical = new StringBuilder(256);
        appendCanonical(canonical, "serviceType", serviceType);
        appendCanonical(canonical, "fullName", fullName);
        appendCanonical(canonical, "company", company);
        appendCanonical(canonical, "phone", normalizePhone(phone));
        appendCanonical(canonical, "email", email);
        appendCanonical(canonical, "city", city);
        appendCanonical(canonical, "quantity", quantity != null ? String.valueOf(quantity) : "1");
        appendCanonical(canonical, "requestedEquipment", requestedEquipment);
        appendCanonical(canonical, "brandModel", brandModel);
        appendCanonical(canonical, "description", description);
        appendCanonical(canonical, "desiredTimeline", desiredTimeline);
        appendCanonical(canonical, "estimatedBudget",
            estimatedBudget != null ? estimatedBudget.stripTrailingZeros().toPlainString() : "");
        appendCanonical(canonical, "additionalMessage", additionalMessage);

        canonical.append("lines=");
        if (lines != null && !lines.isEmpty()) {
            lines.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CreateQuotationRequest.RequestedLine::productId))
                .forEach(line -> canonical.append(line.productId()).append(':').append(line.quantity()).append(';'));
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static void appendCanonical(StringBuilder sb, String key, String value) {
        sb.append(key).append('=');
        if (value != null && !value.isBlank()) {
            sb.append(value.trim().toLowerCase(Locale.ROOT));
        }
        sb.append('\n');
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String trimmed = phone.trim();
        boolean startsWithPlus = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("[^0-9]", "");
        return startsWithPlus ? "+" + digits : digits;
    }

    private void notifyStatusChanged(QuotationEntity quotation, QuotationStatus previousStatus) {
        if (eventPublisher != null) eventPublisher.quotationStatusChanged(quotation, previousStatus);
    }

    private String resolveCustomerReference(JwtAuthenticationToken authentication, String fallbackEmail) {
        if (authentication != null && authentication.getToken() != null && !isStaff(authentication)) {
            String email = authentication.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return fallbackEmail != null ? fallbackEmail : "anonymous";
    }

    private String requireCustomerReference(JwtAuthenticationToken authentication) {
        if (authentication == null || authentication.getToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String email = authentication.getToken().getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication email missing");
        }
        return email;
    }

    private boolean isStaff(JwtAuthenticationToken authentication) {
        return authentication != null && authentication.getAuthorities() != null
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_MANAGER")
                        || authority.getAuthority().equals("ROLE_COMMERCIAL"));
    }
}

package ma.wwtrading.orders.application;

import java.util.List;
import java.util.UUID;

import ma.wwtrading.orders.api.CreatePaymentBankRequest;
import ma.wwtrading.orders.api.PaymentBankResponse;
import ma.wwtrading.orders.api.UpdatePaymentBankRequest;
import ma.wwtrading.orders.domain.PaymentBankEntity;
import ma.wwtrading.orders.domain.PaymentBankRepository;
import ma.wwtrading.orders.infrastructure.PaymentBankLogoStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service

public class PaymentBankApplicationService {

    private final PaymentBankRepository repository;
    private final PaymentBankLogoStorage logoStorage;


    public PaymentBankApplicationService(
            PaymentBankRepository repository,
            PaymentBankLogoStorage logoStorage) {
        this.repository = repository;
        this.logoStorage = logoStorage;
    }

  

    @Transactional(readOnly = true)
    public List<PaymentBankResponse> listActiveBanks() {
        return repository.findByActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentBankResponse> listAllBanks() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentBankResponse getById(UUID id) {
        PaymentBankEntity bank = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment bank not found"));
        return toResponse(bank);
    }

    @Transactional
    public PaymentBankResponse createBank(CreatePaymentBankRequest request, MultipartFile logo) {
        String trimmedRef = request.reference() != null ? request.reference().trim() : "";
        if (trimmedRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank reference is required");
        }
        if (repository.existsByReference(trimmedRef)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank reference already exists");
        }

        String logoUrl = request.logoUrl();
        if (logo != null && !logo.isEmpty()) {
            if (logoStorage != null) {
                try {
                    logoUrl = logoStorage.store(logo);
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
                }
            }
        }

        boolean active = request.active() == null || request.active();

        PaymentBankEntity entity = new PaymentBankEntity(
                trimmedRef,
                request.bankName(),
                request.rib(),
                request.beneficiary(),
                logoUrl,
                active
        );

        PaymentBankEntity saved = repository.save(entity);
        return toResponse(saved);
    }

    @Transactional
    public PaymentBankResponse createBank(CreatePaymentBankRequest request) {
        return createBank(request, null);
    }

    @Transactional
    public PaymentBankResponse updateBank(UUID id, UpdatePaymentBankRequest request, MultipartFile logo) {
        PaymentBankEntity bank = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment bank not found"));

        String trimmedRef = request.reference() != null ? request.reference().trim() : "";
        if (trimmedRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank reference is required");
        }

        if (repository.existsByReferenceAndIdNot(trimmedRef, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank reference already exists");
        }

        String logoUrl = bank.getImageUrl();
        if (logo != null && !logo.isEmpty()) {
            if (logoStorage != null) {
                try {
                    logoUrl = logoStorage.store(logo);
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
                }
            }
        } else if (request.logoUrl() != null && !request.logoUrl().isBlank()) {
            logoUrl = request.logoUrl();
        }

        bank.updateDetails(
                trimmedRef,
                request.bankName(),
                request.rib(),
                request.beneficiary(),
                logoUrl
        );

        if (request.active() != null) {
            bank.setActive(request.active());
        }

        PaymentBankEntity saved = repository.save(bank);
        return toResponse(saved);
    }

    @Transactional
    public PaymentBankResponse updateBank(UUID id, UpdatePaymentBankRequest request) {
        return updateBank(id, request, null);
    }

    @Transactional
    public PaymentBankResponse setActive(UUID id, boolean active) {
        PaymentBankEntity bank = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment bank not found"));

        bank.setActive(active);
        PaymentBankEntity saved = repository.save(bank);
        return toResponse(saved);
    }

    public PaymentBankResponse toResponse(PaymentBankEntity entity) {
        return new PaymentBankResponse(
                entity.getId(),
                entity.getReference(),
                entity.getBankName(),
                entity.getRib(),
                entity.getBeneficiary(),
                entity.getImageUrl(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
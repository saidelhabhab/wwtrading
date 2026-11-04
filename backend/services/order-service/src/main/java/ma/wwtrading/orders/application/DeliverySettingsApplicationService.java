package ma.wwtrading.orders.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ma.wwtrading.orders.api.DeliverySettingsResponse;
import ma.wwtrading.orders.api.UpdateDeliverySettingsRequest;
import ma.wwtrading.orders.domain.DeliverySettingsEntity;
import ma.wwtrading.orders.domain.DeliverySettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeliverySettingsApplicationService {

    private final DeliverySettingsRepository repository;
    private final DeliveryFeeProperties properties;

    @Autowired
    public DeliverySettingsApplicationService(
            @Autowired(required = false) DeliverySettingsRepository repository,
            DeliveryFeeProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DeliverySettingsResponse getSettings() {
        if (repository != null) {
            return repository.findById(DeliverySettingsEntity.DEFAULT_ID)
                    .map(entity -> {
                        syncProperties(entity);
                        return toResponse(entity);
                    })
                    .orElseGet(this::fromProperties);
        }
        return fromProperties();
    }

    @Transactional
    public DeliverySettingsResponse updateSettings(UpdateDeliverySettingsRequest request) {
        if (request == null || request.feesEnabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery fees enabled flag is required");
        }
        validateNonNegative(request.kenitraAreaFee(), "Kenitra area delivery fee must be non-negative");
        validateNonNegative(request.casablancaFee(), "Casablanca delivery fee must be non-negative");
        validateNonNegative(request.otherAreasFee(), "Other areas delivery fee must be non-negative");
        validateNonNegative(request.freeDeliveryThreshold(), "Free delivery threshold must be non-negative");

        if (repository != null) {
            DeliverySettingsEntity entity = repository.findById(DeliverySettingsEntity.DEFAULT_ID)
                    .orElseGet(() -> new DeliverySettingsEntity(
                            properties.isFeesEnabled(),
                            feeOrDefault(properties.getFees().get("Kenitra"), DeliveryFeeProperties.DEFAULT_KENITRA_FEE),
                            feeOrDefault(properties.getFees().get("Casablanca"), DeliveryFeeProperties.DEFAULT_CASABLANCA_FEE),
                            feeOrDefault(properties.getFees().get("External"), DeliveryFeeProperties.DEFAULT_EXTERNAL_FEE),
                            properties.getFreeDeliveryThreshold() != null
                                    ? properties.getFreeDeliveryThreshold()
                                    : DeliveryFeeProperties.DEFAULT_FREE_DELIVERY_THRESHOLD
                    ));
            entity.update(
                    request.feesEnabled(),
                    request.kenitraAreaFee(),
                    request.casablancaFee(),
                    request.otherAreasFee(),
                    request.freeDeliveryThreshold()
            );
            DeliverySettingsEntity saved = repository.saveAndFlush(entity);
            syncProperties(saved);
            return toResponse(saved);
        }

        properties.setFeesEnabled(request.feesEnabled());
        Map<String, BigDecimal> updatedFees = new LinkedHashMap<>(properties.getFees());
        if (request.kenitraAreaFee() != null) {
            updatedFees.put("Kenitra", request.kenitraAreaFee());
        }
        if (request.casablancaFee() != null) {
            updatedFees.put("Casablanca", request.casablancaFee());
        }
        if (request.otherAreasFee() != null) {
            updatedFees.put("External", request.otherAreasFee());
        }
        properties.setFees(updatedFees);
        if (request.freeDeliveryThreshold() != null) {
            properties.setFreeDeliveryThreshold(request.freeDeliveryThreshold());
        }
        return fromProperties();
    }

    public void syncFromRepositoryIfPresent() {
        if (repository != null) {
            repository.findById(DeliverySettingsEntity.DEFAULT_ID)
                    .ifPresent(this::syncProperties);
        }
    }

    private void syncProperties(DeliverySettingsEntity entity) {
        properties.setFeesEnabled(entity.isFeesEnabled());
        properties.setFreeDeliveryThreshold(entity.getFreeDeliveryThreshold());
        Map<String, BigDecimal> fees = new LinkedHashMap<>(properties.getFees());
        fees.put("Kenitra", entity.getKenitraAreaFee());
        fees.put("Casablanca", entity.getCasablancaFee());
        fees.put("External", entity.getOtherAreasFee());
        properties.setFees(fees);
    }

    private DeliverySettingsResponse toResponse(DeliverySettingsEntity entity) {
        return new DeliverySettingsResponse(
                entity.isFeesEnabled(),
                entity.getKenitraAreaFee(),
                entity.getCasablancaFee(),
                entity.getOtherAreasFee(),
                entity.getFreeDeliveryThreshold(),
                List.copyOf(properties.getKenitraSurroundingAreas()),
                entity.getUpdatedAt()
        );
    }

    private DeliverySettingsResponse fromProperties() {
        return new DeliverySettingsResponse(
                properties.isFeesEnabled(),
                feeOrDefault(properties.getFees().get("Kenitra"), DeliveryFeeProperties.DEFAULT_KENITRA_FEE),
                feeOrDefault(properties.getFees().get("Casablanca"), DeliveryFeeProperties.DEFAULT_CASABLANCA_FEE),
                feeOrDefault(properties.getFees().get("External"), DeliveryFeeProperties.DEFAULT_EXTERNAL_FEE),
                properties.getFreeDeliveryThreshold() != null
                        ? properties.getFreeDeliveryThreshold()
                        : DeliveryFeeProperties.DEFAULT_FREE_DELIVERY_THRESHOLD,
                List.copyOf(properties.getKenitraSurroundingAreas()),
                Instant.now()
        );
    }

    private BigDecimal feeOrDefault(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private void validateNonNegative(BigDecimal value, String message) {
        if (value != null && value.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}


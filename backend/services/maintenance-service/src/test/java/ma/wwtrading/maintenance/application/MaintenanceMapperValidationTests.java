package ma.wwtrading.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import ma.wwtrading.maintenance.api.CreateMaintenanceRequest;
import ma.wwtrading.maintenance.domain.MaintenanceEntity;
import ma.wwtrading.maintenance.domain.MaintenancePriority;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MaintenanceMapperValidationTests {

    private final MaintenanceMapper mapper = Mappers.getMapper(MaintenanceMapper.class);

    @Test
    void mapsValidCreationRequestAndPreservesServerManagedDefaults() {
        var maintenance = mapper.toEntity(new CreateMaintenanceRequest(
            "  customer-123  ",
            "  product-456  ",
            "  SN-998877  ",
            MaintenancePriority.HIGH,
            "  Printer service  ",
            "  Replace the worn roller  ",
            "  client-ticket-42  "
        ));

        assertThat(maintenance.getId()).isNotNull();
        assertThat(maintenance.getCustomerReference()).isEqualTo("customer-123");
        assertThat(maintenance.getClientReference()).isEqualTo("client-ticket-42");
        assertThat(maintenance.getProductReference()).isEqualTo("product-456");
        assertThat(maintenance.getSerialNumber()).isEqualTo("SN-998877");
        assertThat(maintenance.getPriority()).isEqualTo(MaintenancePriority.HIGH);
        assertThat(maintenance.getSubject()).isEqualTo("Printer service");
        assertThat(maintenance.getDescription()).isEqualTo("Replace the worn roller");
        assertThat(maintenance.getStatus()).isEqualTo(MaintenanceStatus.OPEN);
        assertThat(maintenance.getCreatedAt()).isNull();
        assertThat(maintenance.getUpdatedAt()).isNull();
    }

    @Test
    void validatesRequiredReferencesAndMaintenanceFields() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var invalid = new CreateMaintenanceRequest(
                "x".repeat(65),
                "x".repeat(65),
                "x".repeat(121),
                MaintenancePriority.LOW,
                "",
                "x".repeat(10001),
                "x".repeat(101)
            );

            assertThat(validator.validate(invalid))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("customerReference", "productReference", "serialNumber", "subject", "description", "clientReference");
            assertThat(validator.validate(new CreateMaintenanceRequest(
                "customer-123", null, null, MaintenancePriority.MEDIUM, "Printer service", "Replace the worn roller"
            ))).isEmpty();
        }
    }

    @Test
    void mapsEntityToReadOnlyResponse() {
        var maintenance = new MaintenanceEntity(
            "customer-123", "client-ticket-42", "product-456", "SN-998877", MaintenancePriority.HIGH,
            "Printer service", "Replace the worn roller"
        );
        var response = mapper.toResponse(maintenance);

        assertThat(response.id()).isEqualTo(maintenance.getId());
        assertThat(response.customerReference()).isEqualTo("customer-123");
        assertThat(response.clientReference()).isEqualTo("client-ticket-42");
        assertThat(response.productReference()).isEqualTo("product-456");
        assertThat(response.serialNumber()).isEqualTo("SN-998877");
        assertThat(response.priority()).isEqualTo(MaintenancePriority.HIGH);
        assertThat(response.subject()).isEqualTo("Printer service");
        assertThat(response.description()).isEqualTo("Replace the worn roller");
        assertThat(response.status()).isEqualTo(MaintenanceStatus.OPEN);
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void requestDtoExcludesServerManagedFields() {
        assertThat(CreateMaintenanceRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("customerReference", "productReference", "serialNumber", "priority", "subject", "description", "clientReference")
            .doesNotContain("id", "status", "createdAt", "updatedAt");
    }

    @Test
    void usesGeneratedMapStructImplementation() {
        assertThat(mapper.getClass().getName()).endsWith("MaintenanceMapperImpl");
    }
}

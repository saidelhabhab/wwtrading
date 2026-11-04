package ma.wwtrading.maintenance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.wwtrading.maintenance.domain.MaintenancePriority;

public record CreateMaintenanceRequest(
    @Size(max = 64) String customerReference,
    @Size(max = 64) String productReference,
    @Size(max = 120) String serialNumber,
    MaintenancePriority priority,
    @NotBlank @Size(max = 200) String subject,
    @NotBlank @Size(max = 10000) String description,
    @Size(max = 100) String clientReference
) {
    public CreateMaintenanceRequest(String customerReference, String productReference, String serialNumber,
                                    MaintenancePriority priority, String subject, String description) {
        this(customerReference, productReference, serialNumber, priority, subject, description, null);
    }

    public CreateMaintenanceRequest(String customerReference, String productReference, String subject, String description) {
        this(customerReference, productReference, null, MaintenancePriority.MEDIUM, subject, description, null);
    }
}

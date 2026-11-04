package ma.wwtrading.maintenance.api;

import jakarta.validation.constraints.Size;

public record CompleteMaintenanceRequest(
    @Size(max = 10000) String interventionNotes
) {
}


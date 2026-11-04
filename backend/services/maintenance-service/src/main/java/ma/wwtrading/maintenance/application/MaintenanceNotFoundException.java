package ma.wwtrading.maintenance.application;

import java.util.UUID;

public class MaintenanceNotFoundException extends RuntimeException {

    public MaintenanceNotFoundException(UUID id) {
        super("Maintenance not found: " + id);
    }
}

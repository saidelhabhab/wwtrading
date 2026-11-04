package ma.wwtrading.maintenance.application;

public class MaintenanceDataConflictException extends RuntimeException {

    public MaintenanceDataConflictException() {
        super("Maintenance data conflicts with an existing record");
    }
}

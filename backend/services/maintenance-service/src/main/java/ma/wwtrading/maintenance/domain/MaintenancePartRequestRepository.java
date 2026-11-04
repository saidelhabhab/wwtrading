package ma.wwtrading.maintenance.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenancePartRequestRepository extends JpaRepository<MaintenancePartRequestEntity, UUID> {

    List<MaintenancePartRequestEntity> findByMaintenanceIdOrderByRequestedAtDesc(UUID maintenanceId);
}


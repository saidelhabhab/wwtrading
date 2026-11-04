package ma.wwtrading.maintenance.domain;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceHistoryRepository extends JpaRepository<MaintenanceHistoryEntity, UUID> {
    Page<MaintenanceHistoryEntity> findByMaintenanceIdOrderByCreatedAtDesc(UUID maintenanceId, Pageable pageable);
}

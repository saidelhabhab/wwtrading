package ma.wwtrading.maintenance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceAttachmentRepository extends JpaRepository<MaintenanceAttachmentEntity, UUID> {

    List<MaintenanceAttachmentEntity> findByMaintenanceIdOrderByCreatedAtAsc(UUID maintenanceId);

    Optional<MaintenanceAttachmentEntity> findByIdAndMaintenanceId(UUID id, UUID maintenanceId);

    long countByMaintenanceId(UUID maintenanceId);
}

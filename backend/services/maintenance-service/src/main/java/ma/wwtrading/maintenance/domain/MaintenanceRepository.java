package ma.wwtrading.maintenance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaintenanceRepository extends JpaRepository<MaintenanceEntity, UUID> {

    interface StatusCountView { MaintenanceStatus getStatus(); long getTotal(); }
    interface DailyCountView { LocalDate getDay(); long getTotal(); }

    List<MaintenanceEntity> findByCustomerReference(String customerReference);

    Page<MaintenanceEntity> findByCustomerReferenceOrderByCreatedAtDesc(String customerReference, Pageable pageable);

    List<MaintenanceEntity> findByProductReference(String productReference);

    List<MaintenanceEntity> findByStatus(MaintenanceStatus status);

    /** Used by the Technician inbox: returns all requests assigned to the given technician, newest assignment first. */
    Page<MaintenanceEntity> findByTechnicianIdOrderByAssignedAtDesc(UUID technicianId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select maintenance from MaintenanceEntity maintenance where maintenance.id = :id")
    Optional<MaintenanceEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select m.status as status, count(m) as total from MaintenanceEntity m where m.createdAt >= :from and m.createdAt < :to group by m.status")
    List<StatusCountView> countByStatusForPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select m.status as status, count(m) as total from MaintenanceEntity m where m.customerSubject = :subject and m.createdAt >= :from and m.createdAt < :to group by m.status")
    List<StatusCountView> countByCustomerSubjectAndStatus(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select m.status as status, count(m) as total from MaintenanceEntity m where m.technicianId = :technicianId and m.createdAt >= :from and m.createdAt < :to group by m.status")
    List<StatusCountView> countByTechnicianAndStatus(@Param("technicianId") UUID technicianId, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as total from maintenance where created_at >= :from and created_at < :to group by cast(created_at at time zone 'UTC' as date) order by day", nativeQuery = true)
    List<DailyCountView> countCreatedByDay(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as total from maintenance where customer_subject = :subject and created_at >= :from and created_at < :to group by cast(created_at at time zone 'UTC' as date) order by day", nativeQuery = true)
    List<DailyCountView> countCreatedByCustomerSubjectAndDay(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as total from maintenance where technician_id = :technicianId and created_at >= :from and created_at < :to group by cast(created_at at time zone 'UTC' as date) order by day", nativeQuery = true)
    List<DailyCountView> countCreatedByTechnicianAndDay(@Param("technicianId") UUID technicianId, @Param("from") Instant from, @Param("to") Instant to);
}

package ma.wwtrading.quotations.prospects.domain;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProspectRepository extends JpaRepository<ProspectEntity, UUID>,
        JpaSpecificationExecutor<ProspectEntity> {

    interface StatusCountView {
        ProspectStatus getStatus();
        long getTotal();
    }

    interface DailyCountView {
        LocalDate getDay();
        long getTotal();
    }

    Page<ProspectEntity> findByAssignedCommercialSubjectOrderByUpdatedAtDesc(
            String assignedCommercialSubject, Pageable pageable);

    Page<ProspectEntity> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    @Query("""
            select p.status as status, count(p) as total
            from ProspectEntity p
            where p.createdAt >= :from and p.createdAt < :toExclusive
            group by p.status
            """)
    List<StatusCountView> countByStatusForPeriod(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    @Query("""
            select p.status as status, count(p) as total
            from ProspectEntity p
            where p.assignedCommercialSubject = :subject
              and p.createdAt >= :from and p.createdAt < :toExclusive
            group by p.status
            """)
    List<StatusCountView> countByStatusForCommercialAndPeriod(
            @Param("subject") String subject,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    @Query(value = """
            select cast(created_at at time zone 'UTC' as date) as day, count(*) as total
            from prospect
            where created_at >= :from and created_at < :toExclusive
            group by cast(created_at at time zone 'UTC' as date)
            order by day
            """, nativeQuery = true)
    List<DailyCountView> countCreatedByDayForPeriod(
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    @Query(value = """
            select cast(created_at at time zone 'UTC' as date) as day, count(*) as total
            from prospect
            where assigned_commercial_subject = :subject
              and created_at >= :from and created_at < :toExclusive
            group by cast(created_at at time zone 'UTC' as date)
            order by day
            """, nativeQuery = true)
    List<DailyCountView> countCreatedByDayForCommercialAndPeriod(
            @Param("subject") String subject,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);
}

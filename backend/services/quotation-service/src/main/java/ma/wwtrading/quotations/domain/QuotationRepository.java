package ma.wwtrading.quotations.domain;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<QuotationEntity, UUID> {

    interface StatusCountView { QuotationStatus getStatus(); long getTotal(); }
    interface DailyCountView { LocalDate getDay(); long getTotal(); }

    @Query("SELECT DISTINCT q FROM QuotationEntity q LEFT JOIN FETCH q.lines WHERE q.idempotencyKey = :idempotencyKey")
    Optional<QuotationEntity> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    Page<QuotationEntity> findByCustomerReferenceOrderByCreatedAtDesc(String customerReference, Pageable pageable);

    Page<QuotationEntity> findByCustomerSubjectOrderByCreatedAtDesc(String customerSubject, Pageable pageable);

    @Query("SELECT q FROM QuotationEntity q WHERE q.customerSubject IS NULL AND (LOWER(q.customerReference) = LOWER(:email) OR LOWER(q.email) = LOWER(:email))")
    List<QuotationEntity> findUnclaimedByEmail(@Param("email") String email);

    Page<QuotationEntity> findByStatusOrderByCreatedAtDesc(QuotationStatus status, Pageable pageable);

    Page<QuotationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select q.status as status, count(q) as total from QuotationEntity q where q.createdAt >= :from and q.createdAt < :to group by q.status")
    List<StatusCountView> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select q.status as status, count(q) as total from QuotationEntity q where q.customerSubject = :subject and q.createdAt >= :from and q.createdAt < :to group by q.status")
    List<StatusCountView> countByCustomerSubjectAndStatus(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as total from quotation where created_at >= :from and created_at < :to group by cast(created_at at time zone 'UTC' as date) order by day", nativeQuery = true)
    List<DailyCountView> countCreatedByDay(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as total from quotation where customer_subject = :subject and created_at >= :from and created_at < :to group by cast(created_at at time zone 'UTC' as date) order by day", nativeQuery = true)
    List<DailyCountView> countCreatedByCustomerSubjectAndDay(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);
}

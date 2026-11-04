package ma.wwtrading.orders.domain;

import java.util.UUID;
import java.util.Optional;
import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

	interface StatusCountView { OrderStatus getStatus(); long getTotal(); }
	interface DailyCountView { LocalDate getDay(); long getTotal(); }
	interface DailyRevenueView { LocalDate getDay(); BigDecimal getPaid(); BigDecimal getRecognized(); long getOrders(); }

	Optional<OrderEntity> findByIdAndCustomerReference(UUID id, String customerReference);

	Optional<OrderEntity> findByQuotationIdAndCustomerReference(UUID quotationId, String customerReference);

	Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

	Optional<OrderEntity> findByTrackingTokenHash(String trackingTokenHash);

	Optional<OrderEntity> findByIdAndTrackingTokenHash(UUID id, String trackingTokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select o from OrderEntity o
		where o.guestOrder = true
		  and o.customerSubject is null
		  and (lower(trim(o.customerReference)) = lower(trim(:email))
		       or (o.contactEmail is not null and lower(trim(o.contactEmail)) = lower(trim(:email))))
		order by o.createdAt desc
		""")
	List<OrderEntity> findUnclaimedGuestOrdersForUpdateByEmail(@Param("email") String email);

	@Query("""
		select o from OrderEntity o
		where (o.customerSubject is not null and o.customerSubject = :subject)
		   or (o.guestOrder = false and o.customerSubject is null and lower(o.customerReference) = lower(:customerReference))
		order by o.createdAt desc
		""")
	Page<OrderEntity> findOwnedOrders(@Param("subject") String subject, @Param("customerReference") String customerReference, Pageable pageable);

	@Query("""
		select o from OrderEntity o
		where o.id = :id
		  and ((o.customerSubject is not null and o.customerSubject = :subject)
		       or (o.guestOrder = false and o.customerSubject is null and lower(o.customerReference) = lower(:customerReference)))
		""")
	Optional<OrderEntity> findOwnedOrderById(@Param("id") UUID id, @Param("subject") String subject, @Param("customerReference") String customerReference);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select o from OrderEntity o
		where o.id = :id
		  and ((o.customerSubject is not null and o.customerSubject = :subject)
		       or (o.guestOrder = false and o.customerSubject is null and lower(o.customerReference) = lower(:customerReference)))
		""")
	Optional<OrderEntity> findOwnedOrderForUpdateById(@Param("id") UUID id, @Param("subject") String subject, @Param("customerReference") String customerReference);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OrderEntity> findForUpdateByIdAndCustomerReference(UUID id, String customerReference);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OrderEntity> findForUpdateById(UUID id);

	Page<OrderEntity> findByCustomerReferenceOrderByCreatedAtDesc(String customerReference, Pageable pageable);

	@Query("select o.status as status, count(o) as total from OrderEntity o where o.createdAt >= :from and o.createdAt < :to group by o.status")
	List<StatusCountView> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

	@Query("select o.status as status, count(o) as total from OrderEntity o where o.customerSubject = :subject and o.createdAt >= :from and o.createdAt < :to group by o.status")
	List<StatusCountView> countByCustomerAndStatus(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

	/**
	 * Order value: every non-cancelled order total. This is what was ordered, NOT revenue.
	 */
	@Query("""
		select coalesce(sum(o.total), 0) from OrderEntity o
		where o.status <> ma.wwtrading.orders.domain.OrderStatus.CANCELLED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	BigDecimal sumOrderValue(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
		select coalesce(sum(o.total), 0) from OrderEntity o
		where o.customerSubject = :subject
		  and o.status <> ma.wwtrading.orders.domain.OrderStatus.CANCELLED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	BigDecimal sumOrderValueByCustomer(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

	/**
	 * Paid revenue: orders whose payment was explicitly confirmed through the payment flow.
	 */
	@Query("""
		select coalesce(sum(o.total), 0) from OrderEntity o
		where o.paymentStatus = ma.wwtrading.orders.domain.PaymentStatus.PAID
		  and o.status <> ma.wwtrading.orders.domain.OrderStatus.CANCELLED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	BigDecimal sumPaidRevenue(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
		select coalesce(sum(o.total), 0) from OrderEntity o
		where o.customerSubject = :subject
		  and o.paymentStatus = ma.wwtrading.orders.domain.PaymentStatus.PAID
		  and o.status <> ma.wwtrading.orders.domain.OrderStatus.CANCELLED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	BigDecimal sumPaidRevenueByCustomer(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

	/**
	 * Recognized revenue: paid orders that have also reached the COMPLETED state.
	 */
	@Query("""
		select coalesce(sum(o.total), 0) from OrderEntity o
		where o.paymentStatus = ma.wwtrading.orders.domain.PaymentStatus.PAID
		  and o.status = ma.wwtrading.orders.domain.OrderStatus.COMPLETED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	BigDecimal sumRecognizedRevenue(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
		select coalesce(sum(o.total), 0) from OrderEntity o
		where o.customerSubject = :subject
		  and o.paymentStatus = ma.wwtrading.orders.domain.PaymentStatus.PAID
		  and o.status = ma.wwtrading.orders.domain.OrderStatus.COMPLETED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	BigDecimal sumRecognizedRevenueByCustomer(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

	/** Number of non-cancelled orders, the denominator of average order value. */
	@Query("""
		select count(o) from OrderEntity o
		where o.status <> ma.wwtrading.orders.domain.OrderStatus.CANCELLED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	long countOrderValueOrders(@Param("from") Instant from, @Param("to") Instant to);

	@Query("""
		select count(o) from OrderEntity o
		where o.customerSubject = :subject
		  and o.status <> ma.wwtrading.orders.domain.OrderStatus.CANCELLED
		  and o.createdAt >= :from and o.createdAt < :to
		""")
	long countOrderValueOrdersByCustomer(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

	/**
	 * Daily paid and recognized revenue in a single grouped pass, with the order count
	 * of the non-cancelled orders contributing to order value.
	 */
	@Query(value = """
		select cast(created_at at time zone 'UTC' as date) as day,
		       coalesce(sum(case when payment_status = 'PAID' and status <> 'CANCELLED' then order_total else 0 end), 0) as paid,
		       coalesce(sum(case when payment_status = 'PAID' and status = 'COMPLETED' then order_total else 0 end), 0) as recognized,
		       count(case when status <> 'CANCELLED' then 1 end) as orders
		from customer_order
		where created_at >= :from and created_at < :to
		group by cast(created_at at time zone 'UTC' as date)
		order by day
		""", nativeQuery = true)
	List<DailyRevenueView> revenueByDay(@Param("from") Instant from, @Param("to") Instant to);

	@Query(value = """
		select cast(created_at at time zone 'UTC' as date) as day,
		       coalesce(sum(case when payment_status = 'PAID' and status <> 'CANCELLED' then order_total else 0 end), 0) as paid,
		       coalesce(sum(case when payment_status = 'PAID' and status = 'COMPLETED' then order_total else 0 end), 0) as recognized,
		       count(case when status <> 'CANCELLED' then 1 end) as orders
		from customer_order
		where customer_subject = :subject and created_at >= :from and created_at < :to
		group by cast(created_at at time zone 'UTC' as date)
		order by day
		""", nativeQuery = true)
	List<DailyRevenueView> revenueByCustomerAndDay(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);

	@Query(value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as total from customer_order where created_at >= :from and created_at < :to group by cast(created_at at time zone 'UTC' as date) order by day", nativeQuery = true)
	List<DailyCountView> countCreatedByDay(@Param("from") Instant from, @Param("to") Instant to);

	@Query(value = "select cast(created_at at time zone 'UTC' as date) as day, count(*) as total from customer_order where customer_subject = :subject and created_at >= :from and created_at < :to group by cast(created_at at time zone 'UTC' as date) order by day", nativeQuery = true)
	List<DailyCountView> countCreatedByCustomerAndDay(@Param("subject") String subject, @Param("from") Instant from, @Param("to") Instant to);
}

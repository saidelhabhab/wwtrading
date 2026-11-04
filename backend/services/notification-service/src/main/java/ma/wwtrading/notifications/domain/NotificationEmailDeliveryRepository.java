package ma.wwtrading.notifications.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEmailDeliveryRepository
        extends JpaRepository<NotificationEmailDeliveryEntity, UUID> {

    @Query(value = """
        select delivery.*
          from notification_email_delivery delivery
         where delivery.status in ('PENDING', 'FAILED', 'RETRYING')
           and delivery.next_attempt_at <= :now
         order by delivery.next_attempt_at, delivery.created_at
         for update skip locked
         limit :batchSize
        """, nativeQuery = true)
    List<NotificationEmailDeliveryEntity> lockDueDeliveries(
        @Param("now") Instant now, @Param("batchSize") int batchSize);

    long countByNotificationId(UUID notificationId);

    java.util.Optional<NotificationEmailDeliveryEntity> findByNotificationId(UUID notificationId);
}

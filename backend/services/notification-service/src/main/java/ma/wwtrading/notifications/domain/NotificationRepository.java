package ma.wwtrading.notifications.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    long countBySourceEventId(UUID sourceEventId);

    Page<NotificationEntity> findByRecipientSubject(String recipientSubject, Pageable pageable);

    Optional<NotificationEntity> findByIdAndRecipientSubject(UUID id, String recipientSubject);

    long countByRecipientSubjectAndReadAtIsNull(String recipientSubject);

    java.util.List<NotificationEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update NotificationEntity notification
           set notification.readAt = :readAt
         where notification.recipientSubject = :recipientSubject
           and notification.readAt is null
        """)
    int markAllUnreadAsRead(
            @Param("recipientSubject") String recipientSubject,
            @Param("readAt") Instant readAt);
}

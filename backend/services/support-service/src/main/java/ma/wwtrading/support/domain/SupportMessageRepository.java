package ma.wwtrading.support.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportMessageRepository extends JpaRepository<SupportMessageEntity, UUID> {
    Page<SupportMessageEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId, Pageable pageable);
    Optional<SupportMessageEntity> findByIdAndConversationId(UUID id, UUID conversationId);
}

package ma.wwtrading.support.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportAttachmentRepository extends JpaRepository<SupportAttachmentEntity, UUID> {
    List<SupportAttachmentEntity> findByMessageIdOrderByCreatedAtAsc(UUID messageId);
    Optional<SupportAttachmentEntity> findByIdAndMessageId(UUID id, UUID messageId);
}

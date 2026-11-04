package ma.wwtrading.support.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SupportConversationRepository extends JpaRepository<SupportConversationEntity, UUID> {
    Page<SupportConversationEntity> findByClientKeycloakSubject(String subject, Pageable pageable);
    Page<SupportConversationEntity> findByAssignedAgentKeycloakSubject(String subject, Pageable pageable);
    Page<SupportConversationEntity> findByAssignedAgentKeycloakSubjectAndStatus(String subject, SupportStatus status,
                                                                                Pageable pageable);
    Page<SupportConversationEntity> findByStatus(SupportStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from SupportConversationEntity conversation where conversation.id = :id")
    Optional<SupportConversationEntity> findByIdForUpdate(@Param("id") UUID id);
}

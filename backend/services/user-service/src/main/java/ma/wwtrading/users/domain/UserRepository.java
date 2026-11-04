package ma.wwtrading.users.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByKeycloakSubject(String keycloakSubject);
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    List<UserEntity> findByKeycloakSubjectIn(Collection<String> keycloakSubjects);
    Page<UserEntity> findByKeycloakSubjectIn(Collection<String> keycloakSubjects, Pageable pageable);
    long countByKeycloakSubjectIn(Collection<String> keycloakSubjects);
    long countByKeycloakSubjectInAndEnabledTrue(Collection<String> keycloakSubjects);
    long countByKeycloakSubjectInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        Collection<String> keycloakSubjects, Instant from, Instant toExclusive);

    @Query("""
        SELECT u FROM UserEntity u
        WHERE u.keycloakSubject IN :subjects
          AND (
            LOWER(u.firstName) LIKE LOWER(CONCAT('%', :kw, '%'))
            OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :kw, '%'))
            OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :kw, '%'))
            OR LOWER(u.phone)     LIKE LOWER(CONCAT('%', :kw, '%'))
          )
    """)
    Page<UserEntity> searchClients(
        @Param("subjects") Collection<String> subjects,
        @Param("kw") String keyword,
        Pageable pageable
    );
}

package ma.wwtrading.products.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface StockRepository extends JpaRepository<StockEntity, UUID> {

    Optional<StockEntity> findByProductId(UUID productId);

    boolean existsByProductId(UUID productId);

    /**
     * Pessimistic read used by every stock write so concurrent operations
     * serialise on the same product balance (prevents overselling races).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<StockEntity> findForUpdateByProductId(UUID productId);
}

package ma.wwtrading.wishlists.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {

    Optional<WishlistEntity> findByCustomerReference(String customerReference);
}

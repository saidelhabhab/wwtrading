package ma.wwtrading.carts.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {

    Optional<CartEntity> findByCustomerReference(String customerReference);
}

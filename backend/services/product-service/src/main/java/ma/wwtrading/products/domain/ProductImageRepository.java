package ma.wwtrading.products.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, UUID> {

    List<ProductImageEntity> findByProductIdOrderByDisplayOrderAscCreatedAtAsc(UUID productId);

    List<ProductImageEntity> findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(UUID productId);

    Optional<ProductImageEntity> findByProductIdAndMainTrue(UUID productId);

    Optional<ProductImageEntity> findFirstByProductIdAndMainTrue(UUID productId);

    Optional<ProductImageEntity> findByIdAndProductId(UUID id, UUID productId);

    long countByProductId(UUID productId);
}

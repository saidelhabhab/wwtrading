package ma.wwtrading.products.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    Optional<ProductEntity> findByProductReference(String productReference);

    /**
     * Lists products with optional filters. Every filter is applied in the database
     * before pagination, so page sizes and totals stay correct.
     *
     * <p>A null filter is ignored, which keeps the unfiltered catalog behaviour of
     * {@code GET /api/products} intact.
     */
    @Query("""
        SELECT product FROM ProductEntity product
        WHERE (:type IS NULL OR product.type = :type)
          AND (:machineCategory IS NULL OR product.machineCategory = :machineCategory)
          AND (:colorMode IS NULL OR product.colorMode = :colorMode)
        """)
    Page<ProductEntity> findAllFiltered(
        @Param("type") ProductType type,
        @Param("machineCategory") MachineCategory machineCategory,
        @Param("colorMode") ProductColorMode colorMode,
        Pageable pageable
    );

    /**
     * Lists products matching an optional search term and optional filters.
     */
    @Query("""
        SELECT product FROM ProductEntity product
        WHERE (:type IS NULL OR product.type = :type)
          AND (:machineCategory IS NULL OR product.machineCategory = :machineCategory)
          AND (:colorMode IS NULL OR product.colorMode = :colorMode)
          AND (
                LOWER(product.name) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(product.brand) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(product.model) LIKE LOWER(CONCAT('%', :search, '%'))
             OR LOWER(product.productReference) LIKE LOWER(CONCAT('%', :search, '%'))
          )
        """)
    Page<ProductEntity> findAllFiltered(
        @Param("search") String search,
        @Param("type") ProductType type,
        @Param("machineCategory") MachineCategory machineCategory,
        @Param("colorMode") ProductColorMode colorMode,
        Pageable pageable
    );
}

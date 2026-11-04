package ma.wwtrading.orders.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentBankRepository extends JpaRepository<PaymentBankEntity, UUID> {

    List<PaymentBankEntity> findByActiveTrueOrderByCreatedAtDesc();

    List<PaymentBankEntity> findAllByOrderByCreatedAtDesc();

    Optional<PaymentBankEntity> findByReference(String reference);

    Optional<PaymentBankEntity> findByReferenceAndActiveTrue(String reference);

    boolean existsByReference(String reference);

    boolean existsByReferenceAndIdNot(String reference, UUID id);
}


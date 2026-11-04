package ma.wwtrading.users.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<AddressEntity, UUID> {

    List<AddressEntity> findAllByUser_IdOrderByCreatedAtAsc(UUID userId);

    Optional<AddressEntity> findByIdAndUser_Id(UUID id, UUID userId);

    Optional<AddressEntity> findFirstByUser_IdAndDefaultAddressTrue(UUID userId);
}

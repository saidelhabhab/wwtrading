package ma.wwtrading.users.application;

import java.util.List;
import java.util.UUID;

import ma.wwtrading.users.api.AddressRequest;
import ma.wwtrading.users.api.AddressResponse;
import ma.wwtrading.users.domain.AddressEntity;
import ma.wwtrading.users.domain.AddressRepository;
import ma.wwtrading.users.domain.UserEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AddressApplicationService {

    private final AddressRepository addressRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public AddressApplicationService(
        AddressRepository addressRepository,
        AuthenticatedUserService authenticatedUserService
    ) {
        this.addressRepository = addressRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public List<AddressResponse> list(Jwt jwt) {
        UserEntity user = currentUser(jwt);
        return addressRepository.findAllByUser_IdOrderByCreatedAtAsc(user.getId()).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public AddressResponse create(Jwt jwt, AddressRequest request) {
        UserEntity user = currentUser(jwt);
        boolean firstAddress = addressRepository.findAllByUser_IdOrderByCreatedAtAsc(user.getId()).isEmpty();
        AddressEntity address = AddressEntity.create(
            user,
            required(request.firstName()),
            required(request.lastName()),
            optional(request.company()),
            optional(request.phone()),
            required(request.addressLine1()),
            optional(request.addressLine2()),
            required(request.city()),
            optional(request.postalCode()),
            required(request.country()),
            firstAddress
        );
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(Jwt jwt, UUID addressId, AddressRequest request) {
        UserEntity user = currentUser(jwt);
        AddressEntity address = ownedAddress(addressId, user.getId());
        address.updateDetails(
            required(request.firstName()),
            required(request.lastName()),
            optional(request.company()),
            optional(request.phone()),
            required(request.addressLine1()),
            optional(request.addressLine2()),
            required(request.city()),
            optional(request.postalCode()),
            required(request.country())
        );
        return toResponse(address);
    }

    @Transactional
    public void delete(Jwt jwt, UUID addressId) {
        UserEntity user = currentUser(jwt);
        List<AddressEntity> addresses = addressRepository.findAllByUser_IdOrderByCreatedAtAsc(user.getId());
        AddressEntity address = addresses.stream()
            .filter(candidate -> candidate.getId().equals(addressId))
            .findFirst()
            .orElseThrow(AddressApplicationService::notFound);

        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        addressRepository.flush();

        if (wasDefault) {
            addresses.stream()
                .filter(candidate -> !candidate.getId().equals(addressId))
                .findFirst()
                .ifPresent(AddressEntity::makeDefault);
        }
    }

    @Transactional
    public AddressResponse makeDefault(Jwt jwt, UUID addressId) {
        UserEntity user = currentUser(jwt);
        AddressEntity selected = ownedAddress(addressId, user.getId());
        if (selected.isDefaultAddress()) return toResponse(selected);

        addressRepository.findFirstByUser_IdAndDefaultAddressTrue(user.getId()).ifPresent(current -> {
            current.clearDefault();
            addressRepository.flush();
        });
        selected.makeDefault();
        return toResponse(selected);
    }

    private UserEntity currentUser(Jwt jwt) {
        return authenticatedUserService.synchronize(jwt);
    }

    private AddressEntity ownedAddress(UUID addressId, UUID userId) {
        return addressRepository.findByIdAndUser_Id(addressId, userId)
            .orElseThrow(AddressApplicationService::notFound);
    }

    private AddressResponse toResponse(AddressEntity address) {
        return new AddressResponse(
            address.getId(),
            address.getFirstName(),
            address.getLastName(),
            address.getCompany(),
            address.getPhone(),
            address.getAddressLine1(),
            address.getAddressLine2(),
            address.getCity(),
            address.getPostalCode(),
            address.getCountry(),
            address.isDefaultAddress(),
            address.getCreatedAt(),
            address.getUpdatedAt()
        );
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
    }

    private static String required(String value) {
        return value.trim();
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}

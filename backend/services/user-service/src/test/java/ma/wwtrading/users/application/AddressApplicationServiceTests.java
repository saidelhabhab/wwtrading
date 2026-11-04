package ma.wwtrading.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import ma.wwtrading.users.api.AddressRequest;
import ma.wwtrading.users.api.AddressResponse;
import ma.wwtrading.users.domain.AddressEntity;
import ma.wwtrading.users.domain.AddressRepository;
import ma.wwtrading.users.domain.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AddressApplicationServiceTests {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    private AddressApplicationService service;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        service = new AddressApplicationService(addressRepository, authenticatedUserService);
        user = UserEntity.fromIdentity("stored-subject", "client@example.test", "Amina", "Client", "fr");
    }

    @Test
    void listsOnlyAddressesOwnedByJwtEmailUser() {
        AddressEntity address = AddressEntity.create(
            user, "Amina", "Client", null, null, "1 Main Street", null,
            "Agadir", "80000", "MA", true
        );
        when(authenticatedUserService.synchronize(any())).thenReturn(user);
        when(addressRepository.findAllByUser_IdOrderByCreatedAtAsc(user.getId())).thenReturn(List.of(address));

        List<AddressResponse> result = service.list(jwt("client@example.test", null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).city()).isEqualTo("Agadir");
        verify(authenticatedUserService).synchronize(any());
        verify(addressRepository).findAllByUser_IdOrderByCreatedAtAsc(user.getId());
    }

    @Test
    void createsAddressForJwtEmailUser() {
        AddressRequest request = new AddressRequest(
            "Amina", "Client", null, null, "1 Main Street", null,
            "Agadir", "80000", "MA"
        );
        when(authenticatedUserService.synchronize(any())).thenReturn(user);
        when(addressRepository.findAllByUser_IdOrderByCreatedAtAsc(user.getId())).thenReturn(List.of());
        when(addressRepository.save(any(AddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddressResponse result = service.create(jwt("client@example.test", null), request);

        assertThat(result.city()).isEqualTo("Agadir");
        assertThat(result.defaultAddress()).isTrue();
        verify(authenticatedUserService).synchronize(any());
        verify(addressRepository).save(any(AddressEntity.class));
    }

    private Jwt jwt(String email, String subject) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60));
        if (email != null) builder.claim("email", email);
        if (subject != null) builder.subject(subject);
        return builder.build();
    }
}

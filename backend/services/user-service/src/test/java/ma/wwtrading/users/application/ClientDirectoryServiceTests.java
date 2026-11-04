package ma.wwtrading.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import ma.wwtrading.users.api.ClientResponse;
import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ClientDirectoryServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakClient keycloakClient;

    private ClientDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new ClientDirectoryService(keycloakClient, userRepository);
    }

    @Test
    void getClients_returnsClientsFromKeycloakAndRepository() {
        Set<String> clientSubjects = Set.of("client-1", "client-2");
        when(keycloakClient.getClientSubjectIds()).thenReturn(clientSubjects);

        UserEntity user1 = UserEntity.fromIdentity("client-1", "client1@test.ma", "Ahmed", "Alami", "fr");
        user1.setPhone("+212612345678");
        UserEntity user2 = UserEntity.fromIdentity("client-2", "client2@test.ma", "Fatima", "Bennani", "fr");

        Pageable pageable = PageRequest.of(0, 20);
        Page<UserEntity> userPage = new PageImpl<>(List.of(user1, user2), pageable, 2);
        when(userRepository.findByKeycloakSubjectIn(clientSubjects, pageable)).thenReturn(userPage);

        Page<ClientResponse> result = service.getClients(null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);

        ClientResponse first = result.getContent().get(0);
        assertThat(first.id()).isEqualTo("client-1");
        assertThat(first.firstName()).isEqualTo("Ahmed");
        assertThat(first.lastName()).isEqualTo("Alami");
        assertThat(first.email()).isEqualTo("client1@test.ma");
        assertThat(first.phone()).isEqualTo("+212612345678");
        assertThat(first.enabled()).isTrue();

        ClientResponse second = result.getContent().get(1);
        assertThat(second.id()).isEqualTo("client-2");
        assertThat(second.phone()).isNull();
    }

    @Test
    void getClients_emptyClientSubjects_returnsEmptyPageWithoutQueryingRepo() {
        when(keycloakClient.getClientSubjectIds()).thenReturn(Collections.emptySet());
        Pageable pageable = PageRequest.of(0, 20);

        Page<ClientResponse> result = service.getClients(null, pageable);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        verify(userRepository, never()).findByKeycloakSubjectIn(any(), any());
        verify(userRepository, never()).searchClients(any(), any(), any());
    }

    @Test
    void getClients_withKeywordSearch_callsSearchClients() {
        Set<String> clientSubjects = Set.of("client-1");
        when(keycloakClient.getClientSubjectIds()).thenReturn(clientSubjects);

        UserEntity user1 = UserEntity.fromIdentity("client-1", "client1@test.ma", "Ahmed", "Alami", "fr");
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserEntity> userPage = new PageImpl<>(List.of(user1), pageable, 1);
        when(userRepository.searchClients(clientSubjects, "Alami", pageable)).thenReturn(userPage);

        Page<ClientResponse> result = service.getClients("  Alami  ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).lastName()).isEqualTo("Alami");
        verify(userRepository).searchClients(clientSubjects, "Alami", pageable);
        verify(userRepository, never()).findByKeycloakSubjectIn(any(), any());
    }

    @Test
    void getClients_withBlankKeyword_callsFindByKeycloakSubjectIn() {
        Set<String> clientSubjects = Set.of("client-1");
        when(keycloakClient.getClientSubjectIds()).thenReturn(clientSubjects);

        UserEntity user1 = UserEntity.fromIdentity("client-1", "client1@test.ma", "Ahmed", "Alami", "fr");
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserEntity> userPage = new PageImpl<>(List.of(user1), pageable, 1);
        when(userRepository.findByKeycloakSubjectIn(clientSubjects, pageable)).thenReturn(userPage);

        Page<ClientResponse> result = service.getClients("   ", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(userRepository).findByKeycloakSubjectIn(clientSubjects, pageable);
        verify(userRepository, never()).searchClients(any(), any(), any());
    }

    @Test
    void getClient_existingClient_returnsResponse() {
        when(keycloakClient.getClientSubjectIds()).thenReturn(Set.of("client-1"));
        UserEntity user1 = UserEntity.fromIdentity("client-1", "client1@test.ma", "Ahmed", "Alami", "fr");
        user1.setPhone("+212600112233");
        when(userRepository.findByKeycloakSubject("client-1")).thenReturn(Optional.of(user1));

        ClientResponse response = service.getClient("client-1");

        assertThat(response.id()).isEqualTo("client-1");
        assertThat(response.firstName()).isEqualTo("Ahmed");
        assertThat(response.lastName()).isEqualTo("Alami");
        assertThat(response.email()).isEqualTo("client1@test.ma");
        assertThat(response.phone()).isEqualTo("+212600112233");
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void getClient_notInKeycloakClients_throws404() {
        when(keycloakClient.getClientSubjectIds()).thenReturn(Set.of("client-1"));

        assertThatThrownBy(() -> service.getClient("staff-1"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));

        verify(userRepository, never()).findByKeycloakSubject(any());
    }

    @Test
    void getClient_inKeycloakClientsButNotInRepo_throws404() {
        when(keycloakClient.getClientSubjectIds()).thenReturn(Set.of("client-1"));
        when(userRepository.findByKeycloakSubject("client-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClient("client-1"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void getClient_nullOrBlankId_throws404() {
        assertThatThrownBy(() -> service.getClient(null))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));

        assertThatThrownBy(() -> service.getClient("   "))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(404));
    }
}


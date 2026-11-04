package ma.wwtrading.users.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import ma.wwtrading.users.application.ClientDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ClientDirectoryControllerTests {

    @Mock
    private ClientDirectoryService clientDirectoryService;

    private ClientDirectoryController controller;

    @BeforeEach
    void setUp() {
        controller = new ClientDirectoryController(clientDirectoryService);
    }

    @Test
    void listClients_delegatesToService() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ClientResponse> expectedPage = new PageImpl<>(List.of(
            new ClientResponse("client-1", "Ali", "Test", "ali@test.ma", "+212600000000", true)
        ));
        when(clientDirectoryService.getClients("searchQuery", pageable)).thenReturn(expectedPage);

        Page<ClientResponse> result = controller.listClients("searchQuery", pageable);

        assertThat(result).isSameAs(expectedPage);
        verify(clientDirectoryService).getClients("searchQuery", pageable);
    }

    @Test
    void getClient_delegatesToService() {
        ClientResponse expected = new ClientResponse("client-1", "Ali", "Test", "ali@test.ma", "+212600000000", true);
        when(clientDirectoryService.getClient("client-1")).thenReturn(expected);

        ClientResponse result = controller.getClient("client-1");

        assertThat(result).isSameAs(expected);
        verify(clientDirectoryService).getClient("client-1");
    }
}


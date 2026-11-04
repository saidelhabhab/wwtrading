package ma.wwtrading.notifications.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import ma.wwtrading.notifications.application.NotificationApplicationService;
import ma.wwtrading.notifications.application.NotificationNotFoundException;
import ma.wwtrading.notifications.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ContextConfiguration(classes = {
    NotificationController.class,
    ApiExceptionHandler.class,
    SecurityConfig.class
})
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "wwtrading.security.issuer-uri=https://identity.example/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=https://identity.example/realms/wwtrading/protocol/openid-connect/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class NotificationSecurityTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationApplicationService service;

    @Test
    void rejectsUnauthenticatedNotificationAccess() throws Exception {
        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void clientAccessUsesTheJwtSubjectAndFixedNewestFirstPagination() throws Exception {
        when(service.listOwn(eq("client-a"), any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/notifications?page=1&size=10").with(client("client-a")))
            .andExpect(status().isOk());

        verify(service).listOwn(eq("client-a"), any(Pageable.class));
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/notifications?size=0").with(client("client-a")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void anotherClientsNotificationCannotBeMarkedRead() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.markRead("client-a", id)).thenThrow(new NotificationNotFoundException(id));

        mockMvc.perform(patch("/api/notifications/{id}/read", id).with(client("client-a")))
            .andExpect(status().isNotFound());

        verify(service).markRead("client-a", id);
    }

    @Test
    void markAllReadUsesOnlyTheJwtSubject() throws Exception {
        mockMvc.perform(patch("/api/notifications/read-all").with(client("client-a")))
            .andExpect(status().isNoContent());

        verify(service).markAllRead("client-a");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor client(String subject) {
        return jwt()
            .jwt(token -> token.subject(subject))
            .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
    }
}

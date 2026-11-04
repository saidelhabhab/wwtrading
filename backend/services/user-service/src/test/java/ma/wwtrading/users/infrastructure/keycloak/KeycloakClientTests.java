package ma.wwtrading.users.infrastructure.keycloak;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakClientTests {

    private static final String SERVER_URL = "https://keycloak.example.test";
    private static final String REALM = "wwtrading";
    private static final String USER_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private MockRestServiceServer server;
    private KeycloakClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KeycloakClient(builder, SERVER_URL, REALM, "user-service-client", "test-secret");
    }

    @Test
    void assignsClientAsDirectRealmRoleWhenMissing() {
        expectToken();
        server.expect(once(), requestTo(roleMappingsUrl()))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SERVER_URL + "/admin/realms/" + REALM + "/roles/CLIENT"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"id\":\"client-role-id\",\"name\":\"CLIENT\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(roleMappingsUrl()))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
            .andExpect(content().json("[{\"id\":\"client-role-id\",\"name\":\"CLIENT\"}]"))
            .andRespond(withSuccess());

        client.ensureClientRole(USER_ID);

        server.verify();
    }

    @Test
    void existingDirectClientRoleDoesNotCreateDuplicateMapping() {
        expectToken();
        server.expect(once(), requestTo(roleMappingsUrl()))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
            .andRespond(withSuccess("[{\"id\":\"client-role-id\",\"name\":\"CLIENT\"}]",
                MediaType.APPLICATION_JSON));

        client.ensureClientRole(USER_ID);

        server.verify();
    }

    private void expectToken() {
        server.expect(once(), requestTo(SERVER_URL + "/realms/" + REALM + "/protocol/openid-connect/token"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                "{\"access_token\":\"service-token\",\"expires_in\":300,\"token_type\":\"Bearer\"}",
                MediaType.APPLICATION_JSON));
    }

    private String roleMappingsUrl() {
        return SERVER_URL + "/admin/realms/" + REALM + "/users/" + USER_ID + "/role-mappings/realm";
    }
}

package ma.wwtrading.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import ma.wwtrading.maintenance.application.MaintenanceApplicationService;
import ma.wwtrading.maintenance.application.MaintenanceNotFoundException;
import ma.wwtrading.maintenance.domain.MaintenancePriority;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;
import ma.wwtrading.maintenance.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ContextConfiguration(classes = {
    MaintenanceController.class, ApiExceptionHandler.class, SecurityConfig.class, OpenApiConfig.class
})
@TestPropertySource(properties = {
    "wwtrading.security.issuer-uri=https://issuer.example.test/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=https://issuer.example.test/realms/wwtrading/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class MaintenanceSecurityOpenApiTests {

    private static final String ISSUER = "https://issuer.example.test/realms/wwtrading";
    private static final String CREATE_BODY = """
        {"customerReference":"customer-123","productReference":"prod-1","serialNumber":"SN-001","priority":"HIGH","subject":"Printer service","description":"Repair"}
        """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MaintenanceApplicationService service;

    @Test
    void enforcesAuthenticationAndMaintenanceRoleMatrix() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any())).thenReturn(response(id));
        when(service.getById(any(), org.mockito.ArgumentMatchers.eq(id))).thenReturn(response(id));
        when(service.getMyRequests(any(), any())).thenReturn(Page.empty());
        when(service.list(any())).thenReturn(Page.empty());

        mvc.perform(post("/api/maintenance").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/maintenance"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/maintenance/my-requests"))
            .andExpect(status().isUnauthorized());

        // CLIENT role can create, get own request by ID, and list my-requests, but CANNOT view global list
        mvc.perform(post("/api/maintenance").with(role("CLIENT"))
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/maintenance/my-requests").with(role("CLIENT")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/maintenance/{id}", id).with(role("CLIENT")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/maintenance").with(role("CLIENT")))
            .andExpect(status().isForbidden());

        mvc.perform(get("/api/maintenance/{id}", id).with(role("TECHNICIAN")))
            .andExpect(status().isOk());
        mvc.perform(get("/api/maintenance").with(role("TECHNICIAN")))
            .andExpect(status().isOk());

        for (String role : List.of("ADMIN", "MANAGER", "COMMERCIAL")) {
            mvc.perform(post("/api/maintenance").with(role(role))
                    .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated());
            mvc.perform(get("/api/maintenance/{id}", id).with(role(role)))
                .andExpect(status().isOk());
            mvc.perform(get("/api/maintenance").with(role(role)))
                .andExpect(status().isOk());
        }
    }

    @Test
    void returnsSafeValidationNotFoundAndInvalidIdentifierProblems() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(service.getById(any(), org.mockito.ArgumentMatchers.eq(missingId))).thenThrow(new MaintenanceNotFoundException(missingId));

        mvc.perform(post("/api/maintenance").with(role("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"\",\"description\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"));
        mvc.perform(get("/api/maintenance/{id}", missingId).with(role("ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("The maintenance record does not exist"));
        mvc.perform(get("/api/maintenance/not-a-uuid").with(role("ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void enforcesLifecycleRoleMatrix() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.start(id)).thenReturn(response(id, MaintenanceStatus.IN_PROGRESS));
        when(service.complete(org.mockito.ArgumentMatchers.eq(id), any())).thenReturn(response(id, MaintenanceStatus.COMPLETED));
        when(service.cancel(id)).thenReturn(response(id, MaintenanceStatus.CANCELLED));

        mvc.perform(post("/api/maintenance/{id}/start", id))
            .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/maintenance/{id}/start", id).with(role("TECHNICIAN")))
            .andExpect(status().isOk());

        for (String role : List.of("ADMIN", "MANAGER")) {
            mvc.perform(post("/api/maintenance/{id}/start", id).with(role(role)))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/maintenance/{id}/complete", id).with(role(role)))
                .andExpect(status().isOk());
            mvc.perform(post("/api/maintenance/{id}/cancel", id).with(role(role)))
                .andExpect(status().isOk());
        }

        mvc.perform(post("/api/maintenance/{id}/complete", id).with(role("TECHNICIAN")))
            .andExpect(status().isOk());
        mvc.perform(post("/api/maintenance/{id}/cancel", id).with(role("TECHNICIAN")))
            .andExpect(status().isForbidden());

        for (String role : List.of("COMMERCIAL", "CLIENT")) {
            mvc.perform(post("/api/maintenance/{id}/start", id).with(role(role)))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/maintenance/{id}/complete", id).with(role(role)))
                .andExpect(status().isForbidden());
            mvc.perform(post("/api/maintenance/{id}/cancel", id).with(role(role)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void enforcesTechnicianAssignmentRoleMatrix() throws Exception {
        UUID id = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        when(service.assignTechnician(id, technicianId)).thenReturn(response(id, MaintenanceStatus.ASSIGNED, technicianId));
        when(service.unassignTechnician(id)).thenReturn(response(id));

        mvc.perform(put("/api/maintenance/{id}/technician/{technicianId}", id, technicianId))
            .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/maintenance/{id}/technician/{technicianId}", id, technicianId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized());

        for (String role : List.of("ADMIN", "MANAGER")) {
            mvc.perform(put("/api/maintenance/{id}/technician/{technicianId}", id, technicianId)
                    .with(role(role)))
                .andExpect(status().isOk());
            mvc.perform(delete("/api/maintenance/{id}/technician", id).with(role(role)))
                .andExpect(status().isOk());
        }

        for (String role : List.of("COMMERCIAL", "TECHNICIAN", "CLIENT")) {
            mvc.perform(put("/api/maintenance/{id}/technician/{technicianId}", id, technicianId)
                    .with(role(role)))
                .andExpect(status().isForbidden());
            mvc.perform(delete("/api/maintenance/{id}/technician", id).with(role(role)))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void validatesIssuerAudienceAndStrictRealmRoles() {
        var security = new SecurityConfig();
        assertThat(security.jwtValidator(ISSUER, "wwtrading-api")
            .validate(token(ISSUER, List.of("wwtrading-api"))).hasErrors()).isFalse();
        assertThat(security.jwtValidator(ISSUER, "wwtrading-api")
            .validate(token("https://wrong.example.test", List.of("wwtrading-api"))).hasErrors()).isTrue();
        assertThat(security.jwtValidator(ISSUER, "wwtrading-api")
            .validate(token(ISSUER, List.of("other-api"))).hasErrors()).isTrue();

        var authentication = security.jwtAuthenticationConverter().convert(Jwt.withTokenValue("token")
            .header("alg", "none").subject("user-1")
            .claim("realm_access", Map.of("roles", List.of("TECHNICIAN", "UNTRUSTED"))).build());
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_TECHNICIAN");
    }

    @Test
    void documentsBearerSecurityAndMaintenanceResponses() {
        OpenAPI openApi = new OpenApiConfig().maintenanceServiceOpenApi();
        openApi.setPaths(new Paths()
            .addPathItem("/api/maintenance", new PathItem().get(new Operation()).post(new Operation()))
            .addPathItem("/api/maintenance/{id}", new PathItem().get(new Operation()))
            .addPathItem("/api/maintenance/{id}/start", new PathItem().post(new Operation()))
            .addPathItem("/api/maintenance/{id}/complete", new PathItem().post(new Operation()))
            .addPathItem("/api/maintenance/{id}/cancel", new PathItem().post(new Operation()))
            .addPathItem("/api/maintenance/{id}/technician/{technicianId}", new PathItem().put(new Operation()))
            .addPathItem("/api/maintenance/{id}/technician", new PathItem().delete(new Operation())));

        new OpenApiConfig().maintenanceEndpointResponses().customise(openApi);

        var scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getPaths().get("/api/maintenance").getPost().getSecurity()).isNotEmpty();
        assertThat(openApi.getPaths().get("/api/maintenance").getPost().getResponses())
            .containsKeys("201", "400", "401", "403", "409", "500");
        assertThat(openApi.getPaths().get("/api/maintenance").getGet().getResponses())
            .containsKeys("200", "400", "401", "403", "500");
        assertThat(openApi.getPaths().get("/api/maintenance/{id}").getGet().getResponses())
            .containsKeys("200", "400", "401", "403", "404", "500");
        for (String action : List.of("start", "complete", "cancel")) {
            Operation operation = openApi.getPaths().get("/api/maintenance/{id}/" + action).getPost();
            assertThat(operation.getSecurity()).isNotEmpty();
            assertThat(operation.getResponses())
                .containsKeys("200", "400", "401", "403", "404", "409", "500");
        }
        Operation assign = openApi.getPaths()
            .get("/api/maintenance/{id}/technician/{technicianId}").getPut();
        assertThat(assign.getSecurity()).isNotEmpty();
        assertThat(assign.getResponses()).containsKeys("200", "400", "401", "403", "404", "500");
        Operation unassign = openApi.getPaths().get("/api/maintenance/{id}/technician").getDelete();
        assertThat(unassign.getSecurity()).isNotEmpty();
        assertThat(unassign.getResponses()).containsKeys("200", "400", "401", "403", "404", "500");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private Jwt token(String issuer, List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "none").subject("user-1")
            .issuer(issuer).audience(audience).issuedAt(now.minusSeconds(30)).expiresAt(now.plusSeconds(300)).build();
    }

    private MaintenanceResponse response(UUID id) {
        return response(id, MaintenanceStatus.OPEN);
    }

    private MaintenanceResponse response(UUID id, MaintenanceStatus status) {
        return response(id, status, null);
    }

    private MaintenanceResponse response(UUID id, MaintenanceStatus status, UUID technicianId) {
        return new MaintenanceResponse(
            id, "customer-123", "product-456", "SN-001", MaintenancePriority.HIGH, technicianId, "Printer service", "Repair", null,
            status, Instant.now(), Instant.now()
        );
    }
}

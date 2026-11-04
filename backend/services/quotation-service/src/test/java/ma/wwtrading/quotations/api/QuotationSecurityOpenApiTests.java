package ma.wwtrading.quotations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import ma.wwtrading.quotations.application.QuotationApplicationService;
import ma.wwtrading.quotations.domain.QuotationStatus;
import ma.wwtrading.quotations.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@ContextConfiguration(classes = {
    QuotationController.class,
    ApiExceptionHandler.class,
    SecurityConfig.class,
    OpenApiConfig.class
})
@TestPropertySource(properties = {
    "wwtrading.security.issuer-uri=https://issuer.example.test/realms/wwtrading",
    "wwtrading.security.jwk-set-uri=https://issuer.example.test/realms/wwtrading/certs",
    "wwtrading.security.audience=wwtrading-api"
})
class QuotationSecurityOpenApiTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private QuotationApplicationService service;

    private QuotationResponse sampleResponse(UUID id, QuotationStatus status) {
        return new QuotationResponse(
            id, "client@example.com", "VENTE", "Bizhub C250i", "Konica Minolta", 1,
            "Need printer", "Jean Dupont", "ACME", "0612345678", "client@example.com",
            "Kénitra", "ASAP", new BigDecimal("15000.00"), null, status,
            null, null, Instant.now(), Instant.now()
        );
    }

    @Test
    void allowsPublicToCreateQuotation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(sampleResponse(id, QuotationStatus.PENDING));

        mvc.perform(post("/api/quotations")
                .contentType("application/json")
                .content("""
                    {
                      "serviceType": "VENTE",
                      "requestedEquipment": "Bizhub C250i",
                      "brandModel": "Konica Minolta",
                      "quantity": 1,
                      "description": "Need printer",
                      "fullName": "Jean Dupont",
                      "company": "ACME",
                      "phone": "0612345678",
                      "email": "client@example.com",
                      "city": "Kénitra",
                      "desiredTimeline": "ASAP"
                    }
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    void allowsClientToListOwnQuotationsButForbiddenForGlobalList() throws Exception {
        when(service.getMyQuotations(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(Page.empty());

        mvc.perform(get("/api/quotations/my-quotations")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isOk());

        mvc.perform(get("/api/quotations")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsCommercialRoleToListAllQuotations() throws Exception {
        when(service.getAllQuotations(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(Page.empty());

        mvc.perform(get("/api/quotations")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COMMERCIAL"))))
            .andExpect(status().isOk());
    }

    @Test
    void declaresBearerJwtOpenApiScheme() {
        var openApi = new OpenApiConfig().quotationServiceOpenApi();

        var scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity()).isNotEmpty();
    }

    @Test
    void allowsCommercialButRejectsClientStatusUpdates() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateQuotationStatus(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.any()))
            .thenReturn(sampleResponse(id, QuotationStatus.IN_REVIEW));

        String request = "{\"status\":\"IN_REVIEW\",\"commercialNotes\":\"Under review\"}";

        mvc.perform(put("/api/quotations/{id}/status", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_COMMERCIAL")))
                .contentType("application/json")
                .content(request))
            .andExpect(status().isOk());

        mvc.perform(put("/api/quotations/{id}/status", id)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT")))
                .contentType("application/json")
                .content(request))
            .andExpect(status().isForbidden());
    }

    @Test
    void permitsActuatorHealthWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isNotFound()); // MockMvc has no actuator controller registered, so 404 proves Security filter did not deny or challenge with 401/403
    }
}

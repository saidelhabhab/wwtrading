package ma.wwtrading.quotations.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.quotations.application.QuotationApplicationService;
import ma.wwtrading.quotations.application.QuotationNotFoundException;
import ma.wwtrading.quotations.domain.QuotationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class QuotationControllerTests {

    private final QuotationApplicationService service = mock(QuotationApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new QuotationController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    private QuotationResponse sampleResponse(UUID id, QuotationStatus status) {
        return new QuotationResponse(
            id, "client@example.com", "VENTE", "Bizhub C250i", "Konica Minolta", 2,
            "Need 2 color printers", "Jean Dupont", "ACME", "0612345678",
            "client@example.com", "Kénitra", "ASAP", new BigDecimal("30000.00"),
            "Urgent request", status, new BigDecimal("28000.00"), "Commercial note",
            Instant.now(), Instant.now()
        );
    }

    @Test
    void createsQuotationAndReturns201Location() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any(), any())).thenReturn(sampleResponse(id, QuotationStatus.PENDING));

        mvc.perform(post("/api/quotations")
                .header("Idempotency-Key", "idemp-abc-123")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceType": "VENTE",
                      "requestedEquipment": "Bizhub C250i",
                      "brandModel": "Konica Minolta",
                      "quantity": 2,
                      "description": "Need 2 color printers",
                      "fullName": "Jean Dupont",
                      "company": "ACME",
                      "phone": "0612345678",
                      "email": "client@example.com",
                      "city": "Kénitra",
                      "desiredTimeline": "ASAP",
                      "estimatedBudget": 30000.00,
                      "additionalMessage": "Urgent request"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/quotations/" + id))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.serviceType").value("VENTE"));

        verify(service).create(any(), eq("idemp-abc-123"), any());
    }

    @Test
    void rejectsInvalidCreateRequestBeforeApplicationLayer() throws Exception {
        mvc.perform(post("/api/quotations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceType": "",
                      "quantity": 0,
                      "description": "",
                      "fullName": "",
                      "phone": "",
                      "email": "not-an-email",
                      "city": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"));

        verifyNoInteractions(service);
    }

    @Test
    void returnsClientQuotations() throws Exception {
        UUID id = UUID.randomUUID();
        var page = new PageImpl<>(List.of(sampleResponse(id, QuotationStatus.PENDING)), PageRequest.of(0, 20), 1);
        when(service.getMyQuotations(any(), any())).thenReturn(page);

        mvc.perform(get("/api/quotations/my-quotations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(id.toString()));
    }

    @Test
    void cancelsPendingQuotation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.cancelQuotation(any(), eq(id))).thenReturn(sampleResponse(id, QuotationStatus.CANCELLED));

        mvc.perform(put("/api/quotations/{id}/cancel", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void updatesStatusByStaff() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateQuotationStatus(any(), eq(id), any())).thenReturn(sampleResponse(id, QuotationStatus.QUOTED));

        mvc.perform(put("/api/quotations/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "QUOTED",
                      "proposedAmount": 28000.00,
                      "commercialNotes": "Offre spéciale avec livraison offerte"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("QUOTED"));
    }

    @Test
    void returnsSafeNotFoundProblem() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getQuotationById(any(), eq(id))).thenThrow(new QuotationNotFoundException(id));

        mvc.perform(get("/api/quotations/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Quotation not found"));
    }
}

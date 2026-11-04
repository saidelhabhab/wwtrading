package ma.wwtrading.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.maintenance.application.MaintenanceApplicationService;
import ma.wwtrading.maintenance.application.MaintenanceMapper;
import ma.wwtrading.maintenance.application.MaintenanceNotFoundException;
import ma.wwtrading.maintenance.domain.MaintenanceEntity;
import ma.wwtrading.maintenance.domain.MaintenancePriority;
import ma.wwtrading.maintenance.domain.MaintenanceRepository;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MaintenanceBehaviorTests {

    private final MaintenanceRepository repository = mock(MaintenanceRepository.class);
    private final MaintenanceMapper mapper = Mappers.getMapper(MaintenanceMapper.class);
    private final MaintenanceApplicationService application = new MaintenanceApplicationService(repository, mapper);
    private final MaintenanceApplicationService controllerService = mock(MaintenanceApplicationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MaintenanceController(controllerService))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .build();
    }

    @Test
    void createsMaintenanceWithServerManagedOpenStatus() {
        when(repository.saveAndFlush(any(MaintenanceEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = application.create(new CreateMaintenanceRequest(
            "customer-123", "product-456", "SN-998877", MaintenancePriority.HIGH, "Printer service", "Replace the worn roller"
        ));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(MaintenanceStatus.OPEN);
        assertThat(response.customerReference()).isEqualTo("customer-123");
        assertThat(response.serialNumber()).isEqualTo("SN-998877");
        assertThat(response.priority()).isEqualTo(MaintenancePriority.HIGH);
        assertThat(response.subject()).isEqualTo("Printer service");
        verify(repository).saveAndFlush(any(MaintenanceEntity.class));
    }

    @Test
    void authenticatedOwnerGetsExistingMaintenanceAndMissingRecordIsRejected() {
        UUID id = UUID.randomUUID();
        var maintenance = maintenance();
        when(repository.findById(id)).thenReturn(Optional.of(maintenance));
        JwtAuthenticationToken owner = authentication("customer-123");

        assertThat(application.getById(owner, id).subject()).isEqualTo("Printer service");

        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> application.getById(owner, missingId))
            .isInstanceOf(MaintenanceNotFoundException.class);
    }

    @Test
    void mapsPaginatedMaintenanceRecords() {
        var pageable = PageRequest.of(1, 10);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(maintenance()), pageable, 11));

        var page = application.list(pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getContent().getFirst()).isInstanceOf(MaintenanceResponse.class);
    }

    @Test
    void validatesCreationRequestFields() {
        try (var validatorFactory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var invalid = new CreateMaintenanceRequest("x".repeat(65), "x".repeat(65), "x".repeat(121), MaintenancePriority.LOW, "", " ");

            assertThat(validator.validate(invalid))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("customerReference", "productReference", "serialNumber", "subject", "description");
        }
    }

    @Test
    void controllerCreatesOnlyResponseDtoAndReturnsLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(controllerService.create(any(), any())).thenReturn(response(id));

        mvc.perform(post("/api/maintenance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerReference\":\"customer-123\",\"subject\":\"Printer service\",\"description\":\"Repair\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/maintenance/" + id))
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void controllerRejectsInvalidInputAndReturnsSafeProblems() throws Exception {
        mvc.perform(post("/api/maintenance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"\",\"description\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"));
        verifyNoInteractions(controllerService);

        UUID missingId = UUID.randomUUID();
        when(controllerService.getById(any(), org.mockito.ArgumentMatchers.eq(missingId))).thenThrow(new MaintenanceNotFoundException(missingId));
        mvc.perform(get("/api/maintenance/{id}", missingId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("The maintenance record does not exist"));

        mvc.perform(get("/api/maintenance/not-a-uuid"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    private MaintenanceEntity maintenance() {
        return new MaintenanceEntity("customer-123", "product-456", "SN-123", MaintenancePriority.MEDIUM, "Printer service", "Repair");
    }

    private MaintenanceResponse response(UUID id) {
        return new MaintenanceResponse(
            id, "customer-123", "product-456", "SN-123", MaintenancePriority.MEDIUM, null, "Printer service", "Repair", null, MaintenanceStatus.OPEN, null, null
        );
    }

    private JwtAuthenticationToken authentication(String email) {
        Jwt token = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(UUID.randomUUID().toString())
            .claim("email", email)
            .build();
        return new JwtAuthenticationToken(token);
    }
}

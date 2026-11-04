package ma.wwtrading.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.maintenance.application.MaintenanceApplicationService;
import ma.wwtrading.maintenance.application.MaintenanceMapper;
import ma.wwtrading.maintenance.application.MaintenanceNotFoundException;
import ma.wwtrading.maintenance.domain.MaintenanceEntity;
import ma.wwtrading.maintenance.domain.MaintenancePriority;
import ma.wwtrading.maintenance.domain.MaintenanceRepository;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MaintenanceTechnicianAssignmentTests {

    @Test
    void entitySupportsIdempotentAssignmentReassignmentAndUnassignment() {
        MaintenanceEntity maintenance = maintenance();
        UUID firstTechnician = UUID.randomUUID();
        UUID secondTechnician = UUID.randomUUID();

        maintenance.assignTechnician(firstTechnician);
        assertThat(maintenance.getTechnicianId()).isEqualTo(firstTechnician);
        maintenance.assignTechnician(secondTechnician);
        assertThat(maintenance.getTechnicianId()).isEqualTo(secondTechnician);
        maintenance.assignTechnician(secondTechnician);
        assertThat(maintenance.getTechnicianId()).isEqualTo(secondTechnician);
        maintenance.unassignTechnician();
        maintenance.unassignTechnician();
        assertThat(maintenance.getTechnicianId()).isNull();
        assertThat(maintenance.getStatus()).isEqualTo(MaintenanceStatus.ASSIGNED);
    }

    @Test
    void applicationUsesLockedMaintenanceAndReturnsOnlyTechnicianReference() {
        MaintenanceRepository repository = mock(MaintenanceRepository.class);
        MaintenanceApplicationService application = new MaintenanceApplicationService(
            repository, Mappers.getMapper(MaintenanceMapper.class)
        );
        MaintenanceEntity maintenance = maintenance();
        UUID maintenanceId = UUID.randomUUID();
        UUID firstTechnician = UUID.randomUUID();
        UUID secondTechnician = UUID.randomUUID();
        when(repository.findByIdForUpdate(maintenanceId)).thenReturn(Optional.of(maintenance));
        when(repository.saveAndFlush(any(MaintenanceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(application.assignTechnician(maintenanceId, firstTechnician).technicianId())
            .isEqualTo(firstTechnician);
        assertThat(application.assignTechnician(maintenanceId, secondTechnician).technicianId())
            .isEqualTo(secondTechnician);
        assertThat(application.assignTechnician(maintenanceId, secondTechnician).technicianId())
            .isEqualTo(secondTechnician);
        assertThat(application.unassignTechnician(maintenanceId).technicianId()).isNull();
        assertThat(application.unassignTechnician(maintenanceId).technicianId()).isNull();
        assertThat(maintenance.getStatus()).isEqualTo(MaintenanceStatus.ASSIGNED);
    }

    @Test
    void applicationRejectsMissingMaintenance() {
        MaintenanceRepository repository = mock(MaintenanceRepository.class);
        MaintenanceApplicationService application = new MaintenanceApplicationService(
            repository, Mappers.getMapper(MaintenanceMapper.class)
        );
        UUID missingId = UUID.randomUUID();
        when(repository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> application.assignTechnician(missingId, UUID.randomUUID()))
            .isInstanceOf(MaintenanceNotFoundException.class);
        assertThatThrownBy(() -> application.unassignTechnician(missingId))
            .isInstanceOf(MaintenanceNotFoundException.class);
    }

    @Test
    void apiValidatesIdentifiersAndReturnsSafeResponses() throws Exception {
        MaintenanceApplicationService service = mock(MaintenanceApplicationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MaintenanceController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        UUID id = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(service.assignTechnician(id, technicianId)).thenReturn(response(id, technicianId));
        when(service.unassignTechnician(id)).thenReturn(response(id, null));
        when(service.assignTechnician(missingId, technicianId)).thenThrow(new MaintenanceNotFoundException(missingId));

        mvc.perform(put("/api/maintenance/{id}/technician/{technicianId}", id, technicianId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.technicianId").value(technicianId.toString()));
        mvc.perform(delete("/api/maintenance/{id}/technician", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.technicianId").doesNotExist());
        mvc.perform(put("/api/maintenance/not-a-uuid/technician/{technicianId}", technicianId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"));
        mvc.perform(put("/api/maintenance/{id}/technician/not-a-uuid", id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"));
        mvc.perform(put("/api/maintenance/{id}/technician/{technicianId}", missingId, technicianId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Maintenance not found"));
    }

    private MaintenanceEntity maintenance() {
        return new MaintenanceEntity("customer-123", "product-456", "Printer service", "Repair");
    }

    private MaintenanceResponse response(UUID id, UUID technicianId) {
        return new MaintenanceResponse(
            id, "customer-123", "product-456", null, MaintenancePriority.MEDIUM, technicianId, "Printer service", "Repair", null,
            MaintenanceStatus.OPEN, null, null
        );
    }
}

package ma.wwtrading.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import ma.wwtrading.maintenance.application.MaintenanceApplicationService;
import ma.wwtrading.maintenance.application.MaintenanceDataConflictException;
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

class MaintenanceLifecycleTests {

    @Test
    void startAllowsOnlyAcceptedMaintenance() {
        MaintenanceEntity maintenance = maintenanceAt(MaintenanceStatus.ACCEPTED);
        maintenance.start();
        assertThat(maintenance.getStatus()).isEqualTo(MaintenanceStatus.IN_PROGRESS);

        assertInvalid(MaintenanceStatus.OPEN, entity -> entity.start());
        assertInvalid(MaintenanceStatus.ASSIGNED, entity -> entity.start());
        assertInvalid(MaintenanceStatus.IN_PROGRESS, entity -> entity.start());
        assertInvalid(MaintenanceStatus.COMPLETED, entity -> entity.start());
        assertInvalid(MaintenanceStatus.CANCELLED, entity -> entity.start());
    }

    @Test
    void completionFollowsPendingClientConfirmation() {
        MaintenanceEntity maintenance = maintenanceAt(MaintenanceStatus.IN_PROGRESS);
        maintenance.submitCompletion("Fuser replaced", "Replaced fuser unit and tested 50 pages.");
        assertThat(maintenance.getStatus()).isEqualTo(MaintenanceStatus.COMPLETION_PENDING_CONFIRMATION);
        assertThat(maintenance.getCompletionSummary()).isEqualTo("Fuser replaced");
        assertThat(maintenance.getInterventionNotes()).isEqualTo("Replaced fuser unit and tested 50 pages.");

        maintenance.confirmCompletion();
        assertThat(maintenance.getStatus()).isEqualTo(MaintenanceStatus.COMPLETED);

        assertInvalid(MaintenanceStatus.OPEN, entity -> entity.submitCompletion(null, null));
        assertInvalid(MaintenanceStatus.ACCEPTED, entity -> entity.submitCompletion(null, null));
        assertInvalid(MaintenanceStatus.COMPLETED, entity -> entity.confirmCompletion());
    }

    @Test
    void cancelAllowsOnlyOpenMaintenance() {
        MaintenanceEntity maintenance = maintenanceAt(MaintenanceStatus.OPEN);
        maintenance.cancel();
        assertThat(maintenance.getStatus()).isEqualTo(MaintenanceStatus.CANCELLED);

        assertInvalid(MaintenanceStatus.IN_PROGRESS, entity -> entity.cancel());
        assertInvalid(MaintenanceStatus.COMPLETED, entity -> entity.cancel());
        assertInvalid(MaintenanceStatus.CANCELLED, entity -> entity.cancel());
    }

    @Test
    void applicationCommandsLockAndPersistValidTransitions() {
        MaintenanceRepository repository = mock(MaintenanceRepository.class);
        MaintenanceApplicationService application = new MaintenanceApplicationService(
            repository, Mappers.getMapper(MaintenanceMapper.class)
        );
        UUID startId = UUID.randomUUID();
        UUID completeId = UUID.randomUUID();
        UUID cancelId = UUID.randomUUID();
        when(repository.findByIdForUpdate(startId)).thenReturn(Optional.of(maintenanceAt(MaintenanceStatus.ACCEPTED)));
        when(repository.findByIdForUpdate(completeId))
            .thenReturn(Optional.of(maintenanceAt(MaintenanceStatus.IN_PROGRESS)));
        when(repository.findByIdForUpdate(cancelId)).thenReturn(Optional.of(maintenanceAt(MaintenanceStatus.OPEN)));
        when(repository.saveAndFlush(any(MaintenanceEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(application.start(startId).status()).isEqualTo(MaintenanceStatus.IN_PROGRESS);
        assertThat(application.complete(completeId, "Completed OK").status()).isEqualTo(MaintenanceStatus.COMPLETED);
        assertThat(application.cancel(cancelId).status()).isEqualTo(MaintenanceStatus.CANCELLED);
        verify(repository).findByIdForUpdate(startId);
        verify(repository).findByIdForUpdate(completeId);
        verify(repository).findByIdForUpdate(cancelId);
    }

    @Test
    void applicationReturnsNotFoundAndConflictInsteadOfInternalErrors() {
        MaintenanceRepository repository = mock(MaintenanceRepository.class);
        MaintenanceApplicationService application = new MaintenanceApplicationService(
            repository, Mappers.getMapper(MaintenanceMapper.class)
        );
        UUID missingId = UUID.randomUUID();
        UUID invalidTransitionId = UUID.randomUUID();
        when(repository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());
        when(repository.findByIdForUpdate(invalidTransitionId))
            .thenReturn(Optional.of(maintenanceAt(MaintenanceStatus.COMPLETED)));

        assertThatThrownBy(() -> application.start(missingId))
            .isInstanceOf(MaintenanceNotFoundException.class);
        assertThatThrownBy(() -> application.cancel(invalidTransitionId))
            .isInstanceOf(MaintenanceDataConflictException.class);
    }

    @Test
    void lifecycleApiReturnsSafeStatusCodes() throws Exception {
        MaintenanceApplicationService service = mock(MaintenanceApplicationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MaintenanceController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        UUID id = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        when(service.start(isNull(), eq(id))).thenReturn(response(id, MaintenanceStatus.IN_PROGRESS));
        when(service.complete(eq(id), any())).thenThrow(new MaintenanceDataConflictException());
        when(service.cancel(missingId)).thenThrow(new MaintenanceNotFoundException(missingId));

        mvc.perform(post("/api/maintenance/{id}/start", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/maintenance/not-a-uuid/start"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid request"));
        mvc.perform(post("/api/maintenance/{id}/complete", id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Data conflict"));
        mvc.perform(post("/api/maintenance/{id}/cancel", missingId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.title").value("Maintenance not found"));
    }

    private void assertInvalid(
        MaintenanceStatus status,
        java.util.function.Consumer<MaintenanceEntity> transition
    ) {
        assertThatThrownBy(() -> transition.accept(maintenanceAt(status)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid maintenance status transition");
    }

    private MaintenanceEntity maintenanceAt(MaintenanceStatus status) {
        MaintenanceEntity maintenance = new MaintenanceEntity(
            "customer-123", "product-456", "Printer service", "Repair"
        );
        switch (status) {
            case OPEN -> { }
            case SUBMITTED -> maintenance.submit();
            case ASSIGNED -> {
                maintenance.submit();
                maintenance.assignTechnician(UUID.randomUUID());
            }
            case ACCEPTED -> {
                maintenance.submit();
                maintenance.assignTechnician(UUID.randomUUID());
                maintenance.accept();
            }
            case REJECTED -> {
                maintenance.submit();
                maintenance.assignTechnician(UUID.randomUUID());
                maintenance.reject();
            }
            case IN_PROGRESS -> {
                maintenance.submit();
                maintenance.assignTechnician(UUID.randomUUID());
                maintenance.accept();
                maintenance.start();
            }
            case WAITING_FOR_PART -> {
                maintenance.submit();
                maintenance.assignTechnician(UUID.randomUUID());
                maintenance.accept();
                maintenance.start();
                maintenance.waitOnPart();
            }
            case COMPLETION_PENDING_CONFIRMATION -> {
                maintenance.submit();
                maintenance.assignTechnician(UUID.randomUUID());
                maintenance.accept();
                maintenance.start();
                maintenance.submitCompletion(null, null);
            }
            case COMPLETED -> {
                maintenance.submit();
                maintenance.assignTechnician(UUID.randomUUID());
                maintenance.accept();
                maintenance.start();
                maintenance.submitCompletion(null, null);
                maintenance.confirmCompletion();
            }
            case CANCELLED -> maintenance.cancel();
        }
        return maintenance;
    }

    private MaintenanceResponse response(UUID id, MaintenanceStatus status) {
        return new MaintenanceResponse(
            id, "customer-123", "product-456", null, MaintenancePriority.MEDIUM, null, "Printer service", "Repair", null,
            status, Instant.now(), Instant.now()
        );
    }
}

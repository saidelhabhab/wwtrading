package ma.wwtrading.maintenance.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import ma.wwtrading.maintenance.application.MaintenanceApplicationService;
import ma.wwtrading.maintenance.application.MaintenanceMapper;
import ma.wwtrading.maintenance.domain.MaintenanceHistoryEntity;
import ma.wwtrading.maintenance.domain.MaintenanceHistoryRepository;
import ma.wwtrading.maintenance.domain.MaintenanceRepository;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class MaintenanceHistoryTests {

    @Test
    void serviceReturnsHistoryPageInReverseChronologicalOrder() {
        MaintenanceRepository repository = mock(MaintenanceRepository.class);
        MaintenanceHistoryRepository historyRepository = mock(MaintenanceHistoryRepository.class);
        MaintenanceApplicationService service = new MaintenanceApplicationService(repository, historyRepository,
            Mappers.getMapper(MaintenanceMapper.class));
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);
        when(historyRepository.findByMaintenanceIdOrderByCreatedAtDesc(any(), any()))
            .thenReturn(new PageImpl<>(List.of(
                history(id, MaintenanceHistoryEntity.EventType.STATUS_CHANGED, null, null, null),
                history(id, MaintenanceHistoryEntity.EventType.CREATED, null, null, null)
            ), PageRequest.of(0, 20), 2));

        Page<MaintenanceHistoryResponse> history = service.getHistory(id, PageRequest.of(0, 20));

        assertThat(history.getContent()).hasSize(2);
        assertThat(history.getContent().get(0).eventType()).isEqualTo(MaintenanceHistoryEntity.EventType.STATUS_CHANGED);
        assertThat(history.getContent().get(1).eventType()).isEqualTo(MaintenanceHistoryEntity.EventType.CREATED);
    }

    @Test
    void historyIsAppendOnlyAndReadOnly() {
        var created = MaintenanceHistoryEntity.created(UUID.randomUUID());
        assertThat(created.getEventType()).isEqualTo(MaintenanceHistoryEntity.EventType.CREATED);
        assertThat(MaintenanceHistoryEntity.class.getDeclaredMethods())
            .extracting(java.lang.reflect.Method::getName)
            .doesNotContain("setEventType", "setFromStatus", "setToStatus", "setTechnicianId", "setMaintenanceId");
    }

    private MaintenanceHistoryEntity history(UUID maintenanceId, MaintenanceHistoryEntity.EventType type,
                                            ma.wwtrading.maintenance.domain.MaintenanceStatus from,
                                            ma.wwtrading.maintenance.domain.MaintenanceStatus to,
                                            UUID technicianId) {
        return switch (type) {
            case CREATED -> MaintenanceHistoryEntity.created(maintenanceId);
            case STATUS_CHANGED -> MaintenanceHistoryEntity.statusChanged(maintenanceId, from, to);
            case TECHNICIAN_ASSIGNED -> MaintenanceHistoryEntity.technicianAssigned(maintenanceId, technicianId);
            case TECHNICIAN_REASSIGNED -> MaintenanceHistoryEntity.technicianReassigned(maintenanceId, technicianId);
            case TECHNICIAN_UNASSIGNED -> MaintenanceHistoryEntity.technicianUnassigned(maintenanceId, technicianId);
        };
    }
}

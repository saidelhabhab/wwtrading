package ma.wwtrading.maintenance.application;

import ma.wwtrading.maintenance.api.CreateMaintenanceRequest;
import ma.wwtrading.maintenance.api.MaintenanceResponse;
import ma.wwtrading.maintenance.domain.MaintenanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MaintenanceMapper {

    MaintenanceResponse toResponse(MaintenanceEntity maintenance);

    default MaintenanceEntity toEntity(CreateMaintenanceRequest request) {
        return toEntity(request.customerReference() != null ? request.customerReference().trim() : null, request);
    }

    default MaintenanceEntity toEntity(String customerReference, CreateMaintenanceRequest request) {
        String ref = customerReference != null ? customerReference.trim() : (request.customerReference() != null ? request.customerReference().trim() : "anonymous");
        return new MaintenanceEntity(
            ref,
            request.clientReference(),
            request.productReference(),
            request.serialNumber(),
            request.priority(),
            request.subject().trim(),
            request.description().trim()
        );
    }
}

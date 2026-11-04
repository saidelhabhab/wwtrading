package ma.wwtrading.quotations.prospects.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.wwtrading.quotations.prospects.domain.ProspectStatus;

public final class ProspectDtos {

    private ProspectDtos() {
    }

    public record ProspectRequest(
            @NotBlank
            @Size(max = 200) String companyName,
            @NotBlank
            @Size(max = 120) String contactName,
            @Size(max = 30) String phone,
            @Email
            @Size(max = 160) String email,
            @Size(max = 100) String city,
            @Size(max = 160) String activity,
            @Size(max = 80) String source,
            ProspectStatus status,
            @Size(max = 10_000) String notes) {

    }

    public record ProspectResponse(
            UUID id,
            String companyName,
            String contactName,
            String phone,
            String email,
            String city,
            String activity,
            String source,
            ProspectStatus status,
            String notes,
            String assignedCommercialSubject,
            Instant createdAt,
            Instant updatedAt) {

    }
}

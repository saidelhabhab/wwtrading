package ma.wwtrading.orders.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import ma.wwtrading.orders.application.DeliverySettingsApplicationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/delivery-settings")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class AdminDeliverySettingsController {

    private final DeliverySettingsApplicationService service;

    public AdminDeliverySettingsController(DeliverySettingsApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get current delivery fee settings for administration")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery settings returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient role (ADMIN required)")
    })
    public DeliverySettingsResponse getSettings() {
        return service.getSettings();
    }

    @PutMapping
    @Operation(summary = "Update delivery fee settings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Delivery settings updated"),
            @ApiResponse(responseCode = "400", description = "Invalid delivery settings"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Insufficient role (ADMIN required)")
    })
    public DeliverySettingsResponse updateSettings(@Valid @RequestBody UpdateDeliverySettingsRequest request) {
        return service.updateSettings(request);
    }

    @PatchMapping
    @Operation(summary = "Partially update delivery fee settings (e.g. toggle feesEnabled)")
    public DeliverySettingsResponse patchSettings(@Valid @RequestBody UpdateDeliverySettingsRequest request) {
        return service.updateSettings(request);
    }
}


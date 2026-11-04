package ma.wwtrading.users.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.wwtrading.users.application.TechnicianDirectoryService;
import ma.wwtrading.users.application.StaffUserApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Directory", description = "Internal directory endpoints for role-based user lookups")
@SecurityRequirement(name = "keycloakBearer")
public class UserDirectoryController {

    private final TechnicianDirectoryService directoryService;
    private final StaffUserApplicationService staffUserService;

    public UserDirectoryController(TechnicianDirectoryService directoryService,
            StaffUserApplicationService staffUserService) {
        this.directoryService = directoryService;
        this.staffUserService = staffUserService;
    }

    @GetMapping(params = "role")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get directory of technicians for maintenance assignment")
    public List<TechnicianDirectoryResponse> listUsers(
        @RequestParam(name = "role") String role
    ) {
        if (!"TECHNICIAN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported role filter: TECHNICIAN");
        }
        return directoryService.getTechnicians();
    }

    @GetMapping(params = "!role")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "List staff users visible to the authenticated management role")
    public List<StaffUserResponse> listStaffUsers(Authentication authentication) {
        return staffUserService.getStaffUsers(authentication);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Create a staff user in Keycloak and the application directory")
    public ResponseEntity<StaffUserResponse> createUser(
            Authentication authentication,
            @Valid @RequestBody CreateStaffUserRequest request) {
        StaffUserResponse created = staffUserService.create(authentication, request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reset user password directly or via email (ADMIN only)")
    public ResponseEntity<AdminResetPasswordResponse> resetPassword(
            Authentication authentication,
            @PathVariable("id") String userId,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        AdminResetPasswordResponse response = staffUserService.resetPassword(authentication, userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set user password directly (ADMIN only)")
    public ResponseEntity<AdminResetPasswordResponse> setPassword(
            Authentication authentication,
            @PathVariable("id") String userId,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        AdminResetPasswordRequest directRequest = new AdminResetPasswordRequest(
                AdminResetPasswordRequest.ResetPasswordMode.DIRECT,
                request.newPassword(),
                request.confirmPassword()
        );
        AdminResetPasswordResponse response = staffUserService.resetPassword(authentication, userId, directRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reset-password-email")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send password reset email to user (ADMIN only)")
    public ResponseEntity<AdminResetPasswordResponse> sendResetPasswordEmail(
            Authentication authentication,
            @PathVariable("id") String userId) {
        AdminResetPasswordRequest emailRequest = new AdminResetPasswordRequest(
                AdminResetPasswordRequest.ResetPasswordMode.EMAIL,
                null,
                null
        );
        AdminResetPasswordResponse response = staffUserService.resetPassword(authentication, userId, emailRequest);
        return ResponseEntity.ok(response);
    }
}

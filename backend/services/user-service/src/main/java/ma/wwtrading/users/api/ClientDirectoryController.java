package ma.wwtrading.users.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.wwtrading.users.application.ClientDirectoryService;

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Client Directory", description = "Internal directory endpoints for client lookups")
@SecurityRequirement(name = "keycloakBearer")
public class ClientDirectoryController {

    private final ClientDirectoryService clientDirectoryService;

    public ClientDirectoryController(ClientDirectoryService clientDirectoryService) {
        this.clientDirectoryService = clientDirectoryService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'COMMERCIAL')")
    @Operation(summary = "List and search client accounts with pagination")
    public Page<ClientResponse> listClients(
            @RequestParam(name = "q", required = false) String query,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return clientDirectoryService.getClients(query, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'COMMERCIAL')")
    @Operation(summary = "Get client details by Keycloak subject ID")
    public ClientResponse getClient(@PathVariable("id") String id) {
        return clientDirectoryService.getClient(id);
    }

    @GetMapping("/recipients")
    @Operation(summary = "Get list of all active client recipients for notification broadcasting")
    public java.util.List<ClientResponse> getClientRecipients() {
        return clientDirectoryService.getClientRecipients();
    }
}

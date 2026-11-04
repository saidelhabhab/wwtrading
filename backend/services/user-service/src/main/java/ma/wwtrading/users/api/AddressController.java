package ma.wwtrading.users.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import ma.wwtrading.users.application.AddressApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class AddressController {

    private final AddressApplicationService service;

    public AddressController(AddressApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<AddressResponse> list(JwtAuthenticationToken authentication) {
        return service.list(authentication.getToken());
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(
        JwtAuthenticationToken authentication,
        @Valid @RequestBody AddressRequest request
    ) {
        AddressResponse address = service.create(authentication.getToken(), request);
        return ResponseEntity.created(URI.create("/api/v1/users/me/addresses/" + address.id())).body(address);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(
        JwtAuthenticationToken authentication,
        @PathVariable UUID addressId,
        @Valid @RequestBody AddressRequest request
    ) {
        return service.update(authentication.getToken(), addressId, request);
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> delete(
        JwtAuthenticationToken authentication,
        @PathVariable UUID addressId
    ) {
        service.delete(authentication.getToken(), addressId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{addressId}/default")
    public AddressResponse makeDefault(
        JwtAuthenticationToken authentication,
        @PathVariable UUID addressId
    ) {
        return service.makeDefault(authentication.getToken(), addressId);
    }
}

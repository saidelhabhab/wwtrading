package ma.wwtrading.wishlists.api;

import java.security.Principal;
import java.util.UUID;

import jakarta.validation.Valid;
import ma.wwtrading.wishlists.application.WishlistApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistApplicationService service;

    public WishlistController(WishlistApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public WishlistResponse getWishlist(Principal principal) {
        return service.getOrCreate(customerReference(principal));
    }

    @PostMapping("/items")
    public ResponseEntity<WishlistResponse> addItem(
        Principal principal,
        @Valid @RequestBody AddWishlistItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.addItem(customerReference(principal), request));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(Principal principal, @PathVariable UUID itemId) {
        service.removeItem(customerReference(principal), itemId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> clear(Principal principal) {
        service.clear(customerReference(principal));
        return ResponseEntity.noContent().build();
    }

    private String customerReference(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return principal.getName();
    }
}

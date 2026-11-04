package ma.wwtrading.carts.api;

import java.security.Principal;
import java.util.UUID;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import ma.wwtrading.carts.application.CartApplicationService;

@RestController
@RequestMapping("/api/cart")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class CartController {

    private final CartApplicationService service;

    public CartController(CartApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get or create the authenticated customer's cart")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cart returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public CartResponse getCart(Principal principal) {
        return service.getOrCreate(customerReference(principal));
    }

    @PostMapping("/items")
    @Operation(summary = "Add or merge an item in the authenticated customer's cart")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cart item added or merged"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient role"),
        @ApiResponse(responseCode = "409", description = "Cart data conflict")
    })
    public ResponseEntity<CartResponse> addItem(
        Principal principal,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.addItem(customerReference(principal), request));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update an owned cart item's quantity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Quantity updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient role"),
        @ApiResponse(responseCode = "404", description = "Cart item not found")
    })
    public CartResponse updateQuantity(
        Principal principal,
        @PathVariable UUID itemId,
        @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        return service.updateQuantity(
            customerReference(principal),
            itemId,
            request
        );
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove an item from the authenticated customer's cart")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cart item removed"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient role"),
        @ApiResponse(responseCode = "404", description = "Cart item not found")
    })
    public ResponseEntity<Void> removeItem(
        Principal principal,
        @PathVariable UUID itemId
    ) {
        service.removeItem(
            customerReference(principal),
            itemId
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    @Operation(summary = "Clear the authenticated customer's cart")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Cart cleared"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Insufficient role")
    })
    public ResponseEntity<Void> clear(
        Principal principal
    ) {
        service.clear(customerReference(principal));
        return ResponseEntity.noContent().build();
    }

    private String customerReference(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required"
            );
        }

        if (principal instanceof JwtAuthenticationToken jwt && jwt.getToken() != null) {
            String email = jwt.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        return principal.getName();
    }
}

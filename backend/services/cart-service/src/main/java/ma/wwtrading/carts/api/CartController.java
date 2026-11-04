package ma.wwtrading.carts.api;

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
    public CartResponse getCart(JwtAuthenticationToken authentication) {
        return service.getOrCreate(customerReference(authentication));
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
        JwtAuthenticationToken authentication,
        @Valid @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.addItem(customerReference(authentication), request));
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
        JwtAuthenticationToken authentication,
        @PathVariable UUID itemId,
        @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        return service.updateQuantity(
            customerReference(authentication),
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
        JwtAuthenticationToken authentication,
        @PathVariable UUID itemId
    ) {
        service.removeItem(
            customerReference(authentication),
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
        JwtAuthenticationToken authentication
    ) {
        service.clear(customerReference(authentication));
        return ResponseEntity.noContent().build();
    }

    private String customerReference(
        JwtAuthenticationToken authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication required"
            );
        }

        String email = authentication.getToken()
            .getClaimAsString("email");

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication email missing"
            );
        }

        return email;
    }
}
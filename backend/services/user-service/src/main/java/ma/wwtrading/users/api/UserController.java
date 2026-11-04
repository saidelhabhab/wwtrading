package ma.wwtrading.users.api;

import jakarta.validation.Valid;
import ma.wwtrading.users.application.UserApplicationService;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserApplicationService service;

    public UserController(UserApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public UserResponse currentUser(JwtAuthenticationToken authentication) {
        return service.currentUser(authentication.getToken());
    }

    @PutMapping
    public UserResponse synchronize(JwtAuthenticationToken authentication) {
        return service.synchronize(authentication.getToken());
    }

    @PatchMapping
    public UserResponse update(
        JwtAuthenticationToken authentication,
        @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return service.updateCurrentUser(authentication.getToken(), request);
    }
}

package ma.wwtrading.users.application;

import ma.wwtrading.security.KeycloakRealmRoles;
import ma.wwtrading.users.api.UpdateUserProfileRequest;
import ma.wwtrading.users.api.UserResponse;
import ma.wwtrading.users.domain.UserEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserApplicationService {

    private final UserMapper mapper;
    private final AuthenticatedUserService authenticatedUserService;

    public UserApplicationService(
        UserMapper mapper,
        AuthenticatedUserService authenticatedUserService
    ) {
        this.mapper = mapper;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public UserResponse currentUser(Jwt jwt) {
        return responseFor(authenticatedUserService.synchronize(jwt), jwt);
    }

    @Transactional
    public UserResponse synchronize(Jwt jwt) {
        UserEntity user = authenticatedUserService.synchronize(jwt);
        return mapper.toResponse(user, KeycloakRealmRoles.roles(jwt));
    }

    @Transactional
    public UserResponse updateCurrentUser(Jwt jwt, UpdateUserProfileRequest request) {
        UserEntity user = authenticatedUserService.synchronize(jwt);
        mapper.update(request, user);
        return mapper.toResponse(user, KeycloakRealmRoles.roles(jwt));
    }

    private UserResponse responseFor(UserEntity user, Jwt jwt) {
        return mapper.toResponse(user, KeycloakRealmRoles.roles(jwt));
    }
}

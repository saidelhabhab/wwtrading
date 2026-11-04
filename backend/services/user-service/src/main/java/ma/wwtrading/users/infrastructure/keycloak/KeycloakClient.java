package ma.wwtrading.users.infrastructure.keycloak;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class KeycloakClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakClient.class);
    private static final String TECHNICIAN_ROLE = "TECHNICIAN";
    private static final int KEYCLOAK_PAGE_SIZE = 100;
    private static final List<String> STAFF_ROLES = List.of("ADMIN", "MANAGER", "COMMERCIAL", TECHNICIAN_ROLE);

    private final RestClient restClient;
    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    private String cachedToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public KeycloakClient(
            @Autowired(required = false) RestClient.Builder restClientBuilder,
            @Value("${wwtrading.keycloak.server-url:http://localhost:8180}") String serverUrl,
            @Value("${wwtrading.keycloak.realm:wwtrading}") String realm,
            @Value("${wwtrading.keycloak.client-id:user-service-client}") String clientId,
            @Value("${wwtrading.keycloak.client-secret:wwtrading-user-service-dev-secret}") String clientSecret
    ) {
        this.restClient = (restClientBuilder != null ? restClientBuilder : RestClient.builder()).build();
        this.serverUrl = serverUrl != null ? serverUrl.replaceAll("/+$", "") : "http://localhost:8180";
        this.realm = realm != null ? realm : "wwtrading";
        this.clientId = clientId != null ? clientId : "user-service-client";
        this.clientSecret = clientSecret != null ? clientSecret : "wwtrading-user-service-dev-secret";
    }

    public Set<String> getTechnicianSubjectIds() {
        String token = obtainAccessToken();
        String url = String.format("%s/admin/realms/%s/roles/%s/users", serverUrl, realm, TECHNICIAN_ROLE);

        try {
            ResponseEntity<KeycloakUserRecord[]> response = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(KeycloakUserRecord[].class);

            KeycloakUserRecord[] body = response.getBody();
            if (body == null || body.length == 0) {
                return Collections.emptySet();
            }

            return Arrays.stream(body)
                    .filter(u -> u != null && u.id() != null && !u.id().isBlank())
                    .filter(u -> u.enabled() == null || u.enabled())
                    .map(KeycloakUserRecord::id)
                    .collect(Collectors.toSet());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.warn("Technician role not found in Keycloak realm {}", realm);
                return Collections.emptySet();
            }
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                LOGGER.error("Access denied to Keycloak Admin REST API: status {}", ex.getStatusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak authorization failed for technician directory");
            }
            LOGGER.error("Failed to fetch technicians from Keycloak: status {}", ex.getStatusCode(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to retrieve technicians from identity provider");
        } catch (Exception ex) {
            LOGGER.error("Failed to connect to Keycloak Admin REST API at {}", url, ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provider unavailable");
        }
    }

    public void ensureClientRole(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("A Keycloak user identifier is required");
        }
        try {
            String token = obtainAccessToken();
            if (hasDirectRealmRole(userId, "CLIENT", token)) {
                return;
            }
            assignRealmRole(userId, "CLIENT", token);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            LOGGER.error("Failed to ensure CLIENT role for subject={} status={}", userId,
                    exception.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Identity provider rejected customer role provisioning");
        } catch (Exception exception) {
            LOGGER.error("Failed to ensure CLIENT role for subject={}", userId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Identity provider unavailable");
        }
    }

    public void ensureEmailVerified(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try {
            String token = obtainAccessToken();
            String userUrl = String.format("%s/admin/realms/%s/users/%s", serverUrl, realm, userId);
            restClient.put()
                    .uri(userUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("emailVerified", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            LOGGER.debug("Could not ensure emailVerified for user {}: {}", userId, ex.getMessage());
        }
    }

    private boolean hasDirectRealmRole(String userId, String roleName, String token) {
        String mappingsUrl = String.format("%s/admin/realms/%s/users/%s/role-mappings/realm", serverUrl, realm, userId);
        KeycloakRoleRecord[] roles = restClient.get()
                .uri(mappingsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(KeycloakRoleRecord[].class);
        if (roles == null) {
            return false;
        }
        return Arrays.stream(roles)
                .anyMatch(role -> role != null && roleName.equals(role.name()));
    }

    public Set<String> getClientSubjectIds() {
        String token = obtainAccessToken();
        Set<String> direct = getRealmRoleUsers("CLIENT", token).stream()
                .filter(u -> u != null && u.id() != null && !u.id().isBlank())
                .filter(u -> u.enabled() == null || u.enabled())
                .map(KeycloakUserRecord::id)
                .collect(Collectors.toSet());

        Set<String> defaultRoles = getRealmRoleUsers("default-roles-" + realm.toLowerCase(java.util.Locale.ROOT), token).stream()
                .filter(u -> u != null && u.id() != null && !u.id().isBlank())
                .filter(u -> u.enabled() == null || u.enabled())
                .map(KeycloakUserRecord::id)
                .collect(Collectors.toSet());

        Set<String> staffIds = getStaffRoleAssignments().keySet();

        Set<String> all = new java.util.LinkedHashSet<>(direct);
        all.addAll(defaultRoles);
        all.removeAll(staffIds);
        return Collections.unmodifiableSet(all);
    }

    public Map<String, String> getStaffRoleAssignments() {
        String token = obtainAccessToken();
        Map<String, String> assignments = new LinkedHashMap<>();
        for (String role : STAFF_ROLES) {
            getRealmRoleUsers(role, token).stream()
                    .map(KeycloakUserRecord::id)
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(id -> assignments.putIfAbsent(id, role));
        }
        return Map.copyOf(assignments);
    }

    private List<KeycloakUserRecord> getRealmRoleUsers(String role, String token) {
        String url = String.format("%s/admin/realms/%s/roles/%s/users", serverUrl, realm, role);
        try {
            List<KeycloakUserRecord> result = new ArrayList<>();
            int first = 0;
            while (true) {
                KeycloakUserRecord[] page = restClient.get()
                        .uri(url + "?first=" + first + "&max=" + KEYCLOAK_PAGE_SIZE + "&briefRepresentation=true")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(KeycloakUserRecord[].class);
                if (page == null || page.length == 0) {
                    break;
                }
                result.addAll(Arrays.asList(page));
                if (page.length < KEYCLOAK_PAGE_SIZE) {
                    break;
                }
                first += KEYCLOAK_PAGE_SIZE;
            }
            return List.copyOf(result);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.warn("Staff role not found in Keycloak realm: role={}", role);
                return List.of();
            }
            LOGGER.error("Unable to retrieve staff role members from Keycloak: role={} status={}",
                    role, exception.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Identity provider could not supply the staff directory");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to connect to Keycloak while loading staff role={}", role);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provider unavailable");
        }
    }

    public String createStaffUser(String firstName, String lastName, String email, String password,
            String requestedRole) {
        String token = obtainAccessToken();
        String usersUrl = String.format("%s/admin/realms/%s/users", serverUrl, realm);
        Map<String, Object> representation = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", false,
                "attributes", Map.of("locale", List.of("fr")),
                "credentials", List.of(Map.of(
                        "type", "password", "value", password, "temporary", true
                ))
        );

        String createdUserId;
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(usersUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(representation)
                    .retrieve()
                    .toBodilessEntity();
            URI location = response.getHeaders().getLocation();
            if (location == null || location.getPath() == null || !location.getPath().contains("/users/")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Identity provider did not return the created user identifier");
            }
            createdUserId = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
            try {
                restClient.put()
                        .uri(usersUrl + "/" + createdUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("emailVerified", true))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                LOGGER.warn("Could not set emailVerified=true for created user {}: {}", createdUserId, ex.getMessage());
            }
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
            }
            LOGGER.error("Keycloak staff user creation failed: status={}", exception.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider rejected user creation");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Keycloak staff user creation failed before role assignment");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provider unavailable");
        }

        try {
            removeRealmRoleIfAssigned(createdUserId, "CLIENT", token);
            removeRealmRoleIfAssigned(createdUserId, "default-roles-" + realm.toLowerCase(java.util.Locale.ROOT), token);
            assignRealmRole(createdUserId, requestedRole, token);
            return createdUserId;
        } catch (RuntimeException configurationFailure) {
            try {
                deleteUser(createdUserId);
            } catch (RuntimeException compensationFailure) {
                LOGGER.error("Keycloak compensation failed for newly-created subject={}", createdUserId);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Identity configuration failed and cleanup requires administrator attention");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity role configuration failed");
        }
    }

    public String createClientUser(String firstName, String lastName, String email, String phone, String password) {
        String token = obtainAccessToken();
        String usersUrl = String.format("%s/admin/realms/%s/users", serverUrl, realm);
        Map<String, Object> representation = Map.of(
                "username", email.trim().toLowerCase(),
                "email", email.trim().toLowerCase(),
                "firstName", firstName.trim(),
                "lastName", lastName.trim(),
                "enabled", true,
                "emailVerified", true,
                "attributes", Map.of(
                        "phone", List.of(phone.trim()),
                        "locale", List.of("fr")
                ),
                "credentials", List.of(Map.of(
                        "type", "password", "value", password, "temporary", false
                ))
        );

        String createdUserId;
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(usersUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(representation)
                    .retrieve()
                    .toBodilessEntity();
            URI location = response.getHeaders().getLocation();
            if (location == null || location.getPath() == null || !location.getPath().contains("/users/")) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Identity provider did not return the created user identifier");
            }
            createdUserId = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
            try {
                restClient.put()
                        .uri(usersUrl + "/" + createdUserId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("emailVerified", true))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ex) {
                LOGGER.warn("Could not set emailVerified=true for created user {}: {}", createdUserId, ex.getMessage());
            }
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cet email.");
            }
            LOGGER.error("Keycloak client user creation failed: status={}", exception.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur lors de la création du compte.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Keycloak client user creation failed", exception);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provider unavailable");
        }

        assignRealmRole(createdUserId, "CLIENT", token);
        return createdUserId;
    }

    public void executePasswordResetEmail(String email) {
        String token = obtainAccessToken();
        String searchUrl = String.format("%s/admin/realms/%s/users?email=%s&exact=true", serverUrl, realm, email.trim());
        try {
            KeycloakUserRecord[] users = restClient.get()
                    .uri(searchUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KeycloakUserRecord[].class);
            if (users != null && users.length > 0) {
                executePasswordResetEmailForUser(users[0].id());
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to execute password reset email for email={}: {}", email, e.getMessage());
        }
    }

    public void executePasswordResetEmailForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Keycloak user ID is required");
        }
        String token = obtainAccessToken();
        String resetUrl = String.format("%s/admin/realms/%s/users/%s/execute-actions-email", serverUrl, realm, userId.trim());
        try {
            restClient.put()
                    .uri(resetUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of("UPDATE_PASSWORD"))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.warn("User {} not found in Keycloak realm {} for password reset email", userId, realm);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable dans le fournisseur d'identité.");
            }
            if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) {
                LOGGER.error("Bad request sending password reset email for user {}: status={}", userId, ex.getStatusCode());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible d'envoyer l'email de réinitialisation. Vérifiez les informations de l'utilisateur.");
            }
            if (ex.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR) {
                LOGGER.error("Keycloak SMTP error sending password reset email for user {}: status={}", userId, ex.getStatusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Échec de l'envoi de l'email : vérifiez la configuration du serveur SMTP.");
            }
            LOGGER.error("Failed to execute password reset email for user {}: status={}", userId, ex.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Erreur du fournisseur d'identité lors de l'envoi de l'email.");
        } catch (Exception ex) {
            LOGGER.error("Keycloak connection error for password reset email user {}", userId, ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Le serveur d'authentification Keycloak est indisponible.");
        }
    }

    public void changeUserPassword(String userId, String newPassword) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Keycloak user ID is required");
        }
        String token = obtainAccessToken();
        String resetPasswordUrl = String.format("%s/admin/realms/%s/users/%s/reset-password", serverUrl, realm, userId.trim());
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false
        );
        try {
            restClient.put()
                    .uri(resetPasswordUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(credential)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOGGER.warn("User {} not found in Keycloak realm {} for password reset", userId, realm);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable dans le fournisseur d'identité.");
            }
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                LOGGER.warn("Password policy or format rejected by Keycloak for user {}", userId);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe ne respecte pas les critères de sécurité de Keycloak.");
            }
            if (e.getStatusCode() == HttpStatus.FORBIDDEN || e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                LOGGER.error("Access denied to Keycloak Admin API for password reset: status={}", e.getStatusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Accès refusé par le fournisseur d'identité.");
            }
            LOGGER.error("Failed to change password for user {}: status={}", userId, e.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossible de modifier le mot de passe sur le fournisseur d'identité.");
        } catch (Exception e) {
            LOGGER.error("Keycloak connection error for password change user {}", userId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Le serveur d'authentification Keycloak est indisponible.");
        }
    }

    public void deleteUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("A created user identifier is required for compensation");
        }
        try {
            restClient.delete()
                    .uri(String.format("%s/admin/realms/%s/users/%s", serverUrl, realm, userId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + obtainAccessToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            LOGGER.error("Unable to compensate newly-created Keycloak subject={}", userId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity cleanup failed");
        }
    }

    private void assignRealmRole(String userId, String roleName, String token) {
        KeycloakRoleRecord role = getRealmRole(roleName, token);
        restClient.post()
                .uri(String.format("%s/admin/realms/%s/users/%s/role-mappings/realm", serverUrl, realm, userId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    private void removeRealmRoleIfAssigned(String userId, String roleName, String token) {
        String mappingsUrl = String.format("%s/admin/realms/%s/users/%s/role-mappings/realm", serverUrl, realm, userId);
        KeycloakRoleRecord[] roles = restClient.get()
                .uri(mappingsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(KeycloakRoleRecord[].class);
        if (roles == null) {
            return;
        }
        List<KeycloakRoleRecord> matching = Arrays.stream(roles)
                .filter(role -> role != null && roleName.equals(role.name()))
                .toList();
        if (matching.isEmpty()) {
            return;
        }
        restClient.method(HttpMethod.DELETE)
                .uri(mappingsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(matching)
                .retrieve()
                .toBodilessEntity();
    }

    private KeycloakRoleRecord getRealmRole(String roleName, String token) {
        try {
            KeycloakRoleRecord role = restClient.get()
                    .uri(String.format("%s/admin/realms/%s/roles/%s", serverUrl, realm, roleName))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KeycloakRoleRecord.class);
            if (role == null || role.id() == null || !roleName.equals(role.name())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Configured realm role is unavailable");
            }
            return role;
        } catch (RestClientResponseException exception) {
            LOGGER.error("Required Keycloak realm role unavailable: role={} status={}", roleName,
                    exception.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Configured realm role is unavailable");
        }
    }

    private synchronized String obtainAccessToken() {
        Instant now = Instant.now();
        if (cachedToken != null && now.isBefore(tokenExpiresAt.minusSeconds(30))) {
            return cachedToken;
        }

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);

        try {
            ResponseEntity<KeycloakTokenResponse> response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .toEntity(KeycloakTokenResponse.class);

            KeycloakTokenResponse body = response.getBody();
            if (body == null || body.accessToken() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid token response from identity provider");
            }

            this.cachedToken = body.accessToken();
            long expiresIn = body.expiresIn() != null ? body.expiresIn() : 60L;
            this.tokenExpiresAt = now.plusSeconds(expiresIn);
            return this.cachedToken;
        } catch (RestClientResponseException ex) {
            LOGGER.error("Failed to obtain M2M token from Keycloak: status {}", ex.getStatusCode(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider authentication failed");
        } catch (Exception ex) {
            LOGGER.error("Failed to connect to Keycloak token endpoint at {}", tokenUrl, ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provider unavailable");
        }
    }

    public record KeycloakUserRecord(
            String id,
            String username,
            String firstName,
            String lastName,
            String email,
            Boolean enabled
            ) {

    }

    public record KeycloakTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType
            ) {

    }

    public record KeycloakRoleRecord(String id, String name, String description, Boolean composite,
            Boolean clientRole, String containerId) {

    }
}

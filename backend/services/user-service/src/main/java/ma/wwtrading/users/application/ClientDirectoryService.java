package ma.wwtrading.users.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ma.wwtrading.users.api.ClientResponse;
import ma.wwtrading.users.api.NotificationRecipientResponse;
import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientDirectoryService {

    private final KeycloakClient keycloakClient;
    private final UserRepository userRepository;

    public ClientDirectoryService(KeycloakClient keycloakClient, UserRepository userRepository) {
        this.keycloakClient = keycloakClient;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<ClientResponse> getClients(String keyword, Pageable pageable) {
        Set<String> clientSubjectIds = keycloakClient.getClientSubjectIds();
        if (clientSubjectIds == null || clientSubjectIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Page<UserEntity> userPage;
        if (keyword != null && !keyword.trim().isEmpty()) {
            userPage = userRepository.searchClients(clientSubjectIds, keyword.trim(), pageable);
        } else {
            userPage = userRepository.findByKeycloakSubjectIn(clientSubjectIds, pageable);
        }

        return userPage.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }

        Set<String> clientSubjectIds = keycloakClient.getClientSubjectIds();
        if (clientSubjectIds == null || !clientSubjectIds.contains(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }

        UserEntity user = userRepository.findByKeycloakSubject(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public java.util.List<ClientResponse> getClientRecipients() {
        Set<String> clientSubjectIds = keycloakClient.getClientSubjectIds();
        if (clientSubjectIds == null || clientSubjectIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userRepository.findByKeycloakSubjectIn(clientSubjectIds, Pageable.unpaged())
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationRecipientResponse> getNotificationRecipients(Set<String> requestedRoles, String subject) {
        Set<String> roles = requestedRoles == null ? Set.of() : requestedRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::toUpperCase)
                .filter(Set.of("ADMIN", "MANAGER", "COMMERCIAL", "TECHNICIAN", "CLIENT")::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Map<String, Set<String>> assignments = new LinkedHashMap<>();
        keycloakClient.getStaffRoleAssignments().forEach((key, role) -> assignments.put(key, Set.of(role)));
        keycloakClient.getClientSubjectIds().forEach(key -> assignments.merge(key, Set.of("CLIENT"),
                (left, right) -> java.util.stream.Stream.concat(left.stream(), right.stream())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet())));

        Set<String> subjects = assignments.entrySet().stream()
                .filter(entry -> roles.isEmpty() || entry.getValue().stream().anyMatch(roles::contains))
                .filter(entry -> subject == null || subject.isBlank() || subject.equals(entry.getKey()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (subjects.isEmpty()) return List.of();

        return userRepository.findByKeycloakSubjectIn(subjects, Pageable.unpaged()).stream()
                .filter(UserEntity::isEnabled)
                .map(user -> new NotificationRecipientResponse(user.getKeycloakSubject(), user.getEmail(), true,
                        assignments.getOrDefault(user.getKeycloakSubject(), Set.of())))
                .toList();
    }

    private ClientResponse toResponse(UserEntity user) {
        return new ClientResponse(
                user.getKeycloakSubject(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.isEnabled()
        );
    }
}

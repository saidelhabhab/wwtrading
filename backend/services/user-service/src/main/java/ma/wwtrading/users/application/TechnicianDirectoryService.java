package ma.wwtrading.users.application;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import ma.wwtrading.users.api.TechnicianDirectoryResponse;
import ma.wwtrading.users.domain.UserEntity;
import ma.wwtrading.users.domain.UserRepository;
import ma.wwtrading.users.infrastructure.keycloak.KeycloakClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TechnicianDirectoryService {

    private static final Comparator<TechnicianDirectoryResponse> ORDERING =
        Comparator.comparing((TechnicianDirectoryResponse t) -> nullSafe(t.lastName()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(t -> nullSafe(t.firstName()), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(t -> nullSafe(t.email()), String.CASE_INSENSITIVE_ORDER);

    private final KeycloakClient keycloakClient;
    private final UserRepository userRepository;

    public TechnicianDirectoryService(KeycloakClient keycloakClient, UserRepository userRepository) {
        this.keycloakClient = keycloakClient;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TechnicianDirectoryResponse> getTechnicians() {
        Set<String> keycloakSubjectIds = keycloakClient.getTechnicianSubjectIds();
        if (keycloakSubjectIds == null || keycloakSubjectIds.isEmpty()) {
            return List.of();
        }

        List<UserEntity> appUsers = userRepository.findByKeycloakSubjectIn(keycloakSubjectIds);
        return appUsers.stream()
            .filter(UserEntity::isEnabled)
            .map(user -> new TechnicianDirectoryResponse(
                user.getKeycloakSubject(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone()
            ))
            .sorted(ORDERING)
            .toList();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value.trim();
    }
}


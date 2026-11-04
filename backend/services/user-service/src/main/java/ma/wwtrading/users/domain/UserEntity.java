package ma.wwtrading.users.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "keycloak_subject", nullable = false, unique = true, length = 64)
    private String keycloakSubject;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 16)
    private String locale;

    @Column(length = 32)
    private String phone;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public static UserEntity fromIdentity(
        String keycloakSubject,
        String email,
        String firstName,
        String lastName,
        String locale
    ) {
        UserEntity user = new UserEntity();
        user.keycloakSubject = keycloakSubject;
        user.email = email;
        user.firstName = firstName;
        user.lastName = lastName;
        user.locale = locale;
        return user;
    }

    public void refreshEmail(String email) {
        this.email = email;
    }

    public void refreshKeycloakSubject(String keycloakSubject) {
        if (keycloakSubject == null || keycloakSubject.isBlank()) {
            throw new IllegalArgumentException("Keycloak subject must not be blank");
        }
        this.keycloakSubject = keycloakSubject;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getKeycloakSubject() { return keycloakSubject; }
    public String getEmail() { return email; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) {
        if (phone == null) {
            this.phone = null;
        } else {
            String trimmed = phone.trim();
            this.phone = trimmed.isEmpty() ? null : trimmed;
        }
    }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

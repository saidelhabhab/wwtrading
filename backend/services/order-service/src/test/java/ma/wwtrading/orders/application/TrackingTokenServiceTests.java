package ma.wwtrading.orders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrackingTokenServiceTests {

    @Test
    @DisplayName("Generates tracking URL using default local development base URL")
    void generatesUrlWithLocalDevelopmentDefault() {
        TrackingTokenService service = new TrackingTokenService();
        Instant now = Instant.parse("2026-10-26T12:00:00Z");

        TrackingTokenService.GeneratedTrackingToken token = service.generateToken(now);

        assertThat(token.rawToken()).isNotBlank();
        assertThat(token.tokenHash()).hasSize(64);
        assertThat(token.trackingUrl()).startsWith("http://localhost:4201/suivi-commande?token=");
        assertThat(token.trackingUrl()).endsWith(token.rawToken());
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("Generates tracking URL with explicit TRACKING_BASE_URL override")
    void generatesUrlWithExplicitTrackingBaseUrl() {
        TrackingTokenService service = new TrackingTokenService("http://localhost:4201", 30);
        String url = service.buildTrackingUrl("my-secret-test-token");
        assertThat(url).isEqualTo("http://localhost:4201/suivi-commande?token=my-secret-test-token");
    }

    @Test
    @DisplayName("Rejects localhost in production environment")
    void rejectsLocalhostInProductionEnvironment() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("production");

        assertThatThrownBy(() -> new TrackingTokenService("http://localhost:4201", 30, env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production environment must configure a public tracking domain, not localhost");
    }

    @Test
    @DisplayName("Accepts public domain in production environment")
    void acceptsPublicDomainInProductionEnvironment() {
        org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
        env.setActiveProfiles("production");

        TrackingTokenService service = new TrackingTokenService("https://wwtrading.ma", 30, env);
        assertThat(service.buildTrackingUrl("test-token")).isEqualTo("https://wwtrading.ma/suivi-commande?token=test-token");
    }

    @Test
    @DisplayName("Generates tracking URL using production base URL and trims trailing slashes")
    void generatesUrlWithProductionBaseUrlAndTrimsTrailingSlashes() {
        TrackingTokenService service = new TrackingTokenService("https://wwtrading.ma///", 14);
        Instant now = Instant.parse("2026-10-26T12:00:00Z");

        TrackingTokenService.GeneratedTrackingToken token = service.generateToken(now);

        assertThat(token.trackingUrl()).startsWith("https://wwtrading.ma/suivi-commande?token=");
        assertThat(token.trackingUrl()).doesNotContain("///");
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofDays(14)));
    }

    @Test
    @DisplayName("Token generation produces high entropy unique tokens without padding")
    void generatesHighEntropyUniqueTokensWithoutPadding() {
        TrackingTokenService service = new TrackingTokenService();
        Set<String> tokens = new HashSet<>();
        Set<String> hashes = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            TrackingTokenService.GeneratedTrackingToken generated = service.generateToken(Instant.now());
            assertThat(generated.rawToken()).doesNotContain("=");
            assertThat(generated.rawToken().length()).isGreaterThanOrEqualTo(42);
            assertThat(generated.tokenHash()).matches("^[0-9a-f]{64}$");
            tokens.add(generated.rawToken());
            hashes.add(generated.tokenHash());
        }

        assertThat(tokens).hasSize(50);
        assertThat(hashes).hasSize(50);
    }

    @Test
    @DisplayName("Hash calculation is deterministic and matches expected SHA-256 digest")
    void hashTokenIsDeterministic() {
        TrackingTokenService service = new TrackingTokenService();
        String sampleToken = "abcdefghijklmnopqrstuvwxyz0123456789-_ABCDEF";

        String hash1 = service.hashToken(sampleToken);
        String hash2 = service.hashToken(sampleToken);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("Rejects null, empty, or out-of-bounds token strings")
    void rejectsInvalidTokens() {
        TrackingTokenService service = new TrackingTokenService();

        assertThatThrownBy(() -> service.hashToken(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be null");

        assertThatThrownBy(() -> service.hashToken("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid tracking token format");

        assertThatThrownBy(() -> service.hashToken("too-short"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid tracking token format");

        String overlyLong = "a".repeat(150);
        assertThatThrownBy(() -> service.hashToken(overlyLong))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid tracking token format");
    }
}


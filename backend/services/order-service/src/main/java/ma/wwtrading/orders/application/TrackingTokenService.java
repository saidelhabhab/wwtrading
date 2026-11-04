package ma.wwtrading.orders.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TrackingTokenService {

    private static final int TOKEN_BYTES = 32; // 256 bits of entropy
    private static final int MIN_TOKEN_LENGTH = 32;
    private static final int MAX_TOKEN_LENGTH = 128;

    public static final String DEFAULT_DEV_FRONTEND_URL = "http://localhost:4201";

    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendBaseUrl;
    private final Duration tokenTtl;

    public TrackingTokenService() {
        this(DEFAULT_DEV_FRONTEND_URL, 30);
    }

    public TrackingTokenService(
            @Value("${TRACKING_BASE_URL:${orders.tracking.frontend-base-url:${FRONTEND_PUBLIC_URL:${APP_BASE_URL:" + DEFAULT_DEV_FRONTEND_URL + "}}}}") String frontendBaseUrl,
            @Value("${orders.tracking.token-ttl-days:30}") long tokenTtlDays) {
        this(frontendBaseUrl, tokenTtlDays, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public TrackingTokenService(
            @Value("${TRACKING_BASE_URL:${orders.tracking.frontend-base-url:${FRONTEND_PUBLIC_URL:${APP_BASE_URL:" + DEFAULT_DEV_FRONTEND_URL + "}}}}") String frontendBaseUrl,
            @Value("${orders.tracking.token-ttl-days:30}") long tokenTtlDays,
            org.springframework.core.env.Environment environment) {
        String resolved = frontendBaseUrl != null && !frontendBaseUrl.isBlank()
                ? frontendBaseUrl.replaceAll("/+$", "")
                : DEFAULT_DEV_FRONTEND_URL;

        if (environment != null && (environment.matchesProfiles("prod") || environment.matchesProfiles("production"))) {
            if (resolved.contains("localhost") || resolved.contains("127.0.0.1")) {
                throw new IllegalStateException("Production environment must configure a public tracking domain, not localhost: " + resolved);
            }
        }
        this.frontendBaseUrl = resolved;
        this.tokenTtl = Duration.ofDays(Math.max(1, tokenTtlDays));
    }

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public GeneratedTrackingToken generateToken(Instant now) {
        byte[] randomBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashToken(rawToken);
        Instant baseTime = now != null ? now : Instant.now();
        Instant expiresAt = baseTime.plus(tokenTtl);
        return new GeneratedTrackingToken(rawToken, tokenHash, expiresAt, buildTrackingUrl(rawToken));
    }

    public String hashToken(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("Tracking token must not be null");
        }
        String trimmed = rawToken.trim();
        if (trimmed.length() < MIN_TOKEN_LENGTH || trimmed.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid tracking token format");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(trimmed.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public String buildTrackingUrl(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return frontendBaseUrl + "/suivi-commande";
        }
        return frontendBaseUrl + "/suivi-commande?token=" + rawToken.trim();
    }

    public record GeneratedTrackingToken(
            String rawToken,
            String tokenHash,
            Instant expiresAt,
            String trackingUrl) {
    }
}


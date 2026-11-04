package ma.wwtrading.orders.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GuestEndpointRateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();
    private final int maxTrackingRequestsPerMinute;
    private final int maxCheckoutRequestsPerMinute;
    private final Duration window = Duration.ofMinutes(1);

    public GuestEndpointRateLimiter() {
        this(30, 15);
    }

    public GuestEndpointRateLimiter(
            @Value("${orders.rate-limit.tracking-per-minute:30}") int maxTrackingRequestsPerMinute,
            @Value("${orders.rate-limit.checkout-per-minute:15}") int maxCheckoutRequestsPerMinute) {
        this.maxTrackingRequestsPerMinute = Math.max(1, maxTrackingRequestsPerMinute);
        this.maxCheckoutRequestsPerMinute = Math.max(1, maxCheckoutRequestsPerMinute);
    }

    public void checkTrackingAllowed(String clientIp) {
        checkAllowed("track:" + normalizeClientKey(clientIp), maxTrackingRequestsPerMinute);
    }

    public void checkCheckoutAllowed(String clientIp) {
        checkAllowed("checkout:" + normalizeClientKey(clientIp), maxCheckoutRequestsPerMinute);
    }

    public void checkAllowed(String bucketKey, int maxRequestsPerWindow) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(window);
        if (buckets.size() > MAX_TRACKED_KEYS && !buckets.containsKey(bucketKey)) {
            buckets.entrySet().removeIf(entry -> {
                Deque<Instant> timestamps = entry.getValue();
                synchronized (timestamps) {
                    while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                        timestamps.pollFirst();
                    }
                    return timestamps.isEmpty();
                }
            });
        }

        Deque<Instant> timestamps = buckets.computeIfAbsent(bucketKey, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequestsPerWindow) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Trop de tentatives. Veuillez patienter une minute avant de réessayer.");
            }
            timestamps.addLast(now);
        }
    }

    private String normalizeClientKey(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        String first = clientIp.split(",")[0].trim();
        return first.isEmpty() ? "unknown" : first;
    }
}


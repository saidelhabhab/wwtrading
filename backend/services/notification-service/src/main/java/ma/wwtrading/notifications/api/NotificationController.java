package ma.wwtrading.notifications.api;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.wwtrading.notifications.application.NotificationApplicationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final NotificationApplicationService service;

    public NotificationController(NotificationApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotificationResponse> list(
            JwtAuthenticationToken authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listOwn(authentication.getToken().getSubject(), PageRequest.of(page, size, NEWEST_FIRST));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(JwtAuthenticationToken authentication) {
        return service.unreadCount(authentication.getToken().getSubject());
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            JwtAuthenticationToken authentication,
            @PathVariable UUID notificationId) {
        return service.markRead(authentication.getToken().getSubject(), notificationId);
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(JwtAuthenticationToken authentication) {
        service.markAllRead(authentication.getToken().getSubject());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/{orderId}/deliveries")
    public OrderNotificationDeliveryResponse orderDeliveries(@PathVariable UUID orderId) {
        return service.getOrderDeliveries(orderId);
    }

    @PostMapping("/orders/{orderId}/retry")
    public OrderNotificationDeliveryResponse retryOrderDeliveries(
            @PathVariable UUID orderId,
            @RequestParam(value = "channel", required = false) String channel) {
        return service.retryOrderDeliveries(orderId, channel);
    }
}

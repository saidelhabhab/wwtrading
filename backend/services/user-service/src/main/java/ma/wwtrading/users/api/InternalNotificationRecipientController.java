package ma.wwtrading.users.api;

import java.util.List;
import java.util.Set;

import ma.wwtrading.users.application.ClientDirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/notification-recipients")
public class InternalNotificationRecipientController {
    private final ClientDirectoryService directory;

    public InternalNotificationRecipientController(ClientDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    public List<NotificationRecipientResponse> recipients(
            @RequestParam(name = "role", required = false) Set<String> roles,
            @RequestParam(name = "subject", required = false) String subject) {
        return directory.getNotificationRecipients(roles, subject);
    }
}

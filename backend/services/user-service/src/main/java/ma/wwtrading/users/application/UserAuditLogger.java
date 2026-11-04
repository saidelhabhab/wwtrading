package ma.wwtrading.users.application;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserAuditLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("WWTRADING_AUDIT");

    public void userCreated(UUID userId, String correlationId) {
        LOGGER.info(
            "audit.action=user_profile_created audit.outcome=success audit.user_id={} correlation_id={}",
            userId,
            correlationId
        );
    }

    public void profileUpdated(UUID userId, String correlationId) {
        LOGGER.info(
            "audit.action=user_profile_updated audit.outcome=success audit.user_id={} correlation_id={}",
            userId,
            correlationId
        );
    }
}

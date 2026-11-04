package ma.wwtrading.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:notifications;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
    "spring.datasource.driver-class-name=org.h2.Driver"
})
class NotificationRepositoryTests {
    @Autowired
    private NotificationRepository repository;

    @Test
    void ownershipQueriesAndUnreadBulkUpdateNeverCrossRecipients() {
        NotificationEntity ownUnread = repository.saveAndFlush(notification("client-a", "First"));
        NotificationEntity otherUnread = repository.saveAndFlush(notification("client-b", "Other"));

        assertThat(repository.findByIdAndRecipientSubject(ownUnread.getId(), "client-a")).isPresent();
        assertThat(repository.findByIdAndRecipientSubject(otherUnread.getId(), "client-a")).isEmpty();
        assertThat(repository.countByRecipientSubjectAndReadAtIsNull("client-a")).isEqualTo(1);

        int updated = repository.markAllUnreadAsRead("client-a", Instant.parse("2026-09-26T12:00:00Z"));

        assertThat(updated).isEqualTo(1);
        assertThat(repository.countByRecipientSubjectAndReadAtIsNull("client-a")).isZero();
        assertThat(repository.countByRecipientSubjectAndReadAtIsNull("client-b")).isEqualTo(1);
    }

    @Test
    void paginatesNotificationsNewestFirst() {
        repository.saveAndFlush(notification("client-a", "Older"));
        repository.saveAndFlush(notification("client-a", "Newer"));

        var page = repository.findByRecipientSubject(
            "client-a",
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(page.getSize()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(NotificationEntity::getTitle).containsExactly("Newer");
    }

    private NotificationEntity notification(String owner, String title) {
        return new NotificationEntity(
            owner, null, NotificationType.SYSTEM, NotificationChannel.IN_APP,
            title, "Message", null, null, null, null, null);
    }
}

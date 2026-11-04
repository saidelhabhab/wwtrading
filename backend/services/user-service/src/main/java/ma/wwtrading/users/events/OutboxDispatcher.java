package ma.wwtrading.users.events;

import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class OutboxDispatcher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    OutboxDispatcher(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${wwtrading.outbox.fixed-delay:2000}")
    @Transactional
    public void publishPending() {
        for (OutboxEventEntity event : repository.findPending(PageRequest.of(0, 50))) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException exception) {
                event.markAttemptFailed();
                Thread.currentThread().interrupt();
                break;
            } catch (Exception exception) {
                event.markAttemptFailed();
                break;
            }
        }
    }
}

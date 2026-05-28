package com.neeraj.upi.user.kafka;

import com.neeraj.upi.common.constants.KafkaTopics;
import com.neeraj.upi.user.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    /**
     * Publishes a UserCreated event to Kafka topic: user.created synchronously.
     *
     * <p>Uses {@code .get()} to block until the broker acknowledges the send.
     * This is intentional: {@link com.neeraj.upi.user.service.OutboxScheduler}
     * catches any exception thrown here and leaves {@code processed=false},
     * guaranteeing the event will be retried on the next poll cycle.
     *
     * <p>Key = userId — ensures ordering per user within the same partition.
     * Called exclusively by OutboxScheduler, never directly from UserService.
     *
     * @throws RuntimeException wrapping {@link ExecutionException} if Kafka rejects the send
     */
    public void publishUserCreated(UserCreatedEvent event) {
        try {
            SendResult<String, UserCreatedEvent> result =
                    kafkaTemplate.send(KafkaTopics.USER_CREATED, event.getUserId().toString(), event)
                            .get();   // blocks — lets OutboxScheduler catch failures

            log.info("UserCreatedEvent published: userId={} topic={} partition={} offset={}",
                    event.getUserId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing UserCreatedEvent for userId="
                    + event.getUserId(), ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Kafka send failed for userId=" + event.getUserId()
                    + ": " + ex.getCause().getMessage(), ex.getCause());
        }
    }
}

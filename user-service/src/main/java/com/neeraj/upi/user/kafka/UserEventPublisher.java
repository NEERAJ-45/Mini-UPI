package com.neeraj.upi.user.kafka;

import com.neeraj.upi.common.constants.KafkaTopics;
import com.neeraj.upi.user.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    /**
     * Publishes a UserCreated event to Kafka topic: user.created
     * Key = userId — ensures ordering per user within the same partition.
     * Called by OutboxScheduler (not directly from UserService).
     */
    public void publishUserCreated(UserCreatedEvent event) {
        kafkaTemplate.send(KafkaTopics.USER_CREATED, event.getUserId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserCreatedEvent for userId={}: {}",
                                event.getUserId(), ex.getMessage(), ex);
                    } else {
                        log.info("UserCreatedEvent published: userId={} topic={} partition={} offset={}",
                                event.getUserId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}

package com.neeraj.upi.user.kafka;

import com.neeraj.upi.user.event.UserCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    @Mock
    private KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @Test
    @DisplayName("publishUserCreated should throw UnsupportedOperationException until implemented")
    void publishUserCreated_throwsNotImplemented() {
        UserEventPublisher publisher = new UserEventPublisher(kafkaTemplate);

        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(UUID.randomUUID())
                .upiId("test@miniupi")
                .fullName("Test")
                .phone("9876543210")
                .createdAt(Instant.now())
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> publisher.publishUserCreated(event));
    }
}

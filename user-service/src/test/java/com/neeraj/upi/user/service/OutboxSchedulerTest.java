package com.neeraj.upi.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neeraj.upi.user.entity.OutboxEvent;
import com.neeraj.upi.user.event.UserCreatedEvent;
import com.neeraj.upi.user.kafka.UserEventPublisher;
import com.neeraj.upi.user.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxScheduler}.
 *
 * Verifies:
 *  1. No-op when the outbox table is empty.
 *  2. Unpublished events are dispatched via UserEventPublisher and marked processed=true.
 *  3. A Kafka failure leaves processed=false (event remains for retry).
 */
@ExtendWith(MockitoExtension.class)
class OutboxSchedulerTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private UserEventPublisher    eventPublisher;
    @Mock private ObjectMapper          objectMapper;

    @InjectMocks
    private OutboxScheduler outboxScheduler;

    @Captor
    private ArgumentCaptor<OutboxEvent> savedEventCaptor;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private OutboxEvent buildUnprocessedUserCreatedEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(UUID.randomUUID().toString())
                .aggregateType("User")
                .eventType("user.created")
                .payload("{\"userId\":\"" + UUID.randomUUID() + "\",\"upiId\":\"john@miniupi\"," +
                         "\"fullName\":\"John Doe\",\"phone\":\"9876543210\",\"createdAt\":null}")
                .build();   // processed=false by @Builder.Default
    }

    // ── Test 1: empty outbox ──────────────────────────────────────────────────

    @Test
    @DisplayName("processOutboxEvents: no-op when there are no pending events")
    void processOutboxEvents_noPendingEvents_doesNotThrow() {
        when(outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc())
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> outboxScheduler.processOutboxEvents());

        verify(eventPublisher,         never()).publishUserCreated(any());
        verify(outboxEventRepository,  never()).save(any());
    }

    // ── Test 2: successful publish → marks processed=true ────────────────────

    @Test
    @DisplayName("processOutboxEvents: dispatches unpublished event via UserEventPublisher and marks processed=true")
    void processOutboxEvents_pendingEvent_dispatchesAndMarksProcessed() throws Exception {
        OutboxEvent event = buildUnprocessedUserCreatedEvent();
        assertFalse(event.isProcessed(), "precondition: event should start unprocessed");

        when(outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(objectMapper.readValue(event.getPayload(), UserCreatedEvent.class))
                .thenReturn(new UserCreatedEvent());

        outboxScheduler.processOutboxEvents();

        // Publisher must have been called exactly once
        verify(eventPublisher, times(1)).publishUserCreated(any(UserCreatedEvent.class));

        // The event must have been saved with processed=true
        verify(outboxEventRepository).save(savedEventCaptor.capture());
        OutboxEvent saved = savedEventCaptor.getValue();
        assertTrue(saved.isProcessed(),    "event must be marked processed after successful publish");
        assertNotNull(saved.getProcessedAt(), "processedAt timestamp must be set");
    }

    // ── Test 3: Kafka failure → leaves processed=false for retry ─────────────

    @Test
    @DisplayName("processOutboxEvents: leaves processed=false when UserEventPublisher throws (Kafka failure)")
    void processOutboxEvents_kafkaFailure_leavesEventUnprocessed() throws Exception {
        OutboxEvent event = buildUnprocessedUserCreatedEvent();

        when(outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
        when(objectMapper.readValue(event.getPayload(), UserCreatedEvent.class))
                .thenReturn(new UserCreatedEvent());

        // Simulate Kafka broker failure
        doThrow(new RuntimeException("Kafka unavailable"))
                .when(eventPublisher).publishUserCreated(any());

        // Scheduler must NOT propagate the exception (it just logs and continues)
        assertDoesNotThrow(() -> outboxScheduler.processOutboxEvents());

        // Event must NOT have been saved (i.e. still unprocessed — no save call)
        verify(outboxEventRepository, never()).save(any());
        assertFalse(event.isProcessed(), "event must remain unprocessed after a Kafka failure");
    }
}

package com.neeraj.upi.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neeraj.upi.user.entity.OutboxEvent;
import com.neeraj.upi.user.event.UserCreatedEvent;
import com.neeraj.upi.user.kafka.UserEventPublisher;
import com.neeraj.upi.user.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background scheduler that implements the Transactional Outbox Pattern.
 *
 * Flow:
 *  1. Poll the outbox_events table for unprocessed records (ordered by created_at ASC).
 *  2. Deserialize the JSON payload into the appropriate event type.
 *  3. Publish to Kafka via UserEventPublisher.
 *  4. Mark the record as processed + stamp processedAt — all inside a transaction.
 *
 * If Kafka publish fails, the event is NOT marked processed and will be retried
 * on the next scheduled run — guaranteeing at-least-once delivery.
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class OutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final UserEventPublisher    eventPublisher;
    private final ObjectMapper          objectMapper;

    @Scheduled(fixedDelay = 2000)   // runs every 2s after the previous execution completes
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByProcessedFalseOrderByCreatedAtAsc();

        if (pending.isEmpty()) return;

        log.debug("OutboxScheduler: processing {} pending event(s)", pending.size());

        for (OutboxEvent outboxEvent : pending) {
            try {
                dispatchEvent(outboxEvent);

                // Mark processed only after a successful Kafka send initiation
                outboxEvent.setProcessed(true);
                outboxEvent.setProcessedAt(Instant.now());
                outboxEventRepository.save(outboxEvent);

            } catch (Exception e) {
                // Log and continue — do NOT mark as processed; it will retry next cycle
                log.error("Failed to process outbox event id={} eventType={}: {}",
                        outboxEvent.getId(), outboxEvent.getEventType(), e.getMessage(), e);
            }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void dispatchEvent(OutboxEvent outboxEvent) throws Exception {
        switch (outboxEvent.getEventType()) {
            case "user.created" -> {
                UserCreatedEvent event = objectMapper.readValue(
                        outboxEvent.getPayload(), UserCreatedEvent.class);
                eventPublisher.publishUserCreated(event);
                log.info("Dispatched user.created for aggregateId={}", outboxEvent.getAggregateId());
            }
            default -> log.warn("Unknown eventType='{}' for outbox id={} — skipping",
                    outboxEvent.getEventType(), outboxEvent.getId());
        }
    }
}

package com.neeraj.upi.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxSchedulerTest {

    private final OutboxScheduler outboxScheduler = new OutboxScheduler();

    @Test
    @DisplayName("processOutboxEvents should throw UnsupportedOperationException until implemented")
    void processOutboxEvents_throwsNotImplemented() {
        assertThrows(UnsupportedOperationException.class,
                () -> outboxScheduler.processOutboxEvents());
    }
}

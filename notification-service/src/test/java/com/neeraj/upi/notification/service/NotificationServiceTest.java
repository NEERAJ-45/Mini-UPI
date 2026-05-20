package com.neeraj.upi.notification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NotificationServiceTest {

    private final NotificationService notificationService = new NotificationService();

    @Test
    @DisplayName("sendWelcome should complete without exception for valid inputs")
    void sendWelcomeShouldCompleteSuccessfully() {
        assertDoesNotThrow(() ->
                notificationService.sendWelcome("john@upi", "John Doe", "9876543210"));
    }

    @Test
    @DisplayName("sendWelcome should handle null and empty inputs gracefully")
    void sendWelcomeShouldHandleNullOrEmptyInputs() {
        assertDoesNotThrow(() -> notificationService.sendWelcome(null, null, null));
        assertDoesNotThrow(() -> notificationService.sendWelcome("", "", ""));
    }

    @Test
    @DisplayName("sendDebitAlert should complete without exception for valid inputs")
    void sendDebitAlertShouldCompleteSuccessfully() {
        assertDoesNotThrow(() ->
                notificationService.sendDebitAlert("9876543210", "john@upi",
                        new BigDecimal("100.50"), "TXN123"));
    }

    @Test
    @DisplayName("sendDebitAlert should handle null, empty, and zero amount inputs gracefully")
    void sendDebitAlertShouldHandleEdgeInputs() {
        assertDoesNotThrow(() -> notificationService.sendDebitAlert(null, null, null, null));
        assertDoesNotThrow(() -> notificationService.sendDebitAlert("", "", BigDecimal.ZERO, ""));
    }

    @Test
    @DisplayName("sendCreditAlert should complete without exception for valid inputs")
    void sendCreditAlertShouldCompleteSuccessfully() {
        assertDoesNotThrow(() ->
                notificationService.sendCreditAlert("9876543210", "jane@upi",
                        new BigDecimal("250.00"), "TXN456"));
    }

    @Test
    @DisplayName("sendCreditAlert should handle null, empty, and negative amount inputs gracefully")
    void sendCreditAlertShouldHandleEdgeInputs() {
        assertDoesNotThrow(() -> notificationService.sendCreditAlert(null, null, null, null));
        assertDoesNotThrow(() -> notificationService.sendCreditAlert("", "",
                new BigDecimal("-50.00"), ""));
    }

    @Test
    @DisplayName("sendFailureAlert should complete without exception for valid inputs")
    void sendFailureAlertShouldCompleteSuccessfully() {
        assertDoesNotThrow(() ->
                notificationService.sendFailureAlert("9876543210",
                        new BigDecimal("500.00"), "Insufficient balance"));
    }

    @Test
    @DisplayName("sendFailureAlert should handle null, empty, and zero amount inputs gracefully")
    void sendFailureAlertShouldHandleEdgeInputs() {
        assertDoesNotThrow(() -> notificationService.sendFailureAlert(null, null, null));
        assertDoesNotThrow(() -> notificationService.sendFailureAlert("", BigDecimal.ZERO, ""));
    }
}

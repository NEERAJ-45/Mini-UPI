package com.neeraj.upi.user.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrPayloadBuilderTest {

    @Test
    @DisplayName("Should build basic UPI URI with required fields")
    void shouldBuildBasicUri() {
        String uri = QrPayloadBuilder.builder()
                .upiId("john@miniupi")
                .name("John Doe")
                .build();

        assertTrue(uri.startsWith("upi://pay?"));
        assertTrue(uri.contains("pa=john%40miniupi"));
        assertTrue(uri.contains("pn=John+Doe"));
        assertTrue(uri.contains("cu=INR"));
    }

    @Test
    @DisplayName("Should build UPI URI with all optional fields")
    void shouldBuildUriWithAllFields() {
        String uri = QrPayloadBuilder.builder()
                .upiId("john@miniupi")
                .name("John Doe")
                .currency("USD")
                .amount("500.00")
                .note("Payment for lunch")
                .build();

        assertTrue(uri.contains("pa=john%40miniupi"));
        assertTrue(uri.contains("pn=John+Doe"));
        assertTrue(uri.contains("cu=USD"));
        assertTrue(uri.contains("am=500.00"));
        assertTrue(uri.contains("tn=Payment+for+lunch"));
    }

    @Test
    @DisplayName("Should build URI without optional amount and note")
    void shouldBuildUriWithoutOptionals() {
        String uri = QrPayloadBuilder.builder()
                .upiId("alice@miniupi")
                .name("Alice")
                .build();

        assertFalse(uri.contains("am="));
        assertFalse(uri.contains("tn="));
    }

    @Test
    @DisplayName("Should encode special characters in name")
    void shouldEncodeSpecialCharacters() {
        String uri = QrPayloadBuilder.builder()
                .upiId("test@miniupi")
                .name("Neeraj & Sons")
                .build();

        assertTrue(uri.contains("pn=Neeraj+%26+Sons"));
    }

    @Test
    @DisplayName("Should throw exception when UPI ID is null")
    void shouldThrowWhenUpiIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> QrPayloadBuilder.builder()
                        .name("John")
                        .build());
    }

    @Test
    @DisplayName("Should throw exception when UPI ID is blank")
    void shouldThrowWhenUpiIdIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> QrPayloadBuilder.builder()
                        .upiId("  ")
                        .name("John")
                        .build());
    }

    @Test
    @DisplayName("Should throw exception when name is null")
    void shouldThrowWhenNameIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> QrPayloadBuilder.builder()
                        .upiId("john@miniupi")
                        .build());
    }

    @Test
    @DisplayName("Should throw exception when name is blank")
    void shouldThrowWhenNameIsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> QrPayloadBuilder.builder()
                        .upiId("john@miniupi")
                        .name("  ")
                        .build());
    }
}

package com.neeraj.upi.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService =
            new QrCodeService();

    @Test
    @DisplayName("Should generate Base64 encoded QR code successfully")
    void shouldGenerateBase64EncodedQrCodeSuccessfully() {

        String upiId = "neeraj@upi";
        String name = "Neeraj Surnis";

        String qrCodeBase64 =
                qrCodeService.generateQrCodeBase64(upiId, name);

        assertNotNull(qrCodeBase64);

        assertFalse(qrCodeBase64.isBlank());

        assertTrue(qrCodeBase64.length() > 100);
    }

    @Test
    @DisplayName("Should generate different QR codes for different UPI IDs")
    void shouldGenerateDifferentQrCodesForDifferentUpiIds() {

        String firstQr =
                qrCodeService.generateQrCodeBase64(
                        "neeraj@upi",
                        "Neeraj"
                );

        String secondQr =
                qrCodeService.generateQrCodeBase64(
                        "alex@upi",
                        "Alex"
                );

        assertNotEquals(firstQr, secondQr);
    }

    @Test
    @DisplayName("Should handle special characters in name")
    void shouldHandleSpecialCharactersInName() {

        String qrCodeBase64 =
                qrCodeService.generateQrCodeBase64(
                        "neeraj@upi",
                        "Neeraj Surnis & Sons"
                );

        assertNotNull(qrCodeBase64);

        assertFalse(qrCodeBase64.isBlank());
    }
}
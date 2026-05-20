package com.neeraj.upi.user.service;

import com.neeraj.upi.user.exception.InvalidJwtException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String BASE64_SECRET = "dGhpcyBpcyBhIHZlcnkgc2VjdXJlIDI1Ni1iaXQgc2VjcmV0IGZvciB0ZXN0aW5nIHB1cnBvc2Vz";
    private static final long EXPIRY_MS = 3600000;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String UPI_ID = "john@miniupi";
    private static final String PHONE = "9876543210";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", BASE64_SECRET);
        ReflectionTestUtils.setField(jwtService, "expiryMs", EXPIRY_MS);
    }

    @Test
    @DisplayName("Should generate a valid JWT token")
    void shouldGenerateToken() {
        String token = jwtService.generateToken(USER_ID, UPI_ID, PHONE);

        assertNotNull(token);
        assertFalse(token.isBlank());
        assertEquals(3, token.split("\\.").length);
    }

    @Test
    @DisplayName("Should validate and extract claims from a valid token")
    void shouldValidateAndExtract() {
        String token = jwtService.generateToken(USER_ID, UPI_ID, PHONE);

        Claims claims = jwtService.validateAndExtract(token);

        assertNotNull(claims);
        assertEquals(USER_ID.toString(), claims.getSubject());
        assertEquals(UPI_ID, claims.get("upiId"));
        assertEquals(PHONE, claims.get("phone"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("Should extract user ID from token subject")
    void shouldExtractUserId() {
        String token = jwtService.generateToken(USER_ID, UPI_ID, PHONE);

        String extractedUserId = jwtService.extractUserId(token);

        assertEquals(USER_ID.toString(), extractedUserId);
    }

    @Test
    @DisplayName("Should extract UPI ID from token claims")
    void shouldExtractUpiId() {
        String token = jwtService.generateToken(USER_ID, UPI_ID, PHONE);

        String extractedUpiId = jwtService.extractUpiId(token);

        assertEquals(UPI_ID, extractedUpiId);
    }

    @Test
    @DisplayName("isTokenValid should return true for a valid token")
    void isTokenValidShouldReturnTrueForValidToken() {
        String token = jwtService.generateToken(USER_ID, UPI_ID, PHONE);

        boolean valid = jwtService.isTokenValid(token);

        assertTrue(valid);
    }

    @Test
    @DisplayName("isTokenValid should throw InvalidJwtException for malformed token")
    void isTokenValidShouldThrowForMalformedToken() {
        String malformedToken = "this.is.not.a.valid.jwt";

        assertThrows(InvalidJwtException.class,
                () -> jwtService.isTokenValid(malformedToken));
    }

    @Test
    @DisplayName("isTokenValid should throw InvalidJwtException for empty token")
    void isTokenValidShouldThrowForEmptyToken() {
        assertThrows(InvalidJwtException.class,
                () -> jwtService.isTokenValid(""));
    }

    @Test
    @DisplayName("isTokenValid should throw InvalidJwtException for token with invalid signature")
    void isTokenValidShouldThrowForInvalidSignature() {
        String tokenFromDifferentSecret = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.dGVzdC1zaWc";

        assertThrows(InvalidJwtException.class,
                () -> jwtService.isTokenValid(tokenFromDifferentSecret));
    }

    @Test
    @DisplayName("Should generate different tokens for different users")
    void shouldGenerateDifferentTokensForDifferentUsers() {
        String token1 = jwtService.generateToken(UUID.randomUUID(), "alice@miniupi", "1111111111");
        String token2 = jwtService.generateToken(UUID.randomUUID(), "bob@miniupi", "2222222222");

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("validateAndExtract should throw for expired token")
    void validateAndExtractShouldThrowForExpiredToken() {
        JwtService shortExpiryJwt = new JwtService();
        ReflectionTestUtils.setField(shortExpiryJwt, "secret", BASE64_SECRET);
        ReflectionTestUtils.setField(shortExpiryJwt, "expiryMs", -1);

        String expiredToken = shortExpiryJwt.generateToken(USER_ID, UPI_ID, PHONE);

        assertThrows(Exception.class,
                () -> jwtService.validateAndExtract(expiredToken));
    }
}

package com.neeraj.upi.user.service;

import com.neeraj.upi.user.exception.InvalidJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiry-ms}")
    private long expiryMs;

    /**
     * Generate a signed JWT with userId, upiId, and phone as claims.
     * Uses JJWT 0.12.x API — algorithm inferred from key length (HS256).
     */
    public String generateToken(UUID userId, String upiId, String phone) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("upiId", upiId);
        claims.put("phone", phone);

        SecretKey key = getSigningKey();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)          // JJWT 0.12.x — algorithm inferred from SecretKey
                .compact();
    }

    /**
     * Parse and validate a JWT. Returns claims payload.
     * Uses JJWT 0.12.x API — verifyWith + parseSignedClaims.
     */
    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())     // JJWT 0.12.x
                .build()
                .parseSignedClaims(token)        // JJWT 0.12.x (replaces deprecated parseClaimsJws)
                .getPayload();                   // JJWT 0.12.x (replaces deprecated getBody)
    }

    public String extractUserId(String token) {
        return validateAndExtract(token).getSubject();
    }

    public String extractUpiId(String token) {
        return validateAndExtract(token).get("upiId", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            throw new InvalidJwtException("Invalid or expired JWT token");
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

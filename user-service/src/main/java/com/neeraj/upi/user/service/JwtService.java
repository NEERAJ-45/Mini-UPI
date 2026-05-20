package com.neeraj.upi.user.service;

import com.neeraj.upi.user.exception.InvalidJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.*;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiry-ms}")
    private long expiryMs;

    /**
     * Generate a signed JWT with userId, upiId, and phone as claims
     */
    public String generateToken(UUID userId, String upiId, String phone) {
        // TODO: use JJWT Jwts.builder(), set subject=userId, claims, expiry, sign with HS256
        // Add this data fields to the token claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("upiId", upiId);
        claims.put("phone", phone);


        //Decode Base64 into bytes
        byte[] keys = Decoders.BASE64.decode(secret);
        //  Create Hmac SHA Signing key from secret bytes
        Key key = Keys.hmacShaKeyFor(keys);

        // Current Timestamp
        Date currentDate = new Date();
        Date expiryDate = new Date(currentDate.getTime() + expiryMs);


        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(currentDate)
                .expiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parse and validate a JWT. Throws JwtException if invalid/expired
     */
    public Claims validateAndExtract(String token) {
        // TODO: use Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
        byte[] keys = Decoders.BASE64.decode(secret);

        Key key = Keys.hmacShaKeyFor(keys);

        return Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
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
}

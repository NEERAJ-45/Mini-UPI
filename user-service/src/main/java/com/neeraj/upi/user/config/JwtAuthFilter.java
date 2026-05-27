package com.neeraj.upi.user.config;

import com.neeraj.upi.user.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        extractAndAuthenticate(request);

        filterChain.doFilter(request, response);
    }

    private void extractAndAuthenticate(HttpServletRequest request) {

        String token = extractToken(request);

        if (token == null) {
            return;
        }

        try {

            if (!jwtService.isTokenValid(token)) {
                return;
            }

            String userId = jwtService.extractUserId(token);

            setAuthentication(userId, request);

        } catch (Exception ex) {

            log.warn("JWT authentication failed: {}", ex.getMessage());

            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authHeader.substring(BEARER_PREFIX.length());
    }

    private void setAuthentication(String userId,
                                   HttpServletRequest request) {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        List.of()
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource()
                        .buildDetails(request)
        );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }
}
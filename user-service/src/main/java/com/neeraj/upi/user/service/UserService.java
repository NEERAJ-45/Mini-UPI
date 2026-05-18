package com.neeraj.upi.user.service;

import com.neeraj.upi.user.dto.*;
import com.neeraj.upi.user.entity.User;
import com.neeraj.upi.user.event.UserCreatedEvent;
import com.neeraj.upi.user.repository.UserRepository;
import com.neeraj.upi.user.kafka.UserEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UpiIdGenerator upiIdGenerator;
    private final UserEventPublisher eventPublisher;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // Check duplicate phone number
        if (userRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        // Generate unique UPI ID
        String upiId = upiIdGenerator.generate(req.getFullName());

        // Hash PIN using BCrypt
        String hashedPin = passwordEncoder.encode(req.getPin());

        // Build and save user entity
        User user = User.builder().fullName(req.getFullName()).phone(req.getPhone()).email(req.getEmail()).upiId(upiId).pinHash(hashedPin).isActive(true).build();
        User savedUser = userRepository.save(user);

        // Publish user creation event for wallet onboarding
        log.info("User Registered Successfully userId={} , upiId={}", savedUser.getId(), savedUser.getUpiId());

        String token = jwtService.generateToken(savedUser.getId(), savedUser.getUpiId(), savedUser.getPhone());
        UserCreatedEvent event = UserCreatedEvent.builder().userId(savedUser.getId()).upiId(savedUser.getUpiId()).fullName(savedUser.getFullName()).phone(savedUser.getPhone()).createdAt(savedUser.getCreatedAt()).build();
        eventPublisher.publishUserCreated(event);
        return AuthResponse.of(token, savedUser.getUpiId(), savedUser.getFullName());
    }

    @Transactional(readOnly = true)

    public AuthResponse login(LoginRequest req) {
        //Find User By Phone
        User user = userRepository.findByPhone(req.getPhone()).orElseThrow(() -> new IllegalArgumentException("Invalid Pin or Phone"));

        // verify BCrypt Pin
        boolean matches = passwordEncoder.matches(req.getPin(), user.getPinHash());

        if (!matches)
            throw new IllegalArgumentException("Invalid Pin or Phone");
        // Check account status
        if (!user.isActive()) throw new IllegalStateException("User Account is not active");

        //Generate token
        String token = jwtService.generateToken(user.getId(), user.getUpiId(), user.getPhone());
        log.info("User logged in Successfully: userId={} ,upiId={}", user.getId(), user.getUpiId());
        //Return Auth Response
        return AuthResponse.of(token, user.getUpiId(), user.getFullName());

    }

    @Transactional(readOnly = true)
    public UserProfileResponse getByUserId(UUID userId) {
        // Done : find user by ID, map to UserProfileResponse

        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        log.info("Fetching user profile by userId={}", userId);
        return UserProfileResponse.builder().id(user.getId()).fullName(user.getFullName()).phone(user.getPhone()).email(user.getEmail()).upiId(user.getUpiId()).isActive(user.isActive()).createdAt(user.getCreatedAt()).build();


    }

    @Transactional(readOnly = true)
    public UserProfileResponse getByUpiId(String upiId) {

        User user = userRepository.findByUpiId(upiId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        log.info("Fetching user profile by upiId={}", upiId);
        return UserProfileResponse.builder()
        .id(user.getId())
        .fullName(user.getFullName())
        .phone(user.getPhone())
        .email(user.getEmail())
        .upiId(user.getUpiId())
        .isActive(user.isActive())
        .createdAt(user.getCreatedAt())
        .build();

    }
}

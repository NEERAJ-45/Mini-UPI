# RevPay — Current Implementation Snapshot

---

## 1. `UserService.java`

```java
package com.neeraj.upi.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neeraj.upi.user.dto.*;
import com.neeraj.upi.user.entity.OutboxEvent;
import com.neeraj.upi.user.entity.User;
import com.neeraj.upi.user.event.UserCreatedEvent;
import com.neeraj.upi.user.exception.InvalidCredentialsException;
import com.neeraj.upi.user.exception.UserAlreadyExistsException;
import com.neeraj.upi.user.exception.UserNotFoundException;
import com.neeraj.upi.user.repository.OutboxEventRepository;
import com.neeraj.upi.user.repository.UserRepository;
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
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UpiIdGenerator upiIdGenerator;
    private final ObjectMapper objectMapper;

    @Transactional
    public AuthResponse register(RegisterRequest req) {

        validateDuplicatePhone(req.getPhone());

        User savedUser = createAndSaveUser(req);

        saveUserCreatedOutboxEvent(savedUser);

        String token = jwtService.generateToken(
                savedUser.getId(),
                savedUser.getUpiId(),
                savedUser.getPhone());

        log.info("User registered: userId={} upiId={}",
                savedUser.getId(),
                savedUser.getUpiId());

        return AuthResponse.of(
                token,
                savedUser.getUpiId(),
                savedUser.getFullName());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        // 1. Lookup by phone — use a vague error message to avoid user enumeration
        User user = userRepository.findByPhone(req.getPhone())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid phone number or PIN"));

        // 2. Verify BCrypt PIN
        if (!passwordEncoder.matches(req.getPin(), user.getPinHash())) {
            throw new InvalidCredentialsException("Invalid phone number or PIN");
        }

        // 3. Check account status
        if (!user.isActive()) {
            throw new InvalidCredentialsException("User account is deactivated");
        }

        // 4. Issue JWT
        String token = jwtService.generateToken(user.getId(), user.getUpiId(), user.getPhone());
        log.info("User logged in: userId={} upiId={}", user.getId(), user.getUpiId());
        return AuthResponse.of(token, user.getUpiId(), user.getFullName());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getByUserId(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found for id: " + userId));
        log.info("Fetching user profile: userId={}", userId);
        return mapToProfileResponse(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getByUpiId(String upiId) {
        User user = userRepository.findByUpiId(upiId)
                .orElseThrow(() -> new UserNotFoundException("User not found for upiId: " + upiId));
        log.info("Fetching user profile: upiId={}", upiId);
        return mapToProfileResponse(user);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private UserProfileResponse mapToProfileResponse(User user) {
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

    private void validateDuplicatePhone(String phone) {
        if (userRepository.existsByPhone(phone)) {
            throw new UserAlreadyExistsException(
                    "Phone number already registered: " + phone);
        }
    }

    private User createAndSaveUser(RegisterRequest req) {

        String upiId = upiIdGenerator.generate(req.getFullName());

        String hashedPin = passwordEncoder.encode(req.getPin());

        User user = User.builder()
                .fullName(req.getFullName())
                .phone(req.getPhone())
                .email(req.getEmail())
                .upiId(upiId)
                .pinHash(hashedPin)
                .isActive(true)
                .build();

        return userRepository.save(user);
    }

    private void saveUserCreatedOutboxEvent(User user) {

        UserCreatedEvent event = buildUserCreatedEvent(user);

        try {

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(user.getId().toString())
                    .aggregateType("User")
                    .eventType("user.created")
                    .payload(objectMapper.writeValueAsString(event))
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Failed to serialize UserCreatedEvent for outbox",
                    e);
        }
    }

    private UserCreatedEvent buildUserCreatedEvent(User user) {

        return UserCreatedEvent.builder()
                .userId(user.getId())
                .upiId(user.getUpiId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
```

---

## 2. `AuthController.java`

```java
package com.neeraj.upi.user.controller;

import com.neeraj.upi.common.dto.ApiResponse;
import com.neeraj.upi.user.dto.AuthResponse;
import com.neeraj.upi.user.dto.LoginRequest;
import com.neeraj.upi.user.dto.RegisterRequest;
import com.neeraj.upi.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and login")
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user — returns JWT + UPI ID")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with phone + PIN — returns JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
```

---

## 3. `UserProfileController.java`

```java
package com.neeraj.upi.user.controller;

import com.neeraj.upi.common.dto.ApiResponse;
import com.neeraj.upi.user.dto.UserProfileResponse;
import com.neeraj.upi.user.service.JwtService;
import com.neeraj.upi.user.service.QrCodeService;
import com.neeraj.upi.user.service.UserService;
import com.neeraj.upi.user.util.QrPayloadBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Profile lookup and QR code generation")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserService   userService;
    private final JwtService    jwtService;
    private final QrCodeService qrCodeService;

    @GetMapping("/me")
    @Operation(summary = "Get current logged-in user's profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);             // strip "Bearer "
        UUID userId = UUID.fromString(jwtService.extractUserId(token));
        return ResponseEntity.ok(ApiResponse.ok(userService.getByUserId(userId)));
    }

    @GetMapping("/{upiId}")
    @Operation(summary = "Lookup any user by their UPI ID (for payment resolution)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getByUpiId(
            @PathVariable String upiId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getByUpiId(upiId)));
    }

    @GetMapping("/qr/{upiId}")
    @Operation(summary = "Get QR code for a UPI ID (returns Base64 PNG + UPI URI)")
    public ResponseEntity<ApiResponse<Map<String, String>>> getQrCode(
            @PathVariable String upiId) {
        UserProfileResponse profile = userService.getByUpiId(upiId);

        String upiUri = QrPayloadBuilder.builder()
                .upiId(upiId)
                .name(profile.getFullName())
                .build();

        String qrBase64 = qrCodeService.generateQrCodeBase64(upiId, profile.getFullName());

        Map<String, String> result = Map.of(
                "upiId",     upiId,
                "upiUri",    upiUri,
                "qrBase64",  qrBase64
        );

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
```

---

## 4. `GlobalExceptionHandler.java`

```java
package com.neeraj.upi.user.exception;

import com.neeraj.upi.common.dto.ApiResponse;
import com.neeraj.upi.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Handle business exceptions (UserAlreadyExists, UserNotFound, etc.) */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException ex) {
        log.error("Business exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getErrorCode(), ex.getMessage()));
    }

    /** Handle @Valid bean validation errors */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        
        log.error("Validation error: {}", errorMessage);
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.fail("VALIDATION_ERROR", errorMessage));
    }

    /** Handle bad login credentials */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity
                .status(401)
                .body(ApiResponse.fail("INVALID_CREDENTIALS", "Invalid phone number or PIN"));
    }

    /** Catch-all for unhandled exceptions */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity
                .internalServerError()
                .body(ApiResponse.fail("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

---

## 5. `RegisterRequest.java`

```java
package com.neeraj.upi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request body for registering a new user")
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100)
    @Schema(description = "Full name of the user", example = "Neeraj Kumar", minLength = 2, maxLength = 100)
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
    @Schema(description = "10-digit Indian mobile number (starts with 6–9)", example = "9876543210")
    private String phone;

    @Schema(description = "Optional email address", example = "neeraj@example.com", nullable = true)
    private String email;

    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
    @Schema(description = "4 to 6 digit numeric PIN (never stored in plain text)", example = "1234")
    private String pin;
}
```

---

## 6. `LoginRequest.java`

```java
package com.neeraj.upi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Request body for user login")
public class LoginRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number")
    @Schema(description = "10-digit Indian mobile number", example = "9876543210")
    private String phone;

    @NotBlank(message = "PIN is required")
    @Schema(description = "4 to 6 digit numeric PIN", example = "1234")
    private String pin;
}
```

---

## 7. `AuthResponse.java`

```java
package com.neeraj.upi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Authentication response returned after successful register or login")
public class AuthResponse {

    @Schema(description = "Signed JWT token — include in Authorization: Bearer <token> header", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String token;

    @Schema(description = "User's Virtual Payment Address (UPI ID)", example = "neeraj@miniupi")
    private String upiId;

    @Schema(description = "User's full name", example = "Neeraj Kumar")
    private String fullName;

    @Schema(description = "Token type — always 'Bearer'", example = "Bearer")
    private String tokenType;

    public static AuthResponse of(String token, String upiId, String fullName) {
        return AuthResponse.builder()
                .token(token)
                .upiId(upiId)
                .fullName(fullName)
                .tokenType("Bearer")
                .build();
    }
}
```

---

## 8. `UserProfileResponse.java`

```java
package com.neeraj.upi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "User profile details")
public class UserProfileResponse {

    @Schema(description = "Unique user identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Full name of the user", example = "Neeraj Kumar")
    private String fullName;

    @Schema(description = "Registered mobile number", example = "9876543210")
    private String phone;

    @Schema(description = "Optional email address", example = "neeraj@example.com", nullable = true)
    private String email;

    @Schema(description = "Virtual Payment Address (UPI ID)", example = "neeraj@miniupi")
    private String upiId;

    @Schema(description = "Whether the account is active", example = "true")
    private boolean isActive;

    @Schema(description = "Account creation timestamp (UTC)", example = "2024-01-15T10:30:00Z")
    private Instant createdAt;
}
```

---

## 9. `QrCodeResponse.java`

```java
package com.neeraj.upi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "QR code generation response containing the UPI URI and Base64 encoded image")
public class QrCodeResponse {

    @Schema(description = "Virtual Payment Address (UPI ID)", example = "neeraj@miniupi")
    private String upiId;

    @Schema(description = "Full name of the payee", example = "Neeraj Kumar")
    private String fullName;

    @Schema(description = "UPI deep-link URI — can be scanned by any UPI app", example = "upi://pay?pa=neeraj@miniupi&pn=Neeraj+Kumar&cu=INR")
    private String upiUri;

    @Schema(description = "Base64-encoded PNG QR code image (300×300 px). Render with: <img src='data:image/png;base64,{qrCodeBase64}' />")
    private String qrCodeBase64;
}
```

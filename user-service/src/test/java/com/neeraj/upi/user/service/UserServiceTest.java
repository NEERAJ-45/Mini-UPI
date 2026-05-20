package com.neeraj.upi.user.service;

import com.neeraj.upi.user.dto.AuthResponse;
import com.neeraj.upi.user.dto.LoginRequest;
import com.neeraj.upi.user.dto.RegisterRequest;
import com.neeraj.upi.user.dto.UserProfileResponse;
import com.neeraj.upi.user.entity.User;
import com.neeraj.upi.user.event.UserCreatedEvent;
import com.neeraj.upi.user.kafka.UserEventPublisher;
import com.neeraj.upi.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UpiIdGenerator upiIdGenerator;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    @Captor
    private ArgumentCaptor<UserCreatedEvent> eventCaptor;

    private static final String TOKEN = "jwt.token.test";
    private static final String UPI_ID = "john@miniupi";
    private static final String HASHED_PIN = "$2a$10$hashedPinValue";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();

    // ── register() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("register should create user, publish event, and return AuthResponse")
    void registerShouldSucceed() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("John Doe");
        req.setPhone("9876543210");
        req.setEmail("john@example.com");
        req.setPin("1234");

        User savedUser = User.builder()
                .id(USER_ID)
                .fullName("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .upiId(UPI_ID)
                .pinHash(HASHED_PIN)
                .isActive(true)
                .createdAt(NOW)
                .build();

        when(userRepository.existsByPhone(req.getPhone())).thenReturn(false);
        when(upiIdGenerator.generate(req.getFullName())).thenReturn(UPI_ID);
        when(passwordEncoder.encode(req.getPin())).thenReturn(HASHED_PIN);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(USER_ID, UPI_ID, req.getPhone())).thenReturn(TOKEN);

        AuthResponse response = userService.register(req);

        assertNotNull(response);
        assertEquals(TOKEN, response.getToken());
        assertEquals(UPI_ID, response.getUpiId());
        assertEquals("John Doe", response.getFullName());
        assertEquals("Bearer", response.getTokenType());

        verify(userRepository).existsByPhone(req.getPhone());
        verify(upiIdGenerator).generate(req.getFullName());
        verify(passwordEncoder).encode(req.getPin());
        verify(userRepository).save(userCaptor.capture());
        verify(jwtService).generateToken(USER_ID, UPI_ID, req.getPhone());
        verify(eventPublisher).publishUserCreated(eventCaptor.capture());

        User capturedUser = userCaptor.getValue();
        assertEquals("John Doe", capturedUser.getFullName());
        assertEquals("9876543210", capturedUser.getPhone());
        assertEquals("john@example.com", capturedUser.getEmail());
        assertEquals(UPI_ID, capturedUser.getUpiId());
        assertEquals(HASHED_PIN, capturedUser.getPinHash());
        assertTrue(capturedUser.isActive());

        UserCreatedEvent capturedEvent = eventCaptor.getValue();
        assertEquals(USER_ID, capturedEvent.getUserId());
        assertEquals(UPI_ID, capturedEvent.getUpiId());
        assertEquals("John Doe", capturedEvent.getFullName());
        assertEquals("9876543210", capturedEvent.getPhone());
        assertEquals(NOW, capturedEvent.getCreatedAt());
    }

    @Test
    @DisplayName("register should throw IllegalArgumentException when phone already exists")
    void registerShouldThrowWhenPhoneExists() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("John Doe");
        req.setPhone("9876543210");
        req.setPin("1234");

        when(userRepository.existsByPhone(req.getPhone())).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.register(req));
        assertEquals("Phone number already registered", ex.getMessage());

        verify(userRepository).existsByPhone(req.getPhone());
        verify(upiIdGenerator, never()).generate(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserCreated(any());
    }

    @Test
    @DisplayName("register should handle user with no email")
    void registerShouldHandleNullEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("Jane Doe");
        req.setPhone("9876543211");
        req.setPin("5678");

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Jane Doe")
                .phone("9876543211")
                .upiId("jane@miniupi")
                .pinHash(HASHED_PIN)
                .isActive(true)
                .createdAt(NOW)
                .build();

        when(userRepository.existsByPhone(req.getPhone())).thenReturn(false);
        when(upiIdGenerator.generate(req.getFullName())).thenReturn("jane@miniupi");
        when(passwordEncoder.encode(req.getPin())).thenReturn(HASHED_PIN);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(any(UUID.class), anyString(), anyString())).thenReturn(TOKEN);

        AuthResponse response = userService.register(req);

        assertNotNull(response);
        assertEquals("Jane Doe", response.getFullName());

        verify(eventPublisher).publishUserCreated(any());
    }

    // ── login() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login should return AuthResponse for valid credentials")
    void loginShouldSucceed() {
        LoginRequest req = new LoginRequest();
        req.setPhone("9876543210");
        req.setPin("1234");

        User user = User.builder()
                .id(USER_ID)
                .fullName("John Doe")
                .phone("9876543210")
                .upiId(UPI_ID)
                .pinHash(HASHED_PIN)
                .isActive(true)
                .build();

        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.getPin(), HASHED_PIN)).thenReturn(true);
        when(jwtService.generateToken(USER_ID, UPI_ID, req.getPhone())).thenReturn(TOKEN);

        AuthResponse response = userService.login(req);

        assertNotNull(response);
        assertEquals(TOKEN, response.getToken());
        assertEquals(UPI_ID, response.getUpiId());
        assertEquals("John Doe", response.getFullName());
        assertEquals("Bearer", response.getTokenType());

        verify(userRepository).findByPhone(req.getPhone());
        verify(passwordEncoder).matches(req.getPin(), HASHED_PIN);
        verify(jwtService).generateToken(USER_ID, UPI_ID, req.getPhone());
    }

    @Test
    @DisplayName("login should throw IllegalArgumentException when phone not found")
    void loginShouldThrowWhenPhoneNotFound() {
        LoginRequest req = new LoginRequest();
        req.setPhone("9876543210");
        req.setPin("1234");

        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(req));
        assertEquals("Invalid Pin or Phone", ex.getMessage());

        verify(userRepository).findByPhone(req.getPhone());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("login should throw IllegalArgumentException when PIN does not match")
    void loginShouldThrowWhenPinMismatch() {
        LoginRequest req = new LoginRequest();
        req.setPhone("9876543210");
        req.setPin("wrongPin");

        User user = User.builder()
                .id(USER_ID)
                .fullName("John Doe")
                .phone("9876543210")
                .upiId(UPI_ID)
                .pinHash(HASHED_PIN)
                .isActive(true)
                .build();

        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.getPin(), HASHED_PIN)).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(req));
        assertEquals("Invalid Pin or Phone", ex.getMessage());

        verify(passwordEncoder).matches(req.getPin(), HASHED_PIN);
        verify(jwtService, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("login should throw IllegalStateException when user is inactive")
    void loginShouldThrowWhenUserInactive() {
        LoginRequest req = new LoginRequest();
        req.setPhone("9876543210");
        req.setPin("1234");

        User user = User.builder()
                .id(USER_ID)
                .fullName("John Doe")
                .phone("9876543210")
                .upiId(UPI_ID)
                .pinHash(HASHED_PIN)
                .isActive(false)
                .build();

        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.getPin(), HASHED_PIN)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> userService.login(req));
        assertEquals("User Account is not active", ex.getMessage());

        verify(jwtService, never()).generateToken(any(), anyString(), anyString());
    }

    // ── getByUserId() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByUserId should return profile when user exists")
    void getByUserIdShouldSucceed() {
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .fullName("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .upiId(UPI_ID)
                .isActive(true)
                .createdAt(NOW)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getByUserId(userId);

        assertNotNull(response);
        assertEquals(userId, response.getId());
        assertEquals("John Doe", response.getFullName());
        assertEquals("9876543210", response.getPhone());
        assertEquals("john@example.com", response.getEmail());
        assertEquals(UPI_ID, response.getUpiId());
        assertTrue(response.isActive());
        assertEquals(NOW, response.getCreatedAt());
    }

    @Test
    @DisplayName("getByUserId should throw IllegalArgumentException when user not found")
    void getByUserIdShouldThrowWhenNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.getByUserId(userId));
        assertEquals("User not found", ex.getMessage());
    }

    // ── getByUpiId() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByUpiId should return profile when user exists")
    void getByUpiIdShouldSucceed() {
        User user = User.builder()
                .id(USER_ID)
                .fullName("John Doe")
                .phone("9876543210")
                .email("john@example.com")
                .upiId(UPI_ID)
                .isActive(true)
                .createdAt(NOW)
                .build();

        when(userRepository.findByUpiId(UPI_ID)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getByUpiId(UPI_ID);

        assertNotNull(response);
        assertEquals(USER_ID, response.getId());
        assertEquals("John Doe", response.getFullName());
        assertEquals("9876543210", response.getPhone());
        assertEquals("john@example.com", response.getEmail());
        assertEquals(UPI_ID, response.getUpiId());
        assertTrue(response.isActive());
        assertEquals(NOW, response.getCreatedAt());
    }

    @Test
    @DisplayName("getByUpiId should throw IllegalArgumentException when user not found")
    void getByUpiIdShouldThrowWhenNotFound() {
        String upiId = "unknown@miniupi";

        when(userRepository.findByUpiId(upiId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.getByUpiId(upiId));
        assertEquals("User not found", ex.getMessage());
    }
}

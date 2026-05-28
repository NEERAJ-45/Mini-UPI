package com.neeraj.upi.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neeraj.upi.user.dto.AuthResponse;
import com.neeraj.upi.user.dto.LoginRequest;
import com.neeraj.upi.user.dto.RegisterRequest;
import com.neeraj.upi.user.dto.UserProfileResponse;
import com.neeraj.upi.user.entity.OutboxEvent;
import com.neeraj.upi.user.entity.User;
import com.neeraj.upi.user.exception.InvalidCredentialsException;
import com.neeraj.upi.user.exception.UserAlreadyExistsException;
import com.neeraj.upi.user.exception.UserNotFoundException;
import com.neeraj.upi.user.repository.OutboxEventRepository;
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

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private UserRepository        userRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PasswordEncoder        passwordEncoder;
    @Mock private JwtService             jwtService;
    @Mock private UpiIdGenerator         upiIdGenerator;
    @Mock private ObjectMapper           objectMapper;

    @InjectMocks
    private UserService userService;

    // ── Captors ───────────────────────────────────────────────────────────────

    @Captor private ArgumentCaptor<User>        userCaptor;
    @Captor private ArgumentCaptor<OutboxEvent> outboxCaptor;

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String  TOKEN      = "jwt.token.test";
    private static final String  UPI_ID     = "john@miniupi";
    private static final String  HASHED_PIN = "$2a$10$hashedPinValue";
    private static final UUID    USER_ID    = UUID.randomUUID();
    private static final Instant NOW        = Instant.now();

    // ── register() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: saves User + OutboxEvent in the same transaction and returns AuthResponse")
    void register_success_savesUserAndOutboxEvent() throws JsonProcessingException {
        RegisterRequest req = buildRegisterRequest("John Doe", "9876543210", "john@example.com", "1234");

        User savedUser = buildUser(USER_ID, "John Doe", "9876543210", "john@example.com", UPI_ID, true);

        when(userRepository.existsByPhone(req.getPhone())).thenReturn(false);
        when(upiIdGenerator.generate(req.getFullName())).thenReturn(UPI_ID);
        when(passwordEncoder.encode(req.getPin())).thenReturn(HASHED_PIN);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"userId\":\"" + USER_ID + "\"}");
        when(jwtService.generateToken(USER_ID, UPI_ID, req.getPhone())).thenReturn(TOKEN);

        AuthResponse response = userService.register(req);

        // ── Assert response ──────────────────────────────────────────────────
        assertNotNull(response);
        assertEquals(TOKEN,      response.getToken());
        assertEquals(UPI_ID,     response.getUpiId());
        assertEquals("John Doe", response.getFullName());
        assertEquals("Bearer",   response.getTokenType());

        // ── Verify User was saved ────────────────────────────────────────────
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();
        assertEquals("John Doe",          capturedUser.getFullName());
        assertEquals("9876543210",         capturedUser.getPhone());
        assertEquals("john@example.com",   capturedUser.getEmail());
        assertEquals(UPI_ID,               capturedUser.getUpiId());
        assertEquals(HASHED_PIN,           capturedUser.getPinHash());
        assertTrue(capturedUser.isActive());

        // ── Verify OutboxEvent was saved (not direct Kafka publish) ──────────
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertEquals(USER_ID.toString(), outbox.getAggregateId());
        assertEquals("User",             outbox.getAggregateType());
        assertEquals("user.created",     outbox.getEventType());
        assertFalse(outbox.isProcessed(), "OutboxEvent must start as unprocessed");
        assertNotNull(outbox.getPayload());

        // ── Verify JWT issued ────────────────────────────────────────────────
        verify(jwtService).generateToken(USER_ID, UPI_ID, req.getPhone());
    }

    @Test
    @DisplayName("register: throws UserAlreadyExistsException when phone is already registered")
    void register_duplicatePhone_throwsUserAlreadyExistsException() {
        RegisterRequest req = buildRegisterRequest("John Doe", "9876543210", null, "1234");
        when(userRepository.existsByPhone(req.getPhone())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.register(req));

        verify(userRepository).existsByPhone(req.getPhone());
        verify(upiIdGenerator,       never()).generate(anyString());
        verify(passwordEncoder,      never()).encode(anyString());
        verify(userRepository,       never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: user with no email is accepted (email is optional)")
    void register_nullEmail_succeeds() throws JsonProcessingException {
        RegisterRequest req = buildRegisterRequest("Jane Doe", "9876543211", null, "5678");
        User savedUser = buildUser(UUID.randomUUID(), "Jane Doe", "9876543211", null, "jane@miniupi", true);

        when(userRepository.existsByPhone(req.getPhone())).thenReturn(false);
        when(upiIdGenerator.generate(req.getFullName())).thenReturn("jane@miniupi");
        when(passwordEncoder.encode(req.getPin())).thenReturn(HASHED_PIN);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(jwtService.generateToken(any(UUID.class), anyString(), anyString())).thenReturn(TOKEN);

        AuthResponse response = userService.register(req);

        assertNotNull(response);
        assertEquals("Jane Doe", response.getFullName());
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    // ── login() ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: returns AuthResponse for valid phone + PIN")
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest req  = buildLoginRequest("9876543210", "1234");
        User user = buildUser(USER_ID, "John Doe", "9876543210", null, UPI_ID, true);

        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.getPin(), HASHED_PIN)).thenReturn(true);
        when(jwtService.generateToken(USER_ID, UPI_ID, req.getPhone())).thenReturn(TOKEN);

        AuthResponse response = userService.login(req);

        assertNotNull(response);
        assertEquals(TOKEN,      response.getToken());
        assertEquals(UPI_ID,     response.getUpiId());
        assertEquals("John Doe", response.getFullName());
        assertEquals("Bearer",   response.getTokenType());

        verify(userRepository).findByPhone(req.getPhone());
        verify(passwordEncoder).matches(req.getPin(), HASHED_PIN);
        verify(jwtService).generateToken(USER_ID, UPI_ID, req.getPhone());
    }

    @Test
    @DisplayName("login: throws InvalidCredentialsException when phone not found")
    void login_phoneNotFound_throwsInvalidCredentials() {
        LoginRequest req = buildLoginRequest("9999999999", "1234");
        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> userService.login(req));

        verify(userRepository).findByPhone(req.getPhone());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtService,      never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("login: throws InvalidCredentialsException when PIN does not match")
    void login_wrongPin_throwsInvalidCredentials() {
        LoginRequest req  = buildLoginRequest("9876543210", "wrong");
        User user = buildUser(USER_ID, "John Doe", "9876543210", null, UPI_ID, true);

        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.getPin(), HASHED_PIN)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> userService.login(req));

        verify(passwordEncoder).matches(req.getPin(), HASHED_PIN);
        verify(jwtService, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("login: throws InvalidCredentialsException when account is inactive")
    void login_inactiveAccount_throwsInvalidCredentials() {
        LoginRequest req  = buildLoginRequest("9876543210", "1234");
        User user = buildUser(USER_ID, "John Doe", "9876543210", null, UPI_ID, false);

        when(userRepository.findByPhone(req.getPhone())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(req.getPin(), HASHED_PIN)).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () -> userService.login(req));

        verify(jwtService, never()).generateToken(any(), anyString(), anyString());
    }

    // ── getByUserId() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByUserId: returns UserProfileResponse when user exists")
    void getByUserId_found_returnsProfile() {
        User user = buildUser(USER_ID, "John Doe", "9876543210", "john@example.com", UPI_ID, true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getByUserId(USER_ID);

        assertNotNull(response);
        assertEquals(USER_ID,            response.getId());
        assertEquals("John Doe",          response.getFullName());
        assertEquals("9876543210",        response.getPhone());
        assertEquals("john@example.com",  response.getEmail());
        assertEquals(UPI_ID,              response.getUpiId());
        assertTrue(response.isActive());
    }

    @Test
    @DisplayName("getByUserId: throws UserNotFoundException when user does not exist")
    void getByUserId_notFound_throwsUserNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getByUserId(unknown));
    }

    // ── getByUpiId() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByUpiId: returns UserProfileResponse when user exists")
    void getByUpiId_found_returnsProfile() {
        User user = buildUser(USER_ID, "John Doe", "9876543210", "john@example.com", UPI_ID, true);
        when(userRepository.findByUpiId(UPI_ID)).thenReturn(Optional.of(user));

        UserProfileResponse response = userService.getByUpiId(UPI_ID);

        assertNotNull(response);
        assertEquals(USER_ID,            response.getId());
        assertEquals("John Doe",          response.getFullName());
        assertEquals("9876543210",        response.getPhone());
        assertEquals("john@example.com",  response.getEmail());
        assertEquals(UPI_ID,              response.getUpiId());
        assertTrue(response.isActive());
    }

    @Test
    @DisplayName("getByUpiId: throws UserNotFoundException when upiId does not exist")
    void getByUpiId_notFound_throwsUserNotFoundException() {
        String unknown = "ghost@miniupi";
        when(userRepository.findByUpiId(unknown)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getByUpiId(unknown));
    }

    // ── Private builder helpers ───────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String name, String phone, String email, String pin) {
        RegisterRequest req = new RegisterRequest();
        req.setFullName(name);
        req.setPhone(phone);
        req.setEmail(email);
        req.setPin(pin);
        return req;
    }

    private LoginRequest buildLoginRequest(String phone, String pin) {
        LoginRequest req = new LoginRequest();
        req.setPhone(phone);
        req.setPin(pin);
        return req;
    }

    private User buildUser(UUID id, String name, String phone, String email, String upiId, boolean active) {
        return User.builder()
                .id(id)
                .fullName(name)
                .phone(phone)
                .email(email)
                .upiId(upiId)
                .pinHash(HASHED_PIN)
                .isActive(active)
                .createdAt(NOW)
                .build();
    }
}

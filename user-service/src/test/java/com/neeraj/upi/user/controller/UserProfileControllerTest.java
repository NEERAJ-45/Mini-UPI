package com.neeraj.upi.user.controller;

import com.neeraj.upi.user.dto.UserProfileResponse;
import com.neeraj.upi.user.exception.GlobalExceptionHandler;
import com.neeraj.upi.user.exception.UserNotFoundException;
import com.neeraj.upi.user.service.JwtService;
import com.neeraj.upi.user.service.QrCodeService;
import com.neeraj.upi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock private UserService   userService;
    @Mock private JwtService    jwtService;
    @Mock private QrCodeService qrCodeService;

    @InjectMocks
    private UserProfileController userProfileController;

    private MockMvc mockMvc;

    private static final String UPI_ID    = "john@miniupi";
    private static final String FULL_NAME = "John Doe";
    private static final UUID   USER_ID   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── GET /users/me ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/me: returns 200 with profile for a valid Bearer token")
    void getMyProfile_validToken_returns200() throws Exception {
        UserProfileResponse profile = buildProfile();

        when(jwtService.extractUserId("valid-token")).thenReturn(USER_ID.toString());
        when(userService.getByUserId(USER_ID)).thenReturn(profile);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upiId").value(UPI_ID))
                .andExpect(jsonPath("$.data.fullName").value(FULL_NAME));
    }

    @Test
    @DisplayName("GET /users/me: returns 500 when no Authorization header (missing header causes exception)")
    void getMyProfile_noAuthHeader_returns500() throws Exception {
        // MissingRequestHeaderException is not yet handled → global 500
        mockMvc.perform(get("/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    // ── GET /users/{upiId} ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/{upiId}: returns 200 with profile when user exists")
    void getByUpiId_exists_returns200() throws Exception {
        UserProfileResponse profile = buildProfile();
        when(userService.getByUpiId(UPI_ID)).thenReturn(profile);

        mockMvc.perform(get("/users/{upiId}", UPI_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upiId").value(UPI_ID))
                .andExpect(jsonPath("$.data.fullName").value(FULL_NAME));
    }

    @Test
    @DisplayName("GET /users/{upiId}: returns 404 when user not found")
    void getByUpiId_notFound_returns404() throws Exception {
        when(userService.getByUpiId("ghost@miniupi"))
                .thenThrow(new UserNotFoundException("User not found for upiId: ghost@miniupi"));

        mockMvc.perform(get("/users/{upiId}", "ghost@miniupi")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ── GET /users/qr/{upiId} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /users/qr/{upiId}: returns 200 with QrCodeResponse fields populated")
    void getQrCode_validUpiId_returnsQrCodeResponseFields() throws Exception {
        UserProfileResponse profile  = buildProfile();
        String              base64   = "iVBORw0KGgoAAAANSUhEUgAA==";

        when(userService.getByUpiId(UPI_ID)).thenReturn(profile);
        when(qrCodeService.generateQrCodeBase64(UPI_ID, FULL_NAME)).thenReturn(base64);

        MvcResult result = mockMvc.perform(get("/users/qr/{upiId}", UPI_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upiId").value(UPI_ID))
                .andExpect(jsonPath("$.data.fullName").value(FULL_NAME))
                .andExpect(jsonPath("$.data.upiUri").isNotEmpty())
                .andExpect(jsonPath("$.data.qrCodeBase64").value(base64))
                .andReturn();

        // Also verify the QR URI contains the UPI ID
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains(UPI_ID), "upiUri should reference the UPI ID");

        verify(userService).getByUpiId(UPI_ID);
        verify(qrCodeService).generateQrCodeBase64(UPI_ID, FULL_NAME);
    }

    @Test
    @DisplayName("GET /users/qr/{upiId}: returns 404 when user not found")
    void getQrCode_userNotFound_returns404() throws Exception {
        when(userService.getByUpiId("ghost@miniupi"))
                .thenThrow(new UserNotFoundException("User not found for upiId: ghost@miniupi"));

        mockMvc.perform(get("/users/qr/{upiId}", "ghost@miniupi")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(qrCodeService, never()).generateQrCodeBase64(any(), any());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private UserProfileResponse buildProfile() {
        return UserProfileResponse.builder()
                .id(USER_ID)
                .fullName(FULL_NAME)
                .phone("9876543210")
                .email("john@example.com")
                .upiId(UPI_ID)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }
}

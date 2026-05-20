package com.neeraj.upi.user.controller;

import com.neeraj.upi.user.exception.GlobalExceptionHandler;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @Mock
    private QrCodeService qrCodeService;

    @InjectMocks
    private UserProfileController userProfileController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /users/me should return 500 until implemented")
    void getMyProfile_throwsNotImplemented() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /users/me should return 401 when no auth header")
    void getMyProfile_returns401WithoutAuth() throws Exception {
        mockMvc.perform(get("/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /users/{upiId} should return 500 until implemented")
    void getByUpiId_throwsNotImplemented() throws Exception {
        mockMvc.perform(get("/users/john@miniupi")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("GET /users/qr/{upiId} should return 500 until implemented")
    void getQrCode_throwsNotImplemented() throws Exception {
        mockMvc.perform(get("/users/qr/john@miniupi")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
    }
}

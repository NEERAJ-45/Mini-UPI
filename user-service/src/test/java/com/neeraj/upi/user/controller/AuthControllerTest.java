package com.neeraj.upi.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neeraj.upi.user.dto.LoginRequest;
import com.neeraj.upi.user.dto.RegisterRequest;
import com.neeraj.upi.user.exception.GlobalExceptionHandler;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /auth/register should return 500 until implemented")
    void registerEndpoint_throwsNotImplemented() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("John Doe");
        req.setPhone("9876543210");
        req.setPin("1234");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("POST /auth/login should return 500 until implemented")
    void loginEndpoint_throwsNotImplemented() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setPhone("9876543210");
        req.setPin("1234");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("POST /auth/register should return 400 for invalid payload")
    void registerEndpoint_returns400ForInvalidPayload() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFullName("");  // @NotBlank violation
        req.setPhone("123");  // @Pattern violation
        req.setPin("");       // @NotBlank violation

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login should return 400 for invalid payload")
    void loginEndpoint_returns400ForInvalidPayload() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setPhone("");     // @NotBlank violation
        req.setPin("");       // @NotBlank violation

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}

package com.neeraj.upi.user.controller;

import com.neeraj.upi.common.dto.ApiResponse;
import com.neeraj.upi.user.dto.QrCodeResponse;
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
    public ResponseEntity<ApiResponse<QrCodeResponse>> getQrCode(@PathVariable String upiId) {
        UserProfileResponse profile = userService.getByUpiId(upiId);

        String upiUri = QrPayloadBuilder.builder()
                .upiId(upiId)
                .name(profile.getFullName())
                .build();

        String qrBase64 = qrCodeService.generateQrCodeBase64(upiId, profile.getFullName());

        QrCodeResponse response = QrCodeResponse.builder()
                .upiId(upiId)
                .fullName(profile.getFullName())
                .upiUri(upiUri)
                .qrCodeBase64(qrBase64)
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

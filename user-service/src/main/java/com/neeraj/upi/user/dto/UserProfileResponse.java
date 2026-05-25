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

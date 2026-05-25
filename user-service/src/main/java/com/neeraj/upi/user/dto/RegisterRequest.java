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

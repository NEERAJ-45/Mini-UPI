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

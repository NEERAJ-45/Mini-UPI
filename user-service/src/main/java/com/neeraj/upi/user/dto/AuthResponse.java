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

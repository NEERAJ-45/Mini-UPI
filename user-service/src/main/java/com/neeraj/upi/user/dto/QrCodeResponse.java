package com.neeraj.upi.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrCodeResponse {
    private String upiId;
    private String fullName;
    private String upiUri;
    private String qrCodeBase64;
}

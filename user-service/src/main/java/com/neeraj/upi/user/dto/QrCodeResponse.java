package com.neeraj.upi.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "QR code generation response containing the UPI URI and Base64 encoded image")
public class QrCodeResponse {

    @Schema(description = "Virtual Payment Address (UPI ID)", example = "neeraj@miniupi")
    private String upiId;

    @Schema(description = "Full name of the payee", example = "Neeraj Kumar")
    private String fullName;

    @Schema(description = "UPI deep-link URI — can be scanned by any UPI app", example = "upi://pay?pa=neeraj@miniupi&pn=Neeraj+Kumar&cu=INR")
    private String upiUri;

    @Schema(description = "Base64-encoded PNG QR code image (300×300 px). Render with: <img src='data:image/png;base64,{qrCodeBase64}' />")
    private String qrCodeBase64;
}

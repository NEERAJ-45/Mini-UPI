package com.neeraj.upi.user.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility builder to construct standard UPI payment URIs.
 * Format: upi://pay?pa={upiId}&pn={name}&cu={currency}&am={amount}&tn={note}
 */
public class QrPayloadBuilder {

    private String upiId;
    private String name;
    private String currency = "INR";
    private String amount;
    private String note;

    public static QrPayloadBuilder builder() {
        return new QrPayloadBuilder();
    }

    public QrPayloadBuilder upiId(String upiId) {
        this.upiId = upiId;
        return this;
    }

    public QrPayloadBuilder name(String name) {
        this.name = name;
        return this;
    }

    public QrPayloadBuilder currency(String currency) {
        this.currency = currency;
        return this;
    }

    public QrPayloadBuilder amount(String amount) {
        this.amount = amount;
        return this;
    }

    public QrPayloadBuilder note(String note) {
        this.note = note;
        return this;
    }

    public String build() {
        if (upiId == null || upiId.isBlank()) {
            throw new IllegalArgumentException("UPI ID (pa) is mandatory");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Payee name (pn) is mandatory");
        }

        StringBuilder uri = new StringBuilder("upi://pay?");
        uri.append("pa=").append(encode(upiId));
        uri.append("&pn=").append(encode(name));
        uri.append("&cu=").append(encode(currency));

        if (amount != null && !amount.isBlank()) {
            uri.append("&am=").append(encode(amount));
        }
        if (note != null && !note.isBlank()) {
            uri.append("&tn=").append(encode(note));
        }

        return uri.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

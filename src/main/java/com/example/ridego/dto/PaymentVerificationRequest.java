package com.example.ridego.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentVerificationRequest {

    @NotBlank
    private String razorpayPaymentId;

    @NotBlank
    private String razorpayOrderId;

    @NotBlank
    private String razorpaySignature;
}

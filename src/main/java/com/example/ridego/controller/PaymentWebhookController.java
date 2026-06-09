package com.example.ridego.controller;

import com.example.ridego.service.RideService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentWebhookController {

    private final RideService rideService;

    public PaymentWebhookController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping("/razorpay/webhook")
    public ResponseEntity<?> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        rideService.handleRazorpayWebhook(payload, signature);
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }
}

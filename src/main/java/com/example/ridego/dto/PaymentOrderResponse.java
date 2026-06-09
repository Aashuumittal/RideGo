package com.example.ridego.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentOrderResponse {

    private String keyId;
    private String orderId;
    private Double amount;
    private Long amountPaise;
    private String currency;
}

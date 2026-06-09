package com.example.ridego.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    private String rideId;
    private String userId;
    private String driverId;
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private Double amount;
    private String currency;
    private String status;
    private Date timestamp;
}

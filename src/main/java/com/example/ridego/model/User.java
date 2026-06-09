package com.example.ridego.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String username;
    private String password;
    private String role; // ROLE_USER or ROLE_DRIVER

    private String phoneNumber;
    private String vehicleType; // Sedan / SUV / Premium
    private String vehicleName;
    private String vehicleNumber;

    @Builder.Default
    private Long totalRatingsCount = 0L;

    @Builder.Default
    private Double averageRating = 0.0;
}

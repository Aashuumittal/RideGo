package com.example.ridego.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateRideRequest {

    @NotBlank(message = "Pickup is required")
    private String pickupLocation;

    @NotBlank(message = "Drop is required")
    private String dropLocation;

    @NotBlank(message = "Vehicle type is required")
    private String vehicleType;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;
    private Double distanceMeters;
    private Double durationSeconds;
    private LocalDateTime scheduledAt;
}

package com.example.ridego.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
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

    // The user's chosen pickup/departure date & time
    private LocalDateTime scheduledAt;

    @Min(value = 1, message = "At least one passenger is required")
    private Integer passengerCount = 1;

    @Min(value = 0, message = "Luggage count cannot be negative")
    private Integer luggageCount = 0;
}
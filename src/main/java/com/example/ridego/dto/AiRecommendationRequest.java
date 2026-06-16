package com.example.ridego.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiRecommendationRequest {

    @NotBlank(message = "Pickup location is required")
    private String pickupLocation;

    @NotBlank(message = "Destination is required")
    private String destination;

    @DecimalMin(value = "0.01", message = "Distance must be greater than zero")
    @NotNull(message = "Distance is required")
    private Double distanceKm;

    @NotBlank(message = "Selected vehicle is required")
    private String selectedVehicle;

    @Min(value = 1, message = "At least one passenger is required")
    @NotNull(message = "Passenger count is required")
    private Integer passengerCount;

    @Min(value = 0, message = "Luggage count cannot be negative")
    @NotNull(message = "Luggage count is required")
    private Integer luggageCount;

    // The user's chosen pickup/departure time (optional)
    private LocalDateTime scheduledPickupAt;

}
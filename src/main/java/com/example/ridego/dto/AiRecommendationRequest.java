package com.example.ridego.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AiRecommendationRequest {

    @DecimalMin(value = "0.01", message = "Distance must be greater than zero")
    @NotNull(message = "Distance is required")
    private Double distanceKm;

    @DecimalMin(value = "0.01", message = "ETA must be greater than zero")
    @NotNull(message = "ETA is required")
    private Double etaMinutes;

    @DecimalMin(value = "0.0", message = "Sedan fare cannot be negative")
    @NotNull(message = "Sedan fare is required")
    private Double sedanFare;

    @DecimalMin(value = "0.0", message = "SUV fare cannot be negative")
    @NotNull(message = "SUV fare is required")
    private Double suvFare;

    @DecimalMin(value = "0.0", message = "Premium fare cannot be negative")
    @NotNull(message = "Premium fare is required")
    private Double premiumFare;
}

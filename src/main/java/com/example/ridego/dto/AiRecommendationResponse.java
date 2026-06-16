package com.example.ridego.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendationResponse {

    private String recommendedVehicle;
    private String selectedVehicle;
    private boolean suitable;
    private String capacityWarning;
    private int passengerCount;
    private int luggageCount;
    private int seatingCapacity;
    private int luggageCapacity;
    private double etaMinutes;
    private double fare;

    // The user's chosen pickup time (passed through for display)
    private LocalDateTime scheduledPickupAt;

    // Estimated arrival computed from scheduledPickupAt + ETA (informational only)
    private LocalDateTime estimatedArrivalAt;

    private String plannerGuidance;
    private boolean aiGenerated;
}
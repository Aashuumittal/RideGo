package com.example.ridego.service.impl;

import com.example.ridego.dto.AiRecommendationRequest;
import com.example.ridego.dto.AiRecommendationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiServiceImplTests {

    private final GeminiServiceImpl service = new GeminiServiceImpl("", "gemini-2.5-flash", new ObjectMapper());

    @Test
    void plannerUsesBackendCalculatedSuitabilityEtaAndEstimatedArrival() {
        AiRecommendationRequest request = request("Sedan", 5, 3);
        // User picks up at 19:15; ETA for Sedan over 30km ~= 40 min → arrival ~19:55
        request.setScheduledPickupAt(LocalDateTime.of(2026, 6, 15, 19, 15));

        AiRecommendationResponse response = service.planRide(request);

        // Sedan seats 4 — not suitable for 5 passengers
        assertThat(response.isSuitable()).isFalse();
        // SUV (6 seats, 4 bags) is the smallest suitable vehicle
        assertThat(response.getRecommendedVehicle()).isEqualTo("SUV");
        assertThat(response.getCapacityWarning()).contains("up to 4 passengers and 2 bags");
        // Sedan ETA over 30 km at 45 km/h = 40.0 min
        assertThat(response.getEtaMinutes()).isEqualTo(40.0);
        // scheduledPickupAt is echoed back unchanged
        assertThat(response.getScheduledPickupAt()).isEqualTo(LocalDateTime.of(2026, 6, 15, 19, 15));
        // estimatedArrivalAt = scheduledPickupAt + roundedEtaMinutes (40)
        assertThat(response.getEstimatedArrivalAt()).isEqualTo(LocalDateTime.of(2026, 6, 15, 19, 55));
        // No API key → AI not generated, fallback guidance used
        assertThat(response.isAiGenerated()).isFalse();
    }

    private AiRecommendationRequest request(String selectedVehicle, int passengers, int luggage) {
        AiRecommendationRequest request = new AiRecommendationRequest();
        request.setPickupLocation("Pickup");
        request.setDestination("Destination");
        request.setDistanceKm(30.0);
        request.setSelectedVehicle(selectedVehicle);
        request.setPassengerCount(passengers);
        request.setLuggageCount(luggage);
        return request;
    }
}
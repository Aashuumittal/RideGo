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
    void plannerUsesBackendCalculatedSuitabilityEtaAndDeparture() {
        AiRecommendationRequest request = request("Sedan", 5, 3);
        request.setDesiredArrivalAt(LocalDateTime.of(2026, 6, 15, 20, 0));

        AiRecommendationResponse response = service.planRide(request);

        assertThat(response.isSuitable()).isFalse();
        assertThat(response.getRecommendedVehicle()).isEqualTo("SUV");
        assertThat(response.getCapacityWarning()).contains("up to 4 passengers and 2 bags");
        assertThat(response.getEtaMinutes()).isEqualTo(40.0);
        assertThat(response.getLatestDepartureAt()).isEqualTo(LocalDateTime.of(2026, 6, 15, 19, 20));
        assertThat(response.getRecommendedDepartureAt()).isEqualTo(LocalDateTime.of(2026, 6, 15, 19, 15));
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

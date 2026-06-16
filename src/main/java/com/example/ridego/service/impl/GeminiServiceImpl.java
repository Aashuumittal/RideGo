package com.example.ridego.service.impl;

import com.example.ridego.dto.AiRecommendationRequest;
import com.example.ridego.dto.AiRecommendationResponse;
import com.example.ridego.service.GeminiService;
import com.example.ridego.util.VehicleCatalog;
import com.example.ridego.util.VehicleCatalog.VehicleSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class GeminiServiceImpl implements GeminiService {

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiServiceImpl(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(GEMINI_BASE_URL)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public AiRecommendationResponse planRide(AiRecommendationRequest request) {
        VehicleSpec selected = VehicleCatalog.spec(request.getSelectedVehicle());
        VehicleSpec recommended = VehicleCatalog.recommendSmallestSuitable(
                request.getPassengerCount(), request.getLuggageCount());
        boolean suitable = VehicleCatalog.isSuitable(selected, request.getPassengerCount(), request.getLuggageCount());

        double etaMinutes = VehicleCatalog.calculateEtaMinutes(request.getDistanceKm(), selected.type());
        double fare = VehicleCatalog.calculateFare(request.getDistanceKm(), selected.type());

        // Pre-compute all three vehicles for comparison
        List<VehicleSpec> allSpecs = VehicleCatalog.all();
        StringBuilder vehicleComparison = new StringBuilder();
        for (VehicleSpec spec : allSpecs) {
            double vEta = VehicleCatalog.calculateEtaMinutes(request.getDistanceKm(), spec.type());
            double vFare = VehicleCatalog.calculateFare(request.getDistanceKm(), spec.type());
            boolean vSuitable = VehicleCatalog.isSuitable(spec, request.getPassengerCount(), request.getLuggageCount());
            vehicleComparison.append(String.format(
                    "%s: seats=%d luggage=%d etaMinutes=%.1f fare=%.2f suitable=%s%n",
                    spec.type(), spec.seatingCapacity(), spec.luggageCapacity(), vEta, vFare, vSuitable));
        }

        LocalDateTime scheduledPickupAt = request.getScheduledPickupAt();
        long roundedEtaMinutes = Math.max(1, Math.round(etaMinutes));
        LocalDateTime estimatedArrivalAt = scheduledPickupAt == null
                ? null : scheduledPickupAt.plusMinutes(roundedEtaMinutes);

        String warning = suitable ? null : capacityWarning(selected, request);
        String fallbackGuidance = buildFallbackGuidance(
                request, allSpecs, selected, recommended, suitable, estimatedArrivalAt);

        String aiGuidance = generateGuidance(
                request, selected, recommended, suitable, warning,
                etaMinutes, fare, scheduledPickupAt, estimatedArrivalAt,
                vehicleComparison.toString());
        String guidance = hasText(aiGuidance) ? aiGuidance : fallbackGuidance;

        return AiRecommendationResponse.builder()
                .recommendedVehicle(recommended == null ? null : recommended.type())
                .selectedVehicle(selected.type())
                .suitable(suitable)
                .capacityWarning(warning)
                .passengerCount(request.getPassengerCount())
                .luggageCount(request.getLuggageCount())
                .seatingCapacity(selected.seatingCapacity())
                .luggageCapacity(selected.luggageCapacity())
                .etaMinutes(etaMinutes)
                .fare(fare)
                .scheduledPickupAt(scheduledPickupAt)
                .estimatedArrivalAt(estimatedArrivalAt)
                .plannerGuidance(guidance)
                .aiGenerated(hasText(aiGuidance))
                .build();
    }

    private String generateGuidance(
            AiRecommendationRequest request,
            VehicleSpec selected,
            VehicleSpec recommended,
            boolean suitable,
            String warning,
            double etaMinutes,
            double fare,
            LocalDateTime scheduledPickupAt,
            LocalDateTime estimatedArrivalAt,
            String vehicleComparison) {
        if (!hasText(apiKey)) {
            return null;
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(
                        request, selected, recommended, suitable, warning, etaMinutes, fare,
                        scheduledPickupAt, estimatedArrivalAt, vehicleComparison))))),
                "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json")
        );

        try {
            JsonNode response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey).build(model))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String text = response == null ? null
                    : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            return parseGuidance(text);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String buildPrompt(
            AiRecommendationRequest request,
            VehicleSpec selected,
            VehicleSpec recommended,
            boolean suitable,
            String warning,
            double etaMinutes,
            double fare,
            LocalDateTime scheduledPickupAt,
            LocalDateTime estimatedArrivalAt,
            String vehicleComparison) {
        return """
                You are the AI Ride Planner for RideGo.
                Write a concise, friendly recommendation for the passenger BEFORE they choose a vehicle.
                Use ONLY the backend-provided values below — do not invent or recalculate anything.

                Structure your response like this (2–4 sentences total):
                1. State the recommended vehicle and why (passenger + luggage fit, ETA, cost).
                2. Mention any other suitable alternatives with their ETA and cost.
                3. If the passenger has already selected a vehicle that differs from the recommended one, note it briefly.
                4. If a pickup time is provided, mention the estimated arrival time.
                Do NOT tell the user when to depart. Do NOT repeat capacity numbers unless relevant to suitability.

                Return ONLY JSON: {"plannerGuidance":"your 2-4 sentence guidance here."}

                Backend-provided facts:
                pickup=%s
                destination=%s
                distanceKm=%.2f
                passengers=%d
                luggage=%d
                selectedVehicle=%s
                recommendedVehicle=%s
                selectedSuitable=%s
                capacityWarning=%s
                scheduledPickupAt=%s
                estimatedArrivalAt=%s

                All vehicle options:
                %s
                """.formatted(
                request.getPickupLocation(), request.getDestination(), request.getDistanceKm(),
                request.getPassengerCount(), request.getLuggageCount(),
                selected.type(),
                recommended == null ? "none" : recommended.type(),
                suitable, valueOrNone(warning),
                valueOrNone(scheduledPickupAt), valueOrNone(estimatedArrivalAt),
                vehicleComparison);
    }

    private String parseGuidance(String text) {
        if (!hasText(text)) {
            return null;
        }
        try {
            JsonNode json = objectMapper.readTree(stripJsonFence(text));
            String guidance = json.path("plannerGuidance").asText("").trim();
            return hasText(guidance) ? guidance : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildFallbackGuidance(
            AiRecommendationRequest request,
            List<VehicleSpec> allSpecs,
            VehicleSpec selected,
            VehicleSpec recommended,
            boolean suitable,
            LocalDateTime estimatedArrivalAt) {

        // Build recommendation sentence
        String recSentence;
        if (recommended == null) {
            recSentence = "No single vehicle can accommodate " + request.getPassengerCount()
                    + " passengers and " + request.getLuggageCount() + " bags.";
        } else {
            double recEta = VehicleCatalog.calculateEtaMinutes(request.getDistanceKm(), recommended.type());
            double recFare = VehicleCatalog.calculateFare(request.getDistanceKm(), recommended.type());
            recSentence = "I recommend the " + recommended.type() + " for " + request.getPassengerCount()
                    + " passenger" + (request.getPassengerCount() == 1 ? "" : "s") + " and "
                    + request.getLuggageCount() + " bag" + (request.getLuggageCount() == 1 ? "" : "s")
                    + " — ETA ~" + Math.round(recEta) + " min, fare ₹" + String.format("%.0f", recFare) + ".";
        }

        // Build alternatives sentence
        StringBuilder alts = new StringBuilder();
        for (VehicleSpec spec : allSpecs) {
            if (recommended != null && spec.type().equals(recommended.type())) continue;
            if (!VehicleCatalog.isSuitable(spec, request.getPassengerCount(), request.getLuggageCount())) continue;
            double aEta = VehicleCatalog.calculateEtaMinutes(request.getDistanceKm(), spec.type());
            double aFare = VehicleCatalog.calculateFare(request.getDistanceKm(), spec.type());
            if (alts.length() > 0) alts.append(" ");
            alts.append(spec.type()).append(" (~").append(Math.round(aEta)).append(" min, ₹")
                    .append(String.format("%.0f", aFare)).append(") is also suitable.");
        }

        // Arrival hint
        String arrival = estimatedArrivalAt == null ? ""
                : " Estimated arrival at " + estimatedArrivalAt.format(DateTimeFormatter.ofPattern("h:mm a")) + ".";

        return recSentence + (alts.length() > 0 ? " " + alts : "") + arrival;
    }

    private String capacityWarning(VehicleSpec selected, AiRecommendationRequest request) {
        return selected.type() + " supports up to " + selected.seatingCapacity() + " passengers and "
                + selected.luggageCapacity() + " bags, but this trip has " + request.getPassengerCount()
                + " passengers and " + request.getLuggageCount() + " bags.";
    }

    private String stripJsonFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private String valueOrNone(Object value) {
        return value == null ? "none" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
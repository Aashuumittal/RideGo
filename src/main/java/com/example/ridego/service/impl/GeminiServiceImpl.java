package com.example.ridego.service.impl;

import com.example.ridego.dto.AiRecommendationRequest;
import com.example.ridego.dto.AiRecommendationResponse;
import com.example.ridego.exception.ExternalServiceException;
import com.example.ridego.service.GeminiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
            @Value("${gemini.model:gemini-1.5-flash}") String model,
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
    public AiRecommendationResponse recommendVehicle(AiRecommendationRequest request) {
        ensureApiKeyConfigured();

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", buildPrompt(request)))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );

        try {
            JsonNode response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                            .queryParam("key", apiKey)
                            .build(model))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String text = response == null
                    ? null
                    : response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null);
            return parseRecommendation(text);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Gemini recommendation failed. Please try again later.");
        }
    }

    private String buildPrompt(AiRecommendationRequest request) {
        return """
                You are RideGo's vehicle recommendation engine. Recommend among Sedan, SUV, and Premium only.
                Return only valid JSON with these exact keys:
                {
                  "bestValueOption": "Sedan",
                  "bestComfortOption": "SUV",
                  "bestPremiumOption": "Premium",
                  "recommendationSummary": "Short passenger-facing summary."
                }

                Route and fare context:
                {
                  "distanceKm": %.2f,
                  "etaMinutes": %.1f,
                  "sedanFare": %.2f,
                  "suvFare": %.2f,
                  "premiumFare": %.2f
                }
                """.formatted(
                request.getDistanceKm(),
                request.getEtaMinutes(),
                request.getSedanFare(),
                request.getSuvFare(),
                request.getPremiumFare()
        );
    }

    private AiRecommendationResponse parseRecommendation(String text) {
        if (!hasText(text)) {
            throw new ExternalServiceException("Gemini did not return a recommendation.");
        }

        try {
            JsonNode json = objectMapper.readTree(stripJsonFence(text));
            String bestValue = readVehicleOption(json, "bestValueOption", "Sedan");
            String bestComfort = readVehicleOption(json, "bestComfortOption", "SUV");
            String bestPremium = readVehicleOption(json, "bestPremiumOption", "Premium");
            String summary = json.path("recommendationSummary").asText("").trim();
            if (!hasText(summary)) {
                summary = "Sedan offers the lowest fare for this route. SUV is recommended for groups or luggage. Premium provides the highest comfort and luxury experience.";
            }
            return new AiRecommendationResponse(bestValue, bestComfort, bestPremium, summary);
        } catch (Exception ex) {
            throw new ExternalServiceException("Gemini returned an unreadable recommendation.");
        }
    }

    private String readVehicleOption(JsonNode json, String field, String fallback) {
        String value = json.path(field).asText(fallback).trim();
        if ("sedan".equalsIgnoreCase(value)) {
            return "Sedan";
        }
        if ("suv".equalsIgnoreCase(value)) {
            return "SUV";
        }
        if ("premium".equalsIgnoreCase(value)) {
            return "Premium";
        }
        return fallback;
    }

    private String stripJsonFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private void ensureApiKeyConfigured() {
        if (!hasText(apiKey)) {
            throw new ExternalServiceException("GEMINI_API_KEY is not configured on the server.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

package com.example.ridego.service.impl;

import com.example.ridego.dto.CoordinateDto;
import com.example.ridego.dto.GeocodeResponse;
import com.example.ridego.dto.RoutePlanRequest;
import com.example.ridego.dto.RoutePlanResponse;
import com.example.ridego.exception.BadRequestException;
import com.example.ridego.exception.ExternalServiceException;
import com.example.ridego.service.RoutePlanningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Map;

@Service
public class OpenRouteServiceRoutePlanningService implements RoutePlanningService {

    private static final String ORS_BASE_URL = "https://api.openrouteservice.org";
    private static final String PROFILE = "driving-car";
    private static final double SEDAN_BASE_FARE = 100.0;
    private static final double SEDAN_PER_KM_FARE = 12.0;
    private static final double SUV_BASE_FARE = 150.0;
    private static final double SUV_PER_KM_FARE = 18.0;
    private static final double PREMIUM_BASE_FARE = 250.0;
    private static final double PREMIUM_PER_KM_FARE = 25.0;

    private final String apiKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenRouteServiceRoutePlanningService(
            @Value("${ors.api-key:}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(ORS_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public GeocodeResponse geocode(String text) {
        ensureApiKeyConfigured();

        String query = text == null ? "" : text.trim();
        if (query.isEmpty()) {
            throw new BadRequestException("Location search text is required");
        }

        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/geocode/search")
                            .queryParam("api_key", apiKey)
                            .queryParam("text", query)
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            return parseGeocodeResponse(response, query);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("OpenRouteService geocoding failed. Please try again later.");
        }
    }

    @Override
    public RoutePlanResponse planRoute(RoutePlanRequest request) {
        ensureApiKeyConfigured();

        GeocodeResponse pickup = resolveLocation(
                request.getPickupLocation(),
                request.getPickupCoordinate(),
                "Pickup location is required");
        GeocodeResponse drop = resolveLocation(
                request.getDropLocation(),
                request.getDropCoordinate(),
                "Destination is required");

        Map<String, Object> body = Map.of(
                "coordinates", List.of(
                        List.of(pickup.getLongitude(), pickup.getLatitude()),
                        List.of(drop.getLongitude(), drop.getLatitude())
                ),
                "instructions", false
        );

        try {
            JsonNode routeGeoJson = restClient.post()
                    .uri("/v2/directions/{profile}/geojson", PROFILE)
                    .header(HttpHeaders.AUTHORIZATION, apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode summary = extractSummary(routeGeoJson);
            double distanceMeters = summary.path("distance").asDouble();
            double durationSeconds = summary.path("duration").asDouble();

            if (distanceMeters <= 0 || durationSeconds <= 0) {
                throw new ExternalServiceException("OpenRouteService did not return a usable route.");
            }

            double distanceKilometers = round(distanceMeters / 1000.0, 2);
            double sedanFare = calculateFare(distanceKilometers, SEDAN_BASE_FARE, SEDAN_PER_KM_FARE);
            double suvFare = calculateFare(distanceKilometers, SUV_BASE_FARE, SUV_PER_KM_FARE);
            double premiumFare = calculateFare(distanceKilometers, PREMIUM_BASE_FARE, PREMIUM_PER_KM_FARE);
            return new RoutePlanResponse(
                    pickup,
                    drop,
                    distanceMeters,
                    distanceKilometers,
                    durationSeconds,
                    round(durationSeconds / 60.0, 1),
                    sedanFare,
                    sedanFare,
                    suvFare,
                    premiumFare,
                    routeGeoJson
            );
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ExternalServiceException("OpenRouteService route calculation failed. Please try again later.");
        }
    }

    private GeocodeResponse resolveLocation(String address, CoordinateDto coordinate, String missingMessage) {
        if (coordinate != null) {
            return new GeocodeResponse(
                    hasText(address) ? address.trim() : "Current location",
                    coordinate.getLatitude(),
                    coordinate.getLongitude()
            );
        }

        if (!hasText(address)) {
            throw new BadRequestException(missingMessage);
        }

        return geocode(address);
    }

    private GeocodeResponse parseGeocodeResponse(JsonNode response, String query) {
        JsonNode features = response == null ? null : response.path("features");
        if (features == null || !features.isArray() || features.isEmpty()) {
            throw new BadRequestException("No valid address found for: " + query);
        }

        JsonNode feature = features.get(0);
        JsonNode coordinates = feature.path("geometry").path("coordinates");
        if (!coordinates.isArray() || coordinates.size() < 2) {
            throw new BadRequestException("No coordinates found for: " + query);
        }

        String label = feature.path("properties").path("label").asText(query);
        double longitude = coordinates.get(0).asDouble();
        double latitude = coordinates.get(1).asDouble();

        return new GeocodeResponse(label, latitude, longitude);
    }

    private JsonNode extractSummary(JsonNode routeGeoJson) {
        JsonNode features = routeGeoJson == null ? objectMapper.createArrayNode() : routeGeoJson.path("features");
        if (!features.isArray() || features.isEmpty()) {
            throw new ExternalServiceException("OpenRouteService did not return route geometry.");
        }

        JsonNode summary = features.get(0).path("properties").path("summary");
        if (summary.isMissingNode()) {
            throw new ExternalServiceException("OpenRouteService did not return route distance or ETA.");
        }
        return summary;
    }

    private void ensureApiKeyConfigured() {
        if (!hasText(apiKey)) {
            throw new ExternalServiceException("ORS_API_KEY is not configured on the server.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private double calculateFare(double distanceKilometers, double baseFare, double perKmFare) {
        return round(baseFare + (distanceKilometers * perKmFare), 2);
    }
}

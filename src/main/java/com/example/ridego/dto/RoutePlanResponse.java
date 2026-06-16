package com.example.ridego.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class RoutePlanResponse {

    private GeocodeResponse pickup;
    private GeocodeResponse drop;
    private double distanceMeters;
    private double distanceKilometers;
    private double durationSeconds;
    private double durationMinutes;
    private double fareEstimate;
    private double sedanFare;
    private double suvFare;
    private double premiumFare;
    private List<VehicleOptionResponse> vehicleOptions;
    private JsonNode routeGeoJson;
}

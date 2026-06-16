package com.example.ridego.util;

import com.example.ridego.exception.BadRequestException;

import java.util.List;
import java.util.Map;

public final class VehicleCatalog {

    public record VehicleSpec(
            String type,
            int seatingCapacity,
            int luggageCapacity,
            double averageSpeedKph,
            double fareMultiplier) {
    }

    private static final double SEDAN_BASE_FARE = 100.0;
    private static final double SEDAN_PER_KM_FARE = 12.0;

    private static final Map<String, VehicleSpec> VEHICLES = Map.of(
            "Sedan", new VehicleSpec("Sedan", 4, 2, 45.0, 1.0),
            "SUV", new VehicleSpec("SUV", 6, 4, 42.0, 1.18),
            "Premium", new VehicleSpec("Premium", 4, 3, 50.0, 1.32)
    );

    private VehicleCatalog() {
    }

    public static List<VehicleSpec> all() {
        return List.of(spec("Sedan"), spec("SUV"), spec("Premium"));
    }

    public static VehicleSpec spec(String vehicleType) {
        VehicleSpec spec = VEHICLES.get(normalize(vehicleType));
        if (spec == null) {
            throw new BadRequestException("Vehicle type must be Sedan, SUV, or Premium");
        }
        return spec;
    }

    public static String normalize(String vehicleType) {
        if (vehicleType == null || vehicleType.trim().isEmpty()) {
            return null;
        }
        return VEHICLES.keySet().stream()
                .filter(type -> type.equalsIgnoreCase(vehicleType.trim()))
                .findFirst()
                .orElse(vehicleType.trim());
    }

    public static boolean isSuitable(VehicleSpec spec, int passengerCount, int luggageCount) {
        return passengerCount <= spec.seatingCapacity() && luggageCount <= spec.luggageCapacity();
    }

    public static VehicleSpec recommendSmallestSuitable(int passengerCount, int luggageCount) {
        return List.of(spec("Sedan"), spec("Premium"), spec("SUV")).stream()
                .filter(vehicle -> isSuitable(vehicle, passengerCount, luggageCount))
                .findFirst()
                .orElse(null);
    }

    public static double calculateFare(double distanceKilometers, String vehicleType) {
        double sedanFare = SEDAN_BASE_FARE + (Math.max(0, distanceKilometers) * SEDAN_PER_KM_FARE);
        return round(sedanFare * spec(vehicleType).fareMultiplier(), 2);
    }

    public static double calculateEtaMinutes(double distanceKilometers, String vehicleType) {
        double minutes = (Math.max(0, distanceKilometers) / spec(vehicleType).averageSpeedKph()) * 60.0;
        return round(Math.max(1.0, minutes), 1);
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}

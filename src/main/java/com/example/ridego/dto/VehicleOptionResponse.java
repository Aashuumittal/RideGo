package com.example.ridego.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VehicleOptionResponse {

    private String type;
    private int seatingCapacity;
    private int luggageCapacity;
    private double averageSpeedKph;
    private double etaMinutes;
    private double fare;
}

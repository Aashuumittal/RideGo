package com.example.ridego.dto;

import jakarta.validation.Valid;
import lombok.Data;

@Data
public class RoutePlanRequest {

    private String pickupLocation;
    private String dropLocation;

    @Valid
    private CoordinateDto pickupCoordinate;

    @Valid
    private CoordinateDto dropCoordinate;
}

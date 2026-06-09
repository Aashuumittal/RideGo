package com.example.ridego.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GeocodeResponse {

    private String label;
    private double latitude;
    private double longitude;
}

package com.example.ridego.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GeocodeRequest {

    @NotBlank(message = "Search text is required")
    private String text;
}

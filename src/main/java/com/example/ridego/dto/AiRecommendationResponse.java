package com.example.ridego.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationResponse {

    private String bestValueOption;
    private String bestComfortOption;
    private String bestPremiumOption;
    private String recommendationSummary;
}

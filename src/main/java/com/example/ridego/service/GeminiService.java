package com.example.ridego.service;

import com.example.ridego.dto.AiRecommendationRequest;
import com.example.ridego.dto.AiRecommendationResponse;

public interface GeminiService {

    AiRecommendationResponse planRide(AiRecommendationRequest request);
}

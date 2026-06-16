package com.example.ridego.controller;

import com.example.ridego.dto.AiRecommendationRequest;
import com.example.ridego.dto.AiRecommendationResponse;
import com.example.ridego.service.GeminiService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final GeminiService geminiService;

    public AiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping("/recommendation")
    public AiRecommendationResponse recommendation(@Valid @RequestBody AiRecommendationRequest request) {
        return geminiService.planRide(request);
    }
}

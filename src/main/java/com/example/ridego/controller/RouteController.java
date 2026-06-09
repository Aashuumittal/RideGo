package com.example.ridego.controller;

import com.example.ridego.dto.GeocodeRequest;
import com.example.ridego.dto.GeocodeResponse;
import com.example.ridego.dto.RoutePlanRequest;
import com.example.ridego.dto.RoutePlanResponse;
import com.example.ridego.service.RoutePlanningService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RoutePlanningService routePlanningService;

    public RouteController(RoutePlanningService routePlanningService) {
        this.routePlanningService = routePlanningService;
    }

    @PostMapping("/geocode")
    public GeocodeResponse geocode(@Valid @RequestBody GeocodeRequest request) {
        return routePlanningService.geocode(request.getText());
    }

    @PostMapping("/plan")
    public RoutePlanResponse planRoute(@Valid @RequestBody RoutePlanRequest request) {
        return routePlanningService.planRoute(request);
    }
}

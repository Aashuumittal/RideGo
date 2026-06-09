package com.example.ridego.service;

import com.example.ridego.dto.GeocodeResponse;
import com.example.ridego.dto.RoutePlanRequest;
import com.example.ridego.dto.RoutePlanResponse;

public interface RoutePlanningService {

    GeocodeResponse geocode(String text);

    RoutePlanResponse planRoute(RoutePlanRequest request);
}

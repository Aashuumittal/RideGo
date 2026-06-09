package com.example.ridego.controller;

import com.example.ridego.dto.DriverStatsResponse;
import com.example.ridego.model.Ride;
import com.example.ridego.service.RideService;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/driver")
public class DriverController {

    private final RideService rideService;

    public DriverController(RideService rideService) {
        this.rideService = rideService;
    }

    private String getCurrentUsername() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping("/rides/requests")
    public List<Ride> getRideRequests() {
        return rideService.getPendingRides(getCurrentUsername());
    }

    @GetMapping("/rides")
    public List<Ride> getDriverRides() {
        return rideService.getDriverRides(getCurrentUsername());
    }

    @GetMapping("/stats")
    public DriverStatsResponse getStats() {
        return rideService.getDriverStats(getCurrentUsername());
    }

    @PostMapping("/rides/{rideId}/accept")
    public Ride acceptRide(@PathVariable String rideId) {
        return rideService.acceptRide(getCurrentUsername(), rideId);
    }
}

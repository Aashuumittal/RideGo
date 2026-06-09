package com.example.ridego.dto;

import com.example.ridego.model.Ride;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DriverStatsResponse {

    private long pending;
    private long accepted;
    private long completed;
    private double totalEarnings;
    private double averageRating;
    private long totalRatings;
    private List<Ride> rideHistory;
}

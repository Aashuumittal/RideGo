package com.example.ridego.service;

import com.example.ridego.dto.CreateRideRequest;
import com.example.ridego.dto.DriverStatsResponse;
import com.example.ridego.dto.PaymentOrderResponse;
import com.example.ridego.dto.PaymentVerificationRequest;
import com.example.ridego.dto.RideRatingRequest;
import com.example.ridego.model.Payment;
import com.example.ridego.model.Ride;

import java.util.List;

public interface RideService {

    Ride createRide(String username, CreateRideRequest request);

    List<Ride> getUserRides(String username);

    List<Ride> getPendingRides(String username);

    List<Ride> getDriverRides(String username);

    DriverStatsResponse getDriverStats(String username);

    Ride acceptRide(String username, String rideId);

    Ride completeRide(String username, String rideId);

    Ride cancelRide(String username, String rideId);

    Ride rateDriver(String username, String rideId, RideRatingRequest request);

    PaymentOrderResponse createPaymentOrder(String username, String rideId);

    Payment verifyPayment(String username, String rideId, PaymentVerificationRequest request);

    void handleRazorpayWebhook(String payload, String signature);
}

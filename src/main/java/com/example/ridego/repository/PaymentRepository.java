package com.example.ridego.repository;

import com.example.ridego.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {

    Optional<Payment> findByRideId(String rideId);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
}

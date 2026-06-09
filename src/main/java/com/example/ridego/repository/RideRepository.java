package com.example.ridego.repository;

import com.example.ridego.model.Ride;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RideRepository extends MongoRepository<Ride, String> {

    List<Ride> findByUserId(String userId);

    List<Ride> findByStatus(String status);

    List<Ride> findByStatusAndVehicleType(String status, String vehicleType);

    List<Ride> findByDriverId(String driverId);

    long countByStatus(String status);

    long countByStatusAndVehicleType(String status, String vehicleType);

    long countByDriverIdAndStatus(String driverId, String status);
}

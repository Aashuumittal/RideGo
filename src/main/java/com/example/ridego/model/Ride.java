package com.example.ridego.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "rides")
public class Ride {

    @Id
    private String id;

    private String userId;
    private String driverId;

    private String pickupLocation;
    private String dropLocation;

    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropLatitude;
    private Double dropLongitude;
    private Double distanceMeters;
    private Double durationSeconds;

    private String vehicleType;
    private Double fare;
    private LocalDateTime scheduledAt;

    private String driverName;
    private String driverPhoneNumber;
    private String driverVehicleType;
    private String driverVehicleName;
    private String driverVehicleNumber;
    private Double driverAverageRating;
    private Long driverRatingsCount;

    private Integer driverRating;
    private String driverFeedback;
    private Date ratedAt;

    private String paymentId;
    private String paymentOrderId;
    private Double paymentAmount;
    private String paymentStatus;
    private Date paymentTimestamp;

    private Date acceptedAt;
    private Date completedAt;
    private Date cancelledAt;

    private String status; // REQUESTED / ACCEPTED / COMPLETED / CANCELLED / PAID
    private Date createdAt;
}

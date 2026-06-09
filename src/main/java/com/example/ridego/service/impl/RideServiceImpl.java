package com.example.ridego.service.impl;

import com.example.ridego.dto.CreateRideRequest;
import com.example.ridego.dto.DriverStatsResponse;
import com.example.ridego.dto.PaymentOrderResponse;
import com.example.ridego.dto.PaymentVerificationRequest;
import com.example.ridego.dto.RideRatingRequest;
import com.example.ridego.exception.BadRequestException;
import com.example.ridego.exception.ExternalServiceException;
import com.example.ridego.exception.NotFoundException;
import com.example.ridego.model.Payment;
import com.example.ridego.model.Ride;
import com.example.ridego.model.User;
import com.example.ridego.repository.PaymentRepository;
import com.example.ridego.repository.RideRepository;
import com.example.ridego.repository.UserRepository;
import com.example.ridego.service.RideService;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RideServiceImpl implements RideService {

    private static final Set<String> VEHICLE_TYPES = Set.of("Sedan", "SUV", "Premium");
    private static final double SEDAN_BASE_FARE = 100.0;
    private static final double SEDAN_PER_KM_FARE = 12.0;
    private static final double SUV_BASE_FARE = 150.0;
    private static final double SUV_PER_KM_FARE = 18.0;
    private static final double PREMIUM_BASE_FARE = 250.0;
    private static final double PREMIUM_PER_KM_FARE = 25.0;
    private static final String INR = "INR";

    private final RideRepository rideRepo;
    private final UserRepository userRepo;
    private final PaymentRepository paymentRepo;
    private final RestClient razorpayClient;
    private final String razorpayKeyId;
    private final String razorpayKeySecret;
    private final String razorpayWebhookSecret;

    public RideServiceImpl(
            RideRepository rideRepo,
            UserRepository userRepo,
            PaymentRepository paymentRepo,
            @Value("${razorpay.key-id:}") String razorpayKeyId,
            @Value("${razorpay.key-secret:}") String razorpayKeySecret,
            @Value("${razorpay.webhook-secret:}") String razorpayWebhookSecret) {
        this.rideRepo = rideRepo;
        this.userRepo = userRepo;
        this.paymentRepo = paymentRepo;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
        this.razorpayWebhookSecret = razorpayWebhookSecret;
        this.razorpayClient = RestClient.builder()
                .baseUrl("https://api.razorpay.com/v1")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private User getUser(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Override
    public Ride createRide(String username, CreateRideRequest request) {
        User user = getUser(username);

        if (!"ROLE_USER".equals(user.getRole())) {
            throw new BadRequestException("Only users can request rides");
        }

        String vehicleType = normalizeVehicleType(request.getVehicleType());
        validateVehicleType(vehicleType);
        ensureDriverAvailable(vehicleType);

        Ride ride = Ride.builder()
                .userId(user.getId())
                .pickupLocation(request.getPickupLocation())
                .dropLocation(request.getDropLocation())
                .pickupLatitude(request.getPickupLatitude())
                .pickupLongitude(request.getPickupLongitude())
                .dropLatitude(request.getDropLatitude())
                .dropLongitude(request.getDropLongitude())
                .distanceMeters(request.getDistanceMeters())
                .durationSeconds(request.getDurationSeconds())
                .vehicleType(vehicleType)
                .fare(calculateFare(request.getDistanceMeters(), vehicleType))
                .scheduledAt(request.getScheduledAt())
                .paymentStatus("UNPAID")
                .status("REQUESTED")
                .createdAt(new Date())
                .build();

        return rideRepo.save(ride);
    }

    @Override
    public List<Ride> getUserRides(String username) {
        User user = getUser(username);
        return rideRepo.findByUserId(user.getId());
    }

    @Override
    public List<Ride> getPendingRides(String username) {
        User driver = getDriver(username);
        String vehicleType = normalizeVehicleType(driver.getVehicleType());
        if (!hasText(vehicleType)) {
            return List.of();
        }
        return rideRepo.findByStatusAndVehicleType("REQUESTED", vehicleType);
    }

    @Override
    public List<Ride> getDriverRides(String username) {
        User driver = getDriver(username);
        return sortNewestFirst(rideRepo.findByDriverId(driver.getId()));
    }

    @Override
    public DriverStatsResponse getDriverStats(String username) {
        User driver = getDriver(username);
        List<Ride> history = getDriverRides(username);
        double earnings = history.stream()
                .filter(ride -> "PAID".equals(ride.getStatus()) || "PAID".equals(ride.getPaymentStatus()))
                .mapToDouble(ride -> ride.getPaymentAmount() != null ? ride.getPaymentAmount() : 0.0)
                .sum();

        return new DriverStatsResponse(
                rideRepo.countByStatusAndVehicleType("REQUESTED", normalizeVehicleType(driver.getVehicleType())),
                rideRepo.countByDriverIdAndStatus(driver.getId(), "ACCEPTED"),
                rideRepo.countByDriverIdAndStatus(driver.getId(), "COMPLETED")
                        + rideRepo.countByDriverIdAndStatus(driver.getId(), "PAID"),
                round(earnings, 2),
                safeDouble(driver.getAverageRating()),
                safeLong(driver.getTotalRatingsCount()),
                history
        );
    }

    @Override
    public Ride acceptRide(String username, String rideId) {
        User driver = getDriver(username);
        Ride ride = getRide(rideId);

        if (!"REQUESTED".equals(ride.getStatus())) {
            throw new BadRequestException("Ride is not available");
        }
        if (!normalizeVehicleType(driver.getVehicleType()).equals(ride.getVehicleType())) {
            throw new BadRequestException("Only " + ride.getVehicleType() + " drivers can accept this ride");
        }

        ride.setDriverId(driver.getId());
        ride.setDriverName(driver.getUsername());
        ride.setDriverPhoneNumber(driver.getPhoneNumber());
        ride.setDriverVehicleType(driver.getVehicleType());
        ride.setDriverVehicleName(driver.getVehicleName());
        ride.setDriverVehicleNumber(driver.getVehicleNumber());
        ride.setDriverAverageRating(safeDouble(driver.getAverageRating()));
        ride.setDriverRatingsCount(safeLong(driver.getTotalRatingsCount()));
        ride.setAcceptedAt(new Date());
        ride.setStatus("ACCEPTED");

        return rideRepo.save(ride);
    }

    @Override
    public Ride completeRide(String username, String rideId) {
        User user = getUser(username);
        Ride ride = getRide(rideId);

        if (!"ACCEPTED".equals(ride.getStatus())) {
            throw new BadRequestException("Ride cannot be completed");
        }

        if (!ride.getDriverId().equals(user.getId()) && !ride.getUserId().equals(user.getId())) {
            throw new BadRequestException("Only the driver or passenger can complete this ride");
        }

        ride.setCompletedAt(new Date());
        ride.setStatus("COMPLETED");

        return rideRepo.save(ride);
    }

    @Override
    public Ride cancelRide(String username, String rideId) {
        User user = getUser(username);
        Ride ride = getRide(rideId);

        if (!ride.getUserId().equals(user.getId())) {
            throw new BadRequestException("Only the passenger can cancel this ride");
        }
        if (!"REQUESTED".equals(ride.getStatus()) && !"ACCEPTED".equals(ride.getStatus())) {
            throw new BadRequestException("Completed rides cannot be cancelled");
        }

        ride.setCancelledAt(new Date());
        ride.setStatus("CANCELLED");
        return rideRepo.save(ride);
    }

    @Override
    public Ride rateDriver(String username, String rideId, RideRatingRequest request) {
        User passenger = getUser(username);
        Ride ride = getRide(rideId);

        if (!ride.getUserId().equals(passenger.getId())) {
            throw new BadRequestException("Only the passenger can rate this ride");
        }
        if (!"COMPLETED".equals(ride.getStatus()) && !"PAID".equals(ride.getStatus())) {
            throw new BadRequestException("Only completed rides can be rated");
        }
        if (ride.getDriverRating() != null) {
            throw new BadRequestException("Driver already rated for this ride");
        }
        if (!hasText(ride.getDriverId())) {
            throw new BadRequestException("Ride has no assigned driver");
        }

        User driver = userRepo.findById(ride.getDriverId())
                .orElseThrow(() -> new NotFoundException("Driver not found"));
        long oldCount = safeLong(driver.getTotalRatingsCount());
        double oldAverage = safeDouble(driver.getAverageRating());
        long newCount = oldCount + 1;
        double newAverage = round(((oldAverage * oldCount) + request.getRating()) / newCount, 2);

        driver.setTotalRatingsCount(newCount);
        driver.setAverageRating(newAverage);
        userRepo.save(driver);

        ride.setDriverRating(request.getRating());
        ride.setDriverFeedback(trimToNull(request.getFeedback()));
        ride.setDriverAverageRating(newAverage);
        ride.setDriverRatingsCount(newCount);
        ride.setRatedAt(new Date());
        return rideRepo.save(ride);
    }

    @Override
    public PaymentOrderResponse createPaymentOrder(String username, String rideId) {
        User passenger = getUser(username);
        Ride ride = getRide(rideId);
        ensurePaymentAllowed(passenger, ride);
        ensureRazorpayConfigured();

        long amountPaise = Math.round(safeDouble(ride.getFare()) * 100);
        if (amountPaise <= 0) {
            throw new BadRequestException("Ride fare is missing");
        }

        try {
            JsonNode response = razorpayClient.post()
                    .uri("/orders")
                    .header(HttpHeaders.AUTHORIZATION, basicAuthHeader())
                    .body(Map.of(
                            "amount", amountPaise,
                            "currency", INR,
                            "receipt", "ride_" + ride.getId(),
                            "payment_capture", 1
                    ))
                    .retrieve()
                    .body(JsonNode.class);

            String orderId = response == null ? null : response.path("id").asText(null);
            if (!hasText(orderId)) {
                throw new ExternalServiceException("Razorpay did not return an order ID.");
            }

            ride.setPaymentOrderId(orderId);
            ride.setPaymentAmount(safeDouble(ride.getFare()));
            ride.setPaymentStatus("ORDER_CREATED");
            rideRepo.save(ride);

            return new PaymentOrderResponse(razorpayKeyId, orderId, safeDouble(ride.getFare()), amountPaise, INR);
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Razorpay order creation failed. Please try again later.");
        }
    }

    @Override
    public Payment verifyPayment(String username, String rideId, PaymentVerificationRequest request) {
        User passenger = getUser(username);
        Ride ride = getRide(rideId);
        ensurePaymentAllowed(passenger, ride);
        ensureRazorpayConfigured();

        if (!request.getRazorpayOrderId().equals(ride.getPaymentOrderId())) {
            throw new BadRequestException("Payment order does not match this ride");
        }
        verifySignature(request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId(), request.getRazorpaySignature(), razorpayKeySecret);

        Date paidAt = new Date();
        Payment payment = Payment.builder()
                .rideId(ride.getId())
                .userId(ride.getUserId())
                .driverId(ride.getDriverId())
                .razorpayPaymentId(request.getRazorpayPaymentId())
                .razorpayOrderId(request.getRazorpayOrderId())
                .amount(safeDouble(ride.getFare()))
                .currency(INR)
                .status("SUCCESS")
                .timestamp(paidAt)
                .build();
        Payment savedPayment = paymentRepo.save(payment);

        ride.setPaymentId(request.getRazorpayPaymentId());
        ride.setPaymentOrderId(request.getRazorpayOrderId());
        ride.setPaymentAmount(safeDouble(ride.getFare()));
        ride.setPaymentStatus("PAID");
        ride.setPaymentTimestamp(paidAt);
        ride.setStatus("PAID");
        rideRepo.save(ride);

        return savedPayment;
    }

    @Override
    public void handleRazorpayWebhook(String payload, String signature) {
        if (!hasText(razorpayWebhookSecret)) {
            throw new ExternalServiceException("RAZORPAY_WEBHOOK_SECRET is not configured on the server.");
        }
        verifySignature(payload, signature, razorpayWebhookSecret);
    }

    private void ensurePaymentAllowed(User passenger, Ride ride) {
        if (!ride.getUserId().equals(passenger.getId())) {
            throw new BadRequestException("Only the passenger can pay for this ride");
        }
        if (!"COMPLETED".equals(ride.getStatus()) && !"ORDER_CREATED".equals(ride.getPaymentStatus())) {
            throw new BadRequestException("Only completed rides can be paid");
        }
        if ("PAID".equals(ride.getStatus()) || "PAID".equals(ride.getPaymentStatus())) {
            throw new BadRequestException("Ride is already paid");
        }
        if (!hasText(ride.getDriverId())) {
            throw new BadRequestException("Ride has no assigned driver");
        }
    }

    private User getDriver(String username) {
        User driver = getUser(username);
        if (!"ROLE_DRIVER".equals(driver.getRole())) {
            throw new BadRequestException("Only drivers can perform this action");
        }
        return driver;
    }

    private Ride getRide(String rideId) {
        return rideRepo.findById(rideId)
                .orElseThrow(() -> new NotFoundException("Ride not found"));
    }

    private void ensureDriverAvailable(String vehicleType) {
        if (!userRepo.existsByRoleAndVehicleType("ROLE_DRIVER", vehicleType)) {
            throw new BadRequestException(vehicleType + " rides are currently unavailable. Please choose another vehicle type or try again later.");
        }
    }

    private double calculateFare(Double distanceMeters, String vehicleType) {
        double baseFare = switch (vehicleType) {
            case "SUV" -> SUV_BASE_FARE;
            case "Premium" -> PREMIUM_BASE_FARE;
            default -> SEDAN_BASE_FARE;
        };
        double perKmFare = switch (vehicleType) {
            case "SUV" -> SUV_PER_KM_FARE;
            case "Premium" -> PREMIUM_PER_KM_FARE;
            default -> SEDAN_PER_KM_FARE;
        };
        if (distanceMeters == null || distanceMeters <= 0) {
            return baseFare;
        }
        double distanceKm = distanceMeters / 1000.0;
        return round(baseFare + (distanceKm * perKmFare), 2);
    }

    private String normalizeVehicleType(String vehicleType) {
        if (!hasText(vehicleType)) {
            return null;
        }
        String value = vehicleType.trim().toLowerCase();
        if ("suv".equals(value)) {
            return "SUV";
        }
        if ("sedan".equals(value)) {
            return "Sedan";
        }
        if ("premium".equals(value)) {
            return "Premium";
        }
        return vehicleType.trim();
    }

    private void validateVehicleType(String vehicleType) {
        if (!VEHICLE_TYPES.contains(vehicleType)) {
            throw new BadRequestException("Vehicle type must be Sedan, SUV, or Premium");
        }
    }

    private List<Ride> sortNewestFirst(List<Ride> rides) {
        return rides.stream()
                .sorted(Comparator.comparing(Ride::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .toList();
    }

    private String basicAuthHeader() {
        String raw = razorpayKeyId + ":" + razorpayKeySecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private void ensureRazorpayConfigured() {
        if (!hasText(razorpayKeyId) || !hasText(razorpayKeySecret)) {
            throw new ExternalServiceException("Razorpay keys are not configured on the server.");
        }
    }

    private void verifySignature(String payload, String signature, String secret) {
        if (!hasText(signature)) {
            throw new BadRequestException("Missing Razorpay signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = bytesToHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            if (!expected.equals(signature)) {
                throw new BadRequestException("Invalid Razorpay signature");
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalServiceException("Could not verify Razorpay signature.");
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

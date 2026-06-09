package com.example.ridego.service.impl;

import com.example.ridego.dto.LoginRequest;
import com.example.ridego.dto.RegisterRequest;
import com.example.ridego.exception.BadRequestException;
import com.example.ridego.model.User;
import com.example.ridego.repository.UserRepository;
import com.example.ridego.service.AuthService;
import com.example.ridego.util.JwtUtil;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Set<String> VEHICLE_TYPES = Set.of("Sedan", "SUV", "Premium");
    private static final Pattern VEHICLE_NUMBER_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$");

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(UserRepository userRepo, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public String register(RegisterRequest request) {
        if (userRepo.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }

        validateRole(request.getRole());
        validateDriverProfile(request);

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .phoneNumber(trimToNull(request.getPhoneNumber()))
                .vehicleType(normalizeVehicleType(request.getVehicleType()))
                .vehicleName(trimToNull(request.getVehicleName()))
                .vehicleNumber(normalizeVehicleNumber(request.getVehicleNumber()))
                .totalRatingsCount(0L)
                .averageRating(0.0)
                .build();

        userRepo.save(user);
        return "User registered successfully";
    }

    @Override
    public String login(LoginRequest request) {
        User user = userRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid username or password");
        }

        return jwtUtil.generateToken(user.getUsername(), user.getRole());
    }

    private void validateRole(String role) {
        if (!"ROLE_USER".equals(role) && !"ROLE_DRIVER".equals(role)) {
            throw new BadRequestException("Invalid role");
        }
    }

    private void validateDriverProfile(RegisterRequest request) {
        if (!"ROLE_DRIVER".equals(request.getRole())) {
            return;
        }

        if (!hasText(request.getPhoneNumber())) {
            throw new BadRequestException("Driver phone number is required");
        }
        if (!VEHICLE_TYPES.contains(normalizeVehicleType(request.getVehicleType()))) {
            throw new BadRequestException("Vehicle type must be Sedan, SUV, or Premium");
        }
        if (!hasText(request.getVehicleName())) {
            throw new BadRequestException("Vehicle model/name is required");
        }
        String vehicleNumber = normalizeVehicleNumber(request.getVehicleNumber());
        if (!hasText(vehicleNumber) || !VEHICLE_NUMBER_PATTERN.matcher(vehicleNumber).matches()) {
            throw new BadRequestException("Vehicle number must match format MP09AB1234");
        }
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

    private String normalizeVehicleNumber(String vehicleNumber) {
        return hasText(vehicleNumber) ? vehicleNumber.replaceAll("\\s+", "").toUpperCase() : null;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

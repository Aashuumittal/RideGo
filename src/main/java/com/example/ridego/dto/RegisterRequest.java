package com.example.ridego.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String role;

    private String phoneNumber;
    private String vehicleType;
    private String vehicleName;
    private String vehicleNumber;
}

package com.example.ridego.service;

import com.example.ridego.dto.LoginRequest;
import com.example.ridego.dto.RegisterRequest;

public interface AuthService {

    String register(RegisterRequest request);

    String login(LoginRequest request);
}
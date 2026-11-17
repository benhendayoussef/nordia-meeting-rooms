package com.nordia.rooms.service;

import java.time.OffsetDateTime;

import com.nordia.rooms.config.BookingPolicy;
import com.nordia.rooms.dtos.request.LoginRequest;
import com.nordia.rooms.dtos.response.LoginResponse;
import com.nordia.rooms.jwt.JwtBlacklistService;
import com.nordia.rooms.util.JwtUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final JwtBlacklistService jwtBlacklistService;
    private final BookingPolicy policy;

    public AuthService(AuthenticationManager authenticationManager, JwtUtils jwtUtils,
                       JwtBlacklistService jwtBlacklistService, BookingPolicy policy) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.jwtBlacklistService = jwtBlacklistService;
        this.policy = policy;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

        String token = jwtUtils.generateAccessJwtToken(authentication.getName());
        OffsetDateTime expiresAt = jwtUtils.extractExpiration(token).atZone(policy.zone()).toOffsetDateTime();
        return new LoginResponse(token, expiresAt);
    }

    public void logout(String token) {
        jwtBlacklistService.blacklistToken(jwtUtils.extractId(token), jwtUtils.extractExpiration(token));
    }
}

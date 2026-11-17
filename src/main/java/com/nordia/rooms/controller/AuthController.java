package com.nordia.rooms.controller;

import com.nordia.rooms.dtos.request.LoginRequest;
import com.nordia.rooms.dtos.response.LoginResponse;
import com.nordia.rooms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a bearer token valid for one hour.")
    @ApiResponse(responseCode = "200", description = "The access token and the moment it expires")
    @ApiResponse(responseCode = "400", description = "Email or password is missing: VALIDATION_ERROR")
    @ApiResponse(responseCode = "401", description = "Wrong email or password: UNAUTHENTICATED")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}

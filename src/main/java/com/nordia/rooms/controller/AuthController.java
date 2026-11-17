package com.nordia.rooms.controller;

import com.nordia.rooms.dtos.request.LoginRequest;
import com.nordia.rooms.dtos.response.LoginResponse;
import com.nordia.rooms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Log out", description = "Revokes the token used for this request.")
    @ApiResponse(responseCode = "204", description = "The token is revoked and can no longer be used")
    @ApiResponse(responseCode = "401", description = "No valid token: UNAUTHENTICATED")
    public void logout(Authentication authentication) {
        authService.logout((String) authentication.getCredentials());
    }
}

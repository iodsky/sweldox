package com.iodsky.sweldox.security.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping("/login")
    @Operation(summary = "Login to get JWT token", description = "Authenticate with email and password to receive a JWT token for accessing protected endpoints")
    @SecurityRequirements() // This endpoint doesn't require authentication
    public ResponseEntity<Map<String, String>> authenticate(@Valid @RequestBody  LoginRequest loginRequest) {
        String token = authenticationService.authenticate(loginRequest);
        return ResponseEntity.ok(Map.of("token", token));
    }

}

package com.djfactory.auth.web;

import com.djfactory.auth.domain.Role;
import com.djfactory.auth.security.JwtService;
import com.djfactory.auth.service.AuthService;
import com.djfactory.auth.web.dto.LoginRequest;
import com.djfactory.auth.web.dto.LoginResponse;
import com.djfactory.auth.web.dto.RegisterRequest;
import com.djfactory.auth.web.dto.UserResponse;
import com.djfactory.auth.web.dto.ValidateResponse;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    @GetMapping("/validate")
    public ValidateResponse validate(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return new ValidateResponse(false, null, Set.<Role>of());
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            JwtService.ParsedToken parsed = jwtService.parse(token);
            return new ValidateResponse(true, parsed.username(), parsed.roles());
        } catch (JwtException | IllegalArgumentException ex) {
            return new ValidateResponse(false, null, Set.<Role>of());
        }
    }
}

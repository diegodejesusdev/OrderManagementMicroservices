package com.djfactory.auth.service;

import com.djfactory.auth.domain.Role;
import com.djfactory.auth.domain.User;
import com.djfactory.auth.repository.UserRepository;
import com.djfactory.auth.security.JwtService;
import com.djfactory.auth.web.dto.LoginResponse;
import com.djfactory.auth.web.dto.RegisterRequest;
import com.djfactory.auth.web.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("username already in use");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateUserException("email already in use");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .roles(EnumSet.of(Role.USER))
                .build();

        User saved = userRepository.save(user);
        return new UserResponse(saved.getId(), saved.getUsername(), saved.getEmail(), saved.getRoles());
    }

    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("invalid credentials");
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRoles());
        return new LoginResponse(token, "Bearer");
    }
}

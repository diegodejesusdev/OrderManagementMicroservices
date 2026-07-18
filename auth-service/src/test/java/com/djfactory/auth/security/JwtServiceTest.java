package com.djfactory.auth.security;

import com.djfactory.auth.domain.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "unit-test-jwt-secret-that-is-at-least-32-bytes-long-for-hmac-sha-256";
    private static final long EXPIRATION_MILLIS = 3_600_000L;

    private final JwtService jwtService = new JwtService(SECRET, EXPIRATION_MILLIS);

    @Test
    void generatedTokenParsesBackToTheSameClaims() {
        Set<Role> roles = EnumSet.of(Role.USER, Role.ADMIN);

        String token = jwtService.generateToken("alice", roles);
        JwtService.ParsedToken parsed = jwtService.parse(token);

        assertThat(parsed.username()).isEqualTo("alice");
        assertThat(parsed.roles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
        assertThat(parsed.expiration()).isAfter(new Date());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken("alice", EnumSet.of(Role.USER));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
    }
}

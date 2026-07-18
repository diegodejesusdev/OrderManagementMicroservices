package com.djfactory.auth.security;

import com.djfactory.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-millis}") long expirationMillis
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String username, Set<Role> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(username)
                .claim("roles", roles.stream().map(Enum::name).collect(Collectors.toList()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        @SuppressWarnings("unchecked")
        List<String> roleNames = claims.get("roles", List.class);
        Set<Role> roles = roleNames == null
                ? Set.of()
                : roleNames.stream().map(Role::valueOf).collect(Collectors.toUnmodifiableSet());

        return new ParsedToken(claims.getSubject(), roles, claims.getExpiration());
    }

    public record ParsedToken(String username, Set<Role> roles, Date expiration) {}
}

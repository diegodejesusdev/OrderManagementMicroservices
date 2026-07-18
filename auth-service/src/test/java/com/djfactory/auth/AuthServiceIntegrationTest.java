package com.djfactory.auth;

import com.djfactory.auth.domain.Role;
import com.djfactory.auth.web.dto.LoginRequest;
import com.djfactory.auth.web.dto.LoginResponse;
import com.djfactory.auth.web.dto.RegisterRequest;
import com.djfactory.auth.web.dto.UserResponse;
import com.djfactory.auth.web.dto.ValidateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthServiceIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8")
            .withDatabaseName("auth_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void registersLogsInAndRejectsWrongPassword() {
        RegisterRequest register = new RegisterRequest("alice", "alice@example.com", "password1234");

        ResponseEntity<UserResponse> registerResponse =
                rest.postForEntity("/auth/register", register, UserResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerResponse.getBody()).isNotNull();
        assertThat(registerResponse.getBody().username()).isEqualTo("alice");
        assertThat(registerResponse.getBody().email()).isEqualTo("alice@example.com");
        assertThat(registerResponse.getBody().roles()).containsExactly(Role.USER);

        ResponseEntity<LoginResponse> loginResponse = rest.postForEntity(
                "/auth/login",
                new LoginRequest("alice", "password1234"),
                LoginResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        assertThat(loginResponse.getBody().token()).isNotBlank();
        assertThat(loginResponse.getBody().tokenType()).isEqualTo("Bearer");

        ResponseEntity<String> wrongPasswordResponse = rest.postForEntity(
                "/auth/login",
                new LoginRequest("alice", "wrong-password"),
                String.class);

        assertThat(wrongPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void validatesIssuedTokenAgainstAuthorizationHeader() {
        rest.postForEntity(
                "/auth/register",
                new RegisterRequest("bob", "bob@example.com", "password1234"),
                UserResponse.class);

        LoginResponse login = rest.postForEntity(
                "/auth/login",
                new LoginRequest("bob", "password1234"),
                LoginResponse.class).getBody();

        assertThat(login).isNotNull();
        String token = login.token();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<ValidateResponse> response = rest.exchange(
                "/auth/validate",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ValidateResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().valid()).isTrue();
        assertThat(response.getBody().username()).isEqualTo("bob");
        assertThat(response.getBody().roles()).containsExactly(Role.USER);
    }
}

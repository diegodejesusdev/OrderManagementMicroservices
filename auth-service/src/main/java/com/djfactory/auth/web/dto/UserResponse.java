package com.djfactory.auth.web.dto;

import com.djfactory.auth.domain.Role;

import java.util.Set;

public record UserResponse(Long id, String username, String email, Set<Role> roles) {}

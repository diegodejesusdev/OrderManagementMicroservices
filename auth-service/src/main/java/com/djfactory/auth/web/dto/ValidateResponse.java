package com.djfactory.auth.web.dto;

import com.djfactory.auth.domain.Role;

import java.util.Set;

public record ValidateResponse(boolean valid, String username, Set<Role> roles) {}

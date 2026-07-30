package com.labequip.equipment.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

/**
 * The Gateway performs JWT validation and forwards the authenticated identity via
 * X-Auth-User / X-Auth-Roles headers. This service re-checks those headers rather than trusting
 * the network path alone, so a request that reaches this service directly (bypassing the Gateway)
 * is rejected instead of silently treated as authenticated.
 */
@Component
public class RoleGuard {

    public void requireAuthenticated(String authUser) {
        if (authUser == null || authUser.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing authentication context - requests must be routed through the API Gateway");
        }
    }

    public void requireRole(String authUser, String rolesHeader, String requiredRole) {
        requireAuthenticated(authUser);
        List<String> roles = rolesHeader == null ? List.of() : Arrays.asList(rolesHeader.split(","));
        if (!roles.contains(requiredRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Requires role: " + requiredRole);
        }
    }
}

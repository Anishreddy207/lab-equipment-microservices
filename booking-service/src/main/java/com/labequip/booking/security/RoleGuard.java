package com.labequip.booking.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

/**
 * The Gateway validates the JWT and forwards the authenticated identity via X-Auth-User /
 * X-Auth-Roles headers. This service re-checks those headers so a request that reaches it
 * directly (bypassing the Gateway) is rejected rather than treated as authenticated.
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires role: " + requiredRole);
        }
    }
}

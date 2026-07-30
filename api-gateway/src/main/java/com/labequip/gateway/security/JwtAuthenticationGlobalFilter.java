package com.labequip.gateway.security;

import com.labequip.gateway.config.SecurityPathsProperties;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces authentication at the Gateway so unauthenticated/invalid requests never reach a
 * downstream microservice. Valid requests are enriched with X-Auth-User / X-Auth-Roles headers
 * so services can apply role-based authorisation without re-parsing the token.
 */
@Component
public class JwtAuthenticationGlobalFilter implements org.springframework.cloud.gateway.filter.GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    private final JwtValidator jwtValidator;
    private final SecurityPathsProperties securityPathsProperties;

    public JwtAuthenticationGlobalFilter(JwtValidator jwtValidator, SecurityPathsProperties securityPathsProperties) {
        this.jwtValidator = jwtValidator;
        this.securityPathsProperties = securityPathsProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (securityPathsProperties.isPublic(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Rejected unauthenticated request to {}", path);
            return reject(exchange, "Missing bearer token");
        }

        String token = authHeader.substring("Bearer ".length());
        Claims claims = jwtValidator.validate(token);
        if (claims == null) {
            log.warn("Rejected request to {} with invalid/expired token", path);
            return reject(exchange, "Invalid or expired token");
        }

        String username = claims.getSubject();
        String roles = claims.get("roles", String.class);

        ServerHttpRequest enriched = request.mutate()
                .header("X-Auth-User", username)
                .header("X-Auth-Roles", roles == null ? "" : roles)
                .build();

        log.info("Authenticated request user={} roles={} path={}", username, roles, path);
        return chain.filter(exchange.mutate().request(enriched).build());
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("X-Auth-Error", message);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

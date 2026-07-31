## A.1 ADR-001: Gateway-Centred Security

### Status

Accepted

### Context

The system exposes two domain microservices (Booking Service, Equipment Service) behind a Spring Cloud Gateway. The assignment requires JWT-based authentication, enforced before requests reach a downstream microservice, plus role-based authorisation with at least two differently protected endpoint categories (read-only for any authenticated user; write/delete restricted to ADMIN).

A decision was needed on **where** authentication is enforced:

1. At the Gateway only, with downstream services trusting a forwarded identity header. 2. At each downstream microservice independently (e.g. Spring Security + JWT decoding in every service). 3. Duplicated in both places.

The domain has two roles only (USER, ADMIN), issued by Booking Service's /api/auth/login and /api/auth/register endpoints (AuthController, JwtTokenProvider), signed with a shared HMAC secret sourced from Config Server (jwt.secret, environment-variable backed, never hardcoded).

### Decision

Authentication is enforced centrally at the API Gateway via JwtAuthenticationGlobalFilter (api-gateway/src/main/java/com/labequip/gateway/security/JwtAuthenticationGlobalFilter.java):

	Every request not matching security.public-paths (/api/auth/**, /actuator/**) must present a valid Bearer JWT, verified via JwtValidator against the shared secret.

	On success, the Gateway enriches the request with X-Auth-User and X-Auth-Roles headers before forwarding it downstream (a form of trusted-header propagation, not re-issuing a token).

	On failure (missing/invalid/expired token), the Gateway returns 401 immediately - the request never reaches Booking Service or Equipment Service.

Each downstream service additionally runs a lightweight RoleGuard (equipment-service/.../security/RoleGuard.java, booking-service/.../security/RoleGuard.java) that re-checks X-Auth-User/X-Auth-Roles before performing the operation:

	EquipmentController: GET requires any authenticated user; POST/PUT/DELETE require ADMIN.

	BookingController: GET/POST/complete/cancel require any authenticated user; DELETE requires ADMIN.

This is defence-in-depth, not duplicated authentication: the services trust the headers as an *identity assertion from a trusted network hop* (the Gateway), not as a token they independently verify. If a request reaches a service without those headers - i.e. it bypassed the Gateway - RoleGuard.requireAuthenticated rejects it with 401, which also satisfies the requirement that "direct browser/client access to microservices should be avoided" by making direct access non-functional for anything but public health checks.

### Alternatives Considered

| Alternative Considered | Why Not Chosen |
| --- | --- |
| Per-service Spring Security + JWT decoding | each service independently validates the JWT and applies @PreAuthorize. Rejected as the primary mechanism: it duplicates JWT parsing logic across every service, means a change to the auth scheme (e.g. rotating to OAuth2) has to be made N times, and does not stop a request that reaches a service directly (bypassing the Gateway) unless each service also re-implements the check - at which point the Gateway's filter is redundant work. Full Spring Security was also heavier than needed for two roles. |
| Trust downstream services completely (no RoleGuard) | simplest, but means any request that reaches a service directly (e.g. hitting localhost:8082 instead of the Gateway) would be treated as authenticated with no role, defeating the "avoid direct access" requirement. Rejected. |
| OAuth2 with an external Authorization Server | (Keycloak / Spring Authorization Server) - more normal for production and would be able to provide token introspection/refresh but is an additional moving part and too much setup/operational expense to use for a two-role, coursework scoped system. JWT issued directly by Booking Service proved itself to be simpler to present end to end in the scope of the assignment and sufficient. |

### Consequences

Positive

- Single point of authentication enforcement - adding a third microservice is a case of:

- Clean separation: Gateway will ask "Is this request authenticated, as whom?"; services will ask "May this identity do this particular operation?".

- Supplies the same protection for both services (read vs. write/delete) with "at least two differently protected endpoint categories" requirement.

Negative / trade-offs

- The same JWT secret needs to be shared between Gateway, Booking Service, Equipment Service (currently shared through a shared application.yml across Config Servers) - distribution/change of the secret key needs a restart of three processes.

- The X-Auth-User/X-Auth-Roles header trust model expects the network segment between Gateway and the set of domain services to not be its own network segment (no mutual TLS or signed headers) - in the scope of this assignment this is acceptable but in a real production deployment this would need to be hardened - e.g. mTLS or signed short-lived hop e-Token.

Feign calls to Equipment Service need to manually forward these headers (FeignHeaderForwardingConfig), as that hop also isn't in the Gateway.

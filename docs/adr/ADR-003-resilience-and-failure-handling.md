# ADR-003: Resilience and Failure Handling for the Sync Booking → Equipment Call

## Status
Accepted

## Context

Booking Service must synchronously confirm equipment is `AVAILABLE` before confirming a booking
(the assignment's required "Service A must synchronously query Service B before completing at
least one operation"). This call crosses a process and network boundary via Eureka + OpenFeign
(`EquipmentClient`), so it can fail in ways a plain in-process method call cannot: Equipment
Service can be down, slow, or network-partitioned. Without protection, a single slow/failing
Equipment Service would make every booking-creation request hang or fail with a raw connection
exception, and repeated attempts would keep retrying against a service that isn't coming back
soon - wasting threads/connections and degrading Booking Service itself.

## Decision

The call is wrapped in `EquipmentAvailabilityService.fetchEquipment()`
(`booking-service/.../service/EquipmentAvailabilityService.java`) with two Resilience4J
annotations, configured per-instance as `equipmentService` in Config Server
(`config-server/.../config/booking-service.yml`):

```yaml
resilience4j:
  circuitbreaker:
    instances:
      equipmentService:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
  retry:
    instances:
      equipmentService:
        max-attempts: 3
        wait-duration: 500ms
feign:
  client:
    config:
      equipment-service:
        connect-timeout: 2000
        read-timeout: 2000
```

- **`@Retry`** absorbs transient blips (e.g. a single dropped connection) with 3 attempts, 500ms
  apart - short enough not to make the caller wait unreasonably long.
- **Feign connect/read timeouts (2s)** bound how long a single attempt can hang, so a slow (not
  down) Equipment Service can't tie up a Booking Service thread indefinitely.
- **`@CircuitBreaker`** tracks the last 10 calls; once at least 5 calls have been made and 50%+
  failed, it opens for 10 seconds, during which calls fail immediately (`notPermittedCalls`,
  verified in testing: see Implementation Evidence) without attempting the network call at all -
  protecting Booking Service from wasting resources on a service known to be down. It then
  half-opens automatically to test recovery with a limited number of trial calls.
- **`fallbackMethod = "equipmentUnavailableFallback"`** runs whenever the retries are exhausted or
  the circuit is open, and throws a `ResponseStatusException(503, ...)` with a clear message -
  turning an opaque connection failure into a well-formed API response the client can act on,
  rather than a hung request or a raw 500.

## Alternatives Considered

1. **No resilience wrapper - let the Feign call fail/hang naturally.** Rejected outright: violates
   the assignment's explicit resilience requirement and would mean any Equipment Service blip
   directly produces hung requests or ugly 500s at the Gateway.
2. **Retry only, no circuit breaker.** Would absorb transient failures but keeps hammering a
   genuinely-down dependency on every subsequent request, each still paying the full retry+timeout
   cost. Rejected: doesn't protect Booking Service's own resources under a sustained outage, which
   is the more realistic multi-minute failure mode a circuit breaker specifically addresses.
3. **Circuit breaker only, no retry.** Rejected: without a retry, a single transient TCP hiccup
   (not indicative of Equipment Service actually being down) would count as a hard failure and
   contribute to tripping the breaker unnecessarily; retry lets genuinely transient failures
   self-heal before they're counted against the breaker's failure budget.
4. **`@TimeLimiter` (async, `CompletableFuture`-based) in addition to circuit breaker/retry.**
   Considered for stricter latency bounding, but rejected in favour of the simpler Feign-level
   connect/read timeouts: `@TimeLimiter` requires the wrapped method to return
   `CompletableFuture`, which would have pushed the synchronous booking-creation flow onto an
   async model for no benefit, since Feign's own timeout already bounds worst-case latency per
   attempt.
5. **Fail the booking silently / return the equipment as unavailable rather than 503 on
   circuit-open.** Rejected: conflating "equipment is in use" (a real business state, `409`) with
   "we couldn't find out" (an infrastructure failure, `503`) would mislead the client into thinking
   the equipment itself was the problem rather than the dependency being unreachable.

## Consequences

**Positive**
- Booking Service degrades gracefully under Equipment Service outages: callers get a fast, clear
  `503` (confirmed in testing at ~1-1.3s, versus a multi-second/indefinite hang without the
  timeout+circuit-breaker combination) instead of a hang or raw exception.
- The circuit breaker prevents pointless repeated network attempts against a service that's known
  to be down for the `wait-duration-in-open-state` window, then automatically probes recovery.
- Distinct HTTP semantics: `409` for "equipment genuinely not available" vs `503` for "couldn't
  verify due to a dependency failure" - a real business state is not confused with an
  infrastructure failure.

**Negative / trade-offs**
- Adds a fixed worst-case failure-path latency of up to roughly `3 attempts × ~2s timeout` before
  the circuit trips open; once open, subsequent calls fail fast. This was judged an acceptable
  trade-off given the assignment's small scale and the value of not treating one bad probe as
  a full open.
- The fallback treats *any* failure the same way (503, "could not verify"), including a 404 from
  Equipment Service for a genuinely non-existent equipment ID - which is also counted as a circuit
  breaker failure. In a production system this would likely be refined with `ignoreExceptions` so
  that legitimate 4xx business responses don't count against the breaker's failure budget; this
  was accepted as a known simplification for the assignment's scope.
- Resilience4J configuration lives in Config Server rather than code, which is consistent with the
  externalised-configuration requirement but means tuning it requires a config refresh/restart
  rather than a code change.

## Implementation Evidence

- `booking-service/src/main/java/com/labequip/booking/service/EquipmentAvailabilityService.java`
- `booking-service/src/main/java/com/labequip/booking/client/EquipmentClient.java`
- `config-server/src/main/resources/config/booking-service.yml` (`resilience4j.*`, `feign.client.config`)
- Resilience evidence transcript: `docs/evidence-resilience.txt` - circuit breaker state
  `CLOSED` → repeated `503`s → `OPEN` (`failureRate: 80%`, `notPermittedCalls: 17`) → automatic
  `HALF_OPEN` transition
- Report evidence: Section 2.3 "Service-to-Service Communication and Resilience"
- Screencast timestamp: **TBD - insert after recording**

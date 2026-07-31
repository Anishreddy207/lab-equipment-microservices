## A.3 ADR-003: Resilience and Failure Handling for the Sync Booking to Equipment Call

### Status

Accepted

### Context

Under "Service A must synchronously query Service B before completing at least one operation", Booking Service must first synchronously confirm the AVAILABLE equipment before confirming first of all Booking (must +/- synchronously confirm) This call is a process boundary and network boundary call through Eureka + OpenFeign (EquipmentClient), and is possible to fail in a way that an ordinary in process method call fails: Equipment Service can be down/slow, or network-partitioned. If without protection, a single slow/failing Equipment Service would cause every request for booking creation to hang or fail with a raw connection exception, with each repeated request trying to connect with a service that won't be returning for a while - thereby wasting threads/connections and brutalizing Booking Service.

### Decision

The call is wrapped in EquipmentAvailabilityService.fetchEquipment() (booking-service/.../service/EquipmentAvailabilityService.java) with two Resilience4J annotations, configured per-instance as equipmentService in Config Server (config-server/.../config/booking-service.yml):

```
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

- Next, `@Retry` accepts transient blips (e.g. one dropped connection) in 3 tries with a 500ms delay between attempts - not long enough for the calling program to wait.

- Feign connect/read timeouts (2s) limit the amount of time a single attempt can hang in case of slow (not down) Equipment Service thus a Booking Service thread can't hang indefinitely.

- However, with at least 5 calls there will be approximately 50%+ circuits that fail, and then @CircuitBreaker will be open for 10 seconds, during this time any circuitry call will fail immediately (notPermittedCalls - verified via testing: see Implementation Evidence) and won't even attempt the network call - as Boosting Service is known to be down. It afterwards half automatic unlocks and tries recovery of a limited number of trial calls. This CLOSED to OPEN to HALF_OPEN lifecycle is an evolution of refinements suggested in the context of circuit-breakers to mitigate state-transition delay and failure detection in microservice architectures [2].

- If retries are exhausted or the circuit is open, fallbackMethod = "equipmentUnavailableFallback" is invoked, and returns a ResponseStatusException(503, ...) with a clear message - making an opaque connection failure into a well-formed API error the client can make sense of, rather than a hung request or a raw 500.

### Alternatives Considered

| Alternative Considered | Why Not Chosen |
| --- | --- |
| No resilience wrapper - let the Feign call fail/hang naturally. | Rejected outright: Violates the explicit resilience requirement of the assignment; would result in hung requests or can't look good 500s at the Gateway right out of the gate. |
| Retry only, no circuit breaker. | Will tolerate transient failures, but keeps pounding a dependency that is truly-down, retries each time on every request with the full retry+timeout cost. Rejected: does not defend Booking Service's own resources against a persistent failure (the more likely multi-minute failure mode addressed by a circuit breaker). |
| Circuit breaker only, no retry. | Rejected: the transient TCP hiccup (not Equipment Service being down) would count as a hard failure and unable to be retried would add to the breaker's failure count unnecessarily. |
| @TimeLimiter (async, CompletableFuture-based) in addition to circuit breaker/retry. | Considered for stricter latency bounding, but rejected in favour of the simpler Feign-level connect/read timeouts: @TimeLimiter requires the wrapped method to return CompletableFuture, which would have pushed the synchronous booking-creation flow onto an async model for no benefit, since Feign's own timeout already bounds worst-case latency per attempt. |
| Fail the booking silently / return the equipment as unavailable rather than 503 on circuit-open | Rejected: "Equipment is in use" (409) and "We couldn't find out" (503) are very similar and could easily cause the client to believe that the equipment was at fault when it actually was a dependency being unavailable. |

### Consequences

Positive

- With Equipment Service outages, Booking Service is fault tolerant – graceful degradation for the caller with a fast, clear 503, confirmed by testing (1-1.3s!) vs. hanging or pure exception without the timeout+circuit breaker combination.

- After a default number of retries in the circuit, the circuit breaker then waits a duration in open state before a service is known to be down, and it automatically probes for a recovery of the service.

- Unique HTTP semantics: 409 means that the equipment is not actually available and 503 means that – because of a dependency failure – a real business state is not available – not an infrastructure failure.

Negative / trade-offs

- Incorporates an additional hard bound worst case latency in the circuit of up to ~3 attempts × ~2s timeout prior to a trip-open, and each subsequent call fails fast afterward. Another acceptable compromise for a small assignment where a 'bad probe' is taken to be only a partial 'open'.

- The fallback will handle any failure the same way (503, "could not verify"), and will include any 404 from Equipment Service for a non-existent equipment ID, which also counts as a circuit breaker failure. In a production system this would probably be customized with ignoreExceptions, such that normal 4xx business answers would not affect the breaker's failure budget, but this was recognized as a known simplified aspect of the assignment limits.

- Resilience4J configuration is stored on Config Server, but not code - consistent with the externalised-configuration requirement, but tuning Rel.4J will require a config refresh/restart, not a code change.

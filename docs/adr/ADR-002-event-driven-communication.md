# ADR-002: Event-Driven Communication for Maintenance Reporting

## Status
Accepted

## Context

When a student completes a booking and reports the equipment as faulty, two things must happen:
Booking Service must record the completion, and Equipment Service must be told to take the
equipment out of service and open a maintenance record. The assignment requires at least one
interaction in the system to use asynchronous messaging rather than a synchronous REST call, with
a justification for why messaging was the better fit here.

The alternative - having `BookingService.complete()` call Equipment Service synchronously (the same
way it already does for the availability check in `EquipmentAvailabilityService`) - was considered
and rejected for this specific interaction.

## Decision

`BookingService.complete()` (`booking-service/.../service/BookingService.java`) publishes a
`MaintenanceRequestedEvent` to RabbitMQ (`MaintenanceEventPublisher`) after marking the booking
`COMPLETED`, instead of calling Equipment Service directly:

- Exchange: `lab-equipment-exchange` (topic), routing key `maintenance.requested`
  (`booking-service/.../messaging/RabbitMQConfig.java`)
- Queue: `maintenance-requested-queue`, bound in Equipment Service
  (`equipment-service/.../messaging/RabbitMQConfig.java`)
- Consumer: `MaintenanceRequestedListener.onMaintenanceRequested()` calls
  `MaintenanceRecordService.handleMaintenanceRequested()`, which sets the equipment's status to
  `UNDER_MAINTENANCE` and creates a new `MaintenanceRecord` - a genuine, meaningful state change,
  not a no-op notification.
- The event class `com.labequip.events.MaintenanceRequestedEvent` is deliberately duplicated
  (same package + class name) in both services rather than shared as a library, so the two
  services remain independently deployable and the event is treated as a payload *contract*, not
  a compile-time dependency.

## Why Asynchronous Rather Than Synchronous, For This Interaction Specifically

This is the interaction where async is *more* appropriate than the sync call used elsewhere in the
system (Booking → Equipment availability check), for reasons specific to this case:

1. **The caller does not need the result.** Confirming a booking genuinely depends on knowing
   whether the equipment is available *right now* - that's a natural synchronous read. Completing
   a booking does not depend on Equipment Service having already processed the maintenance flag;
   the booking is complete regardless of whether Equipment Service is up, slow, or momentarily
   unreachable.
2. **Availability under partial outage.** If Equipment Service is down, a synchronous design would
   force a choice between failing the booking-completion request (losing the user's action) or
   silently dropping the maintenance report. With messaging, RabbitMQ durably holds the event
   until Equipment Service is available again to consume it - the booking completes immediately
   either way, and the maintenance side effect is never lost.
3. **Decoupled failure domains.** A slow or failing Equipment Service should not make booking
   completion slow or failing. The sync call (availability check) *should* propagate Equipment
   Service's health into the booking flow (you cannot confirm a booking you can't verify) - this
   one deliberately should not.
4. **Natural one-to-many extension point.** A "maintenance requested" event is the kind of fact
   other future consumers might care about (e.g. a notification service, an analytics service) -
   a queue/exchange scales to multiple consumers without Booking Service knowing about them; a
   direct REST call would not.

## Alternatives Considered

1. **Synchronous REST call from Booking Service to Equipment Service on completion** (mirroring the
   existing Feign-based availability check). Rejected: couples booking completion's success/latency
   to Equipment Service's availability for a side effect the caller doesn't need to wait on, and
   the assignment specifically asks for one interaction to demonstrate messaging instead.
2. **Kafka instead of RabbitMQ.** Considered, but RabbitMQ was chosen for this single
   point-to-point/topic-routed event: no need for Kafka's log-based replay, partitioning, or
   consumer-group semantics at this scale, and RabbitMQ's lower operational overhead (single
   container, simpler `docker-compose` entry, quicker to demonstrate) was a better fit for a
   two-service, single-event-type system. Empirical message-queue benchmarking supports this:
   RabbitMQ remains competitive with lower operational complexity at moderate throughput, while
   Kafka's advantages concentrate at large-scale, high-throughput, multi-consumer-group workloads
   this system does not have [3].
3. **Spring Cloud Stream abstraction over RabbitMQ** instead of raw `spring-boot-starter-amqp` +
   `RabbitTemplate`/`@RabbitListener`. Rejected for this assignment: Cloud Stream would add a
   binder abstraction layer that isn't needed for a single exchange/queue and would obscure the
   exchange/routing-key/queue wiring the report needs to evidence directly.

## Consequences

**Positive**
- Booking completion is fast and does not depend on Equipment Service's availability.
- Equipment Service processes maintenance events at its own pace, and RabbitMQ durably queues them
  if Equipment Service is temporarily down (queue is declared `durable: true`).
- New consumers of `MaintenanceRequested` can be added without touching Booking Service.

**Negative / trade-offs**
- Eventual consistency: there is a (normally sub-second) window where a booking is `COMPLETED` but
  the equipment hasn't yet flipped to `UNDER_MAINTENANCE`. Acceptable for this domain (nobody is
  blocked waiting on it).
- No compensating transaction/outbox pattern was implemented: if `RabbitTemplate.convertAndSend`
  fails synchronously (e.g. broker unreachable at publish time) after the booking row is already
  committed, the event is lost. For this assignment's scope this was accepted as a known
  simplification rather than implementing a transactional outbox.
- Cross-service type mapping required care: the default `DefaultClassMapper` in
  `Jackson2JsonMessageConverter` resolves `__TypeId__` by fully-qualified class name, so both
  services' event classes must share the same package + class name (documented in both
  `RabbitMQConfig` classes) - a subtlety worth calling out since a mismatch fails silently at
  consume time with a `MessageConversionException`.

## Implementation Evidence

- `booking-service/src/main/java/com/labequip/booking/messaging/MaintenanceEventPublisher.java`
- `booking-service/src/main/java/com/labequip/booking/messaging/RabbitMQConfig.java`
- `equipment-service/src/main/java/com/labequip/equipment/messaging/MaintenanceRequestedListener.java`
- `equipment-service/src/main/java/com/labequip/equipment/messaging/RabbitMQConfig.java`
- `equipment-service/src/main/java/com/labequip/equipment/service/MaintenanceRecordService.java`
  (`handleMaintenanceRequested`)
- RabbitMQ management UI evidence: `docs/screenshots/02-rabbitmq-queue.jpg`,
  `docs/screenshots/03-rabbitmq-exchange.jpg`
- Actual message payload (exchange, routing key, JSON body, traceparent header), captured via
  RabbitMQ's "Get messages" inspector: `docs/screenshots/08-rabbitmq-message-payload.jpg`
- Distributed trace showing the publish/consume hop: `docs/screenshots/05-zipkin-async-trace.jpg`
  (spans: `booking-service: lab-equipment-exchange/maintenance.requested send` →
  `equipment-service: maintenance-requested-queue receive`)
- Report evidence: Section 2.3 "Asynchronous Messaging"
- Screencast timestamp: **12:29–13:36**

## References

[1] V. Velepucha and P. Flores, "A Survey on Microservices Architecture: Principles, Patterns and
Migration Challenges," *IEEE Access*, vol. 11, pp. 88339-88358, 2023,
doi: 10.1109/ACCESS.2023.3305687.

[3] R. Maharjan, M. S. H. Chy, M. A. Arju, and T. Cerny, "Benchmarking Message Queues," *Telecom*,
vol. 4, no. 2, pp. 298-312, 2023, doi: 10.3390/telecom4020018.

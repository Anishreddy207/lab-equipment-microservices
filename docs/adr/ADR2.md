## A.2 ADR-002: Event-Driven Communication for Maintenance Reporting

### Status

Accepted

### Context

If a student completes a booking and notifies Booking Service that the equipment is broken, then two things need to occur; Booking Service should mark the booking as complete and Equipment Service should be notified to remove the equipment from service and open a maintenance record. The assignment must involve at least one interaction with the system that uses an asynchronous message, not the synchronous REST call, with explanation—why was messaging appropriate here?

It was considered to implement BookingService.complete() in a synchronized manner (like in EquipmentAvailabilityService already) - but it has been rejected for this particular interaction.

### Decision

BookingService.complete() (booking-service/.../service/BookingService.java) emits an event of MaintenanceRequestedEvent to the rabbitmq, rather than invoking the Equipment Service:

- Exchange: lab-equipment-exchange (topic), routing key maintenance.requested (booking-service/.../messaging/RabbitMQConfig.java)

- Let's check out the Equipment Service's bound-in queue: maintenance-requested-queue (equipment-service/…./messaging/RabbitMQConfig.java)

- Consumer: MaintenanceRequestedListener.onMaintenanceRequested() calls MaintenanceRecordService.set the equipment's status to UNDER_MAINTENANCE and create a new MaintenanceRecord – a REAL, MEANINGFUL, instead of a no-op!

- The event class className.MaintenanceRequestedEvent is intentionally duplicated (both package + class name) between the two services instead of being shared as a library, which allows the two services to deploy independently and allows the event to be considered a payload contract not a compile time dependency.

### Why Asynchronous Rather Than Synchronous, For This Interaction Specifically

This is the interaction in which async is more suitable than the other sync call (Booking to Equipment availability check) for this specific reasons:

1. **The caller does not need the result.** The true checking of a booking is just a synchronous read to see whether the equipment is available now or not. A booking is done even if Equipment Service is up, slow or just out of reach while the maintenance flag is being processed.

2. **Availability under partial outage.** If Equipment Service is down, a synchronous design would force a choice between failing the booking-completion request (losing the user's action) or silently dropping the maintenance report. With messaging, RabbitMQ durably holds the event until Equipment Service is available again to consume it - the booking completes immediately either way, and the maintenance side effect is never lost.

3. **Decoupled failure domains.** A slow or failing Equipment Service should not make booking completion slow or failing. The sync call (availability check) *should* propagate Equipment Service's health into the booking flow (you cannot confirm a booking you can't verify) - this one deliberately should not.

4. **Natural one-to-many extension point.** A "maintenance requested" event is the kind of fact other future consumers might care about (e.g. a notification service, an analytics service) - a queue/exchange scales to multiple consumers without Booking Service knowing about them; a direct REST call would not.

### Alternatives Considered

| Alternative Considered | Why Not Chosen |
| --- | --- |
| Synchronous REST call from Booking Service to Equipment Service on completion | (mirroring the existing Feign-based availability check). Rejected: couples booking completion's success/latency to Equipment Service's availability for a side effect the caller doesn't need to wait on, and the assignment specifically asks for one interaction to demonstrate messaging instead. |
| Kafka instead of RabbitMQ. | Considered, but RabbitMQ was chosen for this single point-to-point/topic-routed event: no need for Kafka's log-based replay, partitioning, or consumer-group semantics at this scale, and RabbitMQ's lower operational overhead (single container, simpler docker-compose entry, quicker to demonstrate) was a better fit for a two-service, single-event-type system. Empirical message-queue benchmarking supports this: RabbitMQ remains competitive with lower operational complexity at moderate throughput, while Kafka's advantages concentrate at large-scale, high-throughput, multi-consumer-group workloads this system does not have [3]. |
| Spring Cloud Stream abstraction over RabbitMQ | instead of raw spring-boot-starter-amqp + RabbitTemplate/@RabbitListener. Rejected for this assignment: Cloud Stream would add a binder abstraction layer that isn't needed for a single exchange/queue and would obscure the exchange/routing-key/queue wiring the report needs to evidence directly. |

### Consequences

Positive

	Booking completion is fast and does not depend on Equipment Service's availability.

	Equipment Service processes maintenance events at its own pace, and RabbitMQ durably queues them if Equipment Service is temporarily down (queue is declared durable: true).

	New consumers of MaintenanceRequested can be added without touching Booking Service.

Negative / trade-offs

- Eventual consistency: there is a window, normally sub second, of a COMPLETED booking and a not yet UNDER_MAINTENANCE equipment. It is OK for this domain (nobody is blocked waiting for it).

- If the RabbitTemplate. was called, the compensating transaction/outbox pattern was not included. If as part of the ongoing transaction, the booking row has been committed, and convertAndSend fails synchronously for some reason (e.g. broker unreachable at publish time), the event is lost. This was considered a known simplification to get the scope of this assignment down and not as full as implementing a transactional outbox.

- However there were a few things that needed to be mapped cautiously: for cross-service type mapping, the default DefaultClassMapper (used by the Jackson2JsonMessageConverter) matching logic is based on class name (the default "TypeId" of the injected class), which requires both services to use exactly the same package + class name for the event classes (is underlined in both services' RabbitMQConfig classes) - which is a detail worth emphasizing since a mismatch, sadly, does not get discovered till consumption time, where it manifests as a MessageConversionException.

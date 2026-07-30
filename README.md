# Lab Equipment Booking & Maintenance Coordination — Microservices

A university lab equipment booking and maintenance-coordination system, built as a distributed
Spring Boot microservice architecture (Individual Assignment, Microservices Architecture module).

## Domain

- **Booking Service** (Service A) — manages equipment reservations: create/read/update/cancel/delete
  bookings, and marks a booking completed (optionally reporting equipment as faulty).
- **Equipment Service** (Service B) — manages the lab equipment catalog and maintenance records.

**Synchronous link:** Booking Service calls Equipment Service (via OpenFeign + Eureka + Resilience4J)
to verify equipment is `AVAILABLE` before confirming a booking.

**Asynchronous link:** When a booking is completed with a fault reported, Booking Service publishes a
`MaintenanceRequested` event over RabbitMQ. Equipment Service consumes it, flips the equipment to
`UNDER_MAINTENANCE`, and opens a maintenance record — independently of whether Equipment Service was
even online at the moment the booking was completed.

## Architecture

| Component | Port | Notes |
|---|---|---|
| discovery-server (Eureka) | 8761 | http://localhost:8761 |
| config-server | 8888 | native profile, config repo at `config-server/src/main/resources/config` |
| api-gateway | 8080 | sole client entry point |
| booking-service | 8081 | issues JWTs, owns bookings + users |
| equipment-service | 8082 | owns equipment + maintenance records |
| RabbitMQ | 5672 / 15672 (mgmt UI) | `guest`/`guest` |
| Zipkin | 9411 | http://localhost:9411 |
| booking-db (Postgres, prod profile only) | 5433 | |
| equipment-db (Postgres, prod profile only) | 5434 | |

All client traffic must go through the Gateway (`http://localhost:8080`) — the domain services are
not meant to be called directly by a browser/client.

## Prerequisites

- Java 21+ (project targets 21; tested on Temurin 25 — **avoid running Maven under a JDK newer than
  ~25**, since Lombok's annotation processing broke on this machine's Homebrew-installed JDK 26; the
  parent `pom.xml` explicitly configures `annotationProcessorPaths` for Lombok as a result)
- Maven (`mvn`) — or use each module directly with `mvn` since no wrapper is committed
- Docker + Docker Compose (for RabbitMQ, Zipkin, and the prod-profile Postgres databases)

## Running it

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts RabbitMQ, Zipkin, and two Postgres instances (only needed for the `prod` profile).

### 2. Build

```bash
mvn -DskipTests package
```

### 3. Start services, in this order

```bash
java -jar discovery-server/target/discovery-server-1.0.0.jar &
java -jar config-server/target/config-server-1.0.0.jar &

# wait for both to be healthy, then:
SPRING_PROFILES_ACTIVE=dev java -jar equipment-service/target/equipment-service-1.0.0.jar &
SPRING_PROFILES_ACTIVE=dev java -jar booking-service/target/booking-service-1.0.0.jar &
SPRING_PROFILES_ACTIVE=dev java -jar api-gateway/target/api-gateway-1.0.0.jar &
```

`dev` profile uses in-memory H2 (no external DB needed). `prod` profile uses the Postgres containers
from `docker-compose.yml` — export matching credentials first:

```bash
export BOOKING_DB_PASSWORD=booking_pass
export EQUIPMENT_DB_PASSWORD=equipment_pass
export JWT_SECRET=some-long-random-production-secret
SPRING_PROFILES_ACTIVE=prod java -jar equipment-service/target/equipment-service-1.0.0.jar &
SPRING_PROFILES_ACTIVE=prod java -jar booking-service/target/booking-service-1.0.0.jar &
```

### 4. Try it

Demo accounts (seeded on booking-service startup):

| username | password | role |
|---|---|---|
| admin | admin123 | ADMIN |
| student | student123 | USER |

```bash
./smoke-test.sh
```

This script: logs in as both users, shows a 401 with no token, a 403 for a USER attempting an
ADMIN-only write, creates equipment, creates a booking (sync call to Equipment Service), completes it
with a reported fault (publishes the async event), and confirms the equipment flipped to
`UNDER_MAINTENANCE` with a new maintenance record.

To see the Resilience4J fallback: kill `equipment-service` and POST to `/api/bookings` again — you'll
get a `503` in about a second instead of a hung request.

### 5. Observe it

- Eureka dashboard: http://localhost:8761
- RabbitMQ management UI: http://localhost:15672 (guest/guest) — see the `lab-equipment-exchange` and
  `maintenance-requested-queue`
- Zipkin: http://localhost:9411 — search by service (`api-gateway`, `booking-service`,
  `equipment-service`) to see traces propagated across the Gateway → Booking → Equipment call chain
- Logs (`logs/*.log`) include `[service,traceId,spanId]` in every line for correlation

## API summary (all via the Gateway, `http://localhost:8080`)

- `POST /api/auth/register`, `POST /api/auth/login` — public
- `GET/POST/PUT/DELETE /api/equipment` — GET/POST/PUT need auth, DELETE/POST/PUT need ADMIN
- `GET/PUT /api/maintenance-records` — GET needs auth, status update needs ADMIN
- `GET/POST/PUT/DELETE /api/bookings` — GET/POST/PUT need auth, DELETE needs ADMIN

## Configuration

All configuration is externalised via Spring Cloud Config Server (`config-server/src/main/resources/config`).
Two profiles are provided per domain service: `dev` (H2, verbose logging) and `prod` (Postgres,
credentials from environment variables — never hardcoded).

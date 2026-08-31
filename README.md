# Padel Auto-Booker — Backend

Spring Boot 4.1 (Java 21, Gradle) service that talks to the Sporty Plus / tenis.plus API, holds the
booking queue in Postgres (Liquibase migrations), and fires queued bookings at midnight.

## Run locally (without Docker)

Needs a Postgres reachable at `jdbc:postgresql://localhost:5432/padel` (user/pass `padel`), or set
`DB_URL`, `DB_USER`, `DB_PASSWORD`.

```bash
./gradlew bootRun
```

API on `http://localhost:8080`. Normally you run it via the infra `docker compose` instead.

## Key pieces

- `client/SportyApiClient` — dual-host calls (login on `api.sporty.plus`, data on `api.tenis.plus`),
  auto token-refresh on 401, raw create-reservation for the burst, server-clock measurement.
- `auth/AuthService` — token cache + login/refresh from stored credentials.
- `availability/AvailabilityService` — builds the live week grid (working_hours − reservations).
- `booking/BookingScheduler` + `BookingExecutor` — schedule and fire the midnight burst; reloads
  pending bookings on startup so nothing is lost across restarts.
- `api/*` — REST endpoints consumed by the Angular app under `/api`.

## Config

See `src/main/resources/application.yml` (`sporty.*`), overridable via env vars
(`DB_*`, `SPORTY_*`). Booking burst timing lives under `sporty.booking`.

## Tables (Liquibase)

`credentials`, `queued_bookings`, `booking_attempts` — see `src/main/resources/db/changelog`.

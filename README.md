# Event Registration API (Spring Boot)

REST API for events with limited seats and an automatic waitlist. People register for an event, get a seat if one
is free, otherwise they join the waitlist. When someone with a seat cancels, the person who has waited longest is
promoted automatically.

**Stack:** Java 21, Spring Boot 3.3, Spring Web, Spring Data JPA (Hibernate), Spring Security (JWT, method security),
Bean Validation, PostgreSQL 16, Flyway, springdoc OpenAPI, JUnit 5 + Testcontainers, GitHub Actions.

## Features

- Register / log in with stateless JWT authentication (BCrypt password hashes)
- Events: browse upcoming events (paged, with confirmed and waitlisted counts), create events (admin only)
- Registration: get a seat or a waitlist spot, cancel your registration, list your registrations
- Waitlist promotion: cancelling a confirmed seat gives it to the longest-waiting person, in the same transaction
- Admin can list all registrations of an event
- Errors as RFC 7807 problem details; validation errors list the offending fields
- API docs at `/swagger-ui.html`

## Run it

Requirements: JDK 21, Maven, PostgreSQL (or Docker).

```bash
# Option A: Docker
docker compose up -d

# Option B: your own PostgreSQL. Create an empty database "event_registration", then set:
#   DB_URL=jdbc:postgresql://localhost:5432/event_registration  DB_USER=...  DB_PASSWORD=...

mvn spring-boot:run        # Flyway creates the schema on startup
```

Open http://localhost:8080/swagger-ui.html. To make a user an admin (there is no public admin signup on purpose):

```sql
update users set role = 'ADMIN' where email = 'you@example.com';
```

Log in again afterwards so the new role is in your token.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | none | returns a JWT |
| GET | `/api/events?page=&size=` | user | upcoming events, paged |
| GET | `/api/events/{id}` | user | one event with counts |
| POST | `/api/events` | admin | create event |
| POST | `/api/events/{id}/registrations` | user | register: `CONFIRMED` or `WAITLISTED` |
| DELETE | `/api/events/{id}/registrations/me` | user | cancel my registration (may promote someone) |
| GET | `/api/registrations/me` | user | my registrations |
| GET | `/api/events/{id}/registrations` | admin | all registrations of an event |

## Tests

```bash
mvn verify      # needs Docker: Testcontainers starts a real PostgreSQL
```

Covers seats vs waitlist, double registration (409), promotion on cancel (in waiting order), cancelling a waitlist
spot (nobody is promoted), re-registering after cancel, admin-only event creation (403), missing token (401),
pagination, and a **concurrency test**: 20 parallel registrations for an event with 5 seats must give exactly
5 confirmed and 15 waitlisted.

## Design decisions

- **Capacity is protected by a row lock.** `register` and `cancel` first load the event with `SELECT ... FOR UPDATE`
  (`@Lock(PESSIMISTIC_WRITE)`). Without it, two requests could both count 4 of 5 seats and both take the last one.
  With it, requests for the same event run one after another, while different events do not block each other.
  (My other project, a booking API, solves a different problem with a database exclusion constraint instead.)
- **Promotion happens in the same transaction as the cancel**, so a freed seat is never lost or given twice.
- **Waitlist order is the registration id**, so "first come, first served" does not depend on timestamps.
- **A partial unique index** (`event_id, user_id` where status is not `CANCELLED`) is a safety net against double
  registrations. The service checks first and returns a clear 409.
- **N+1 queries avoided:** event counts for a whole page come from one grouped query, and registration lists load their
  event with an `@EntityGraph`.
- **Own page format** (`PagedResponse`) instead of returning Spring's `Page`, so the JSON does not depend on internals.
- **Method security:** admin-only endpoints use `@PreAuthorize`, next to the code they protect.
- **Flyway owns the schema**; Hibernate only validates it (`ddl-auto: validate`).

## Not done yet (honest list)

- Changing an event's capacity does not promote waitlisted people automatically
- No email notification when someone is promoted
- No refresh tokens, no rate limiting on login
- Single instance only: the row lock works across instances, but there is no deployment setup

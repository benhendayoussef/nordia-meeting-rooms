# Nordia Meeting Rooms

A small REST API to book meeting rooms at a fictional company, Nordia. It lists rooms, creates and cancels bookings, and enforces the booking rules (overlaps, opening hours, durations, a daily limit per organiser).

Java 21, Spring Boot 4.1, SQLite through `JdbcClient`, Spring Security with JWT.

## Run it

Requires a JDK 21. The database file `nordia.db` is created in the working directory.

Optional first step: create your settings file from the template.

```
cp .env.example .env
```

`.env` is not committed (it is in `.gitignore`). It holds the JWT secret and the other settings listed under Configuration. It is read by `bootRun`, by the jar when started from the same folder, and by `docker compose`. Without it everything still runs, with a random JWT secret.

```
./gradlew bootRun
```

On Windows use `gradlew.bat bootRun`. The API is then on `http://localhost:8080`.

With Docker:

```
docker build --load -t nordia-meeting-rooms .
docker run -d --name nordia -p 8080:8080 -e NORDIA_JWT_SECRET=<at least 32 characters> -v nordia-data:/app/data nordia-meeting-rooms
```

`--load` is only needed when the builder is `docker-container` (the Docker Desktop default on some setups); otherwise the image stays in the build cache. The `nordia-data` volume keeps the database between runs. Stop with `docker rm -f nordia`.

With Docker Compose (the app plus Redis, used to revoke tokens on logout). It takes `NORDIA_JWT_SECRET` from `.env`:

```
docker compose up -d --build
docker compose down
```

Swagger UI: `http://localhost:8080/swagger-ui.html`. The OpenAPI document is at `/v3/api-docs`.

### Configuration

Set these in `.env` (see `.env.example`) or as real environment variables, which win over `.env`. The `.env` file is never copied into the Docker image.

| Variable | Default | Meaning |
|---|---|---|
| `NORDIA_JWT_SECRET` | random at startup | HMAC secret for the tokens, at least 32 characters. If it is not set a random one is generated and a warning is logged; tokens then stop working after a restart. |
| `NORDIA_DB_PATH` | `nordia.db` | Path of the SQLite file. |
| `NORDIA_SECURITY_TOKEN_BLACKLIST` | `memory` | `memory` or `redis`. Where revoked tokens are remembered. Compose sets `redis`. |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host, only used with `redis`. |

The booking rules (time zone, opening hours, durations, daily limit) and the demo users are in `src/main/resources/application.yml`. The rooms are in `src/main/resources/data/rooms.json` and are loaded at startup; editing the file and restarting updates them.

## Test it

```
./gradlew test
```

No Docker or Redis is needed. The one test class that uses a Redis container (Testcontainers) is skipped when Docker is not available.

## Endpoints

Everything except the rooms listing, the login and Swagger needs `Authorization: Bearer <token>`. The organiser of a booking is the user the token belongs to.

| Method and path | Auth | Purpose |
|---|---|---|
| `GET /api/rooms?minCapacity=N` | no | Rooms with at least N seats, sorted by name |
| `GET /api/rooms/{roomId}/bookings?date=YYYY-MM-DD` | no | Active bookings of a room on a day, sorted by start |
| `POST /api/auth/login` | no | Log in, get a token valid for one hour |
| `POST /api/auth/logout` | yes | Revoke the token used for the request |
| `POST /api/bookings` | yes | Book a room |
| `GET /api/bookings/{id}` | yes | Read a booking |
| `DELETE /api/bookings/{id}` | yes | Cancel a booking |

Demo users, created at startup: `alice@nordia.com` / `alice123`, `bob@nordia.com` / `bob123`, `carol@nordia.com` / `carol123`.

```
# rooms with room for at least 10 people
curl 'http://localhost:8080/api/rooms?minCapacity=10'

# bookings of room 1 on a day
curl 'http://localhost:8080/api/rooms/1/bookings?date=2030-01-15'

# log in (jq extracts the token; without jq, copy accessToken from the response)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@nordia.com","password":"alice123"}' | jq -r .accessToken)

# book room 1 on a future weekday
curl -X POST http://localhost:8080/api/bookings \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"roomId":1,"start":"2030-01-15T09:00:00+01:00","end":"2030-01-15T10:00:00+01:00","subject":"Planning"}'

# read it back
curl http://localhost:8080/api/bookings/1 -H "Authorization: Bearer $TOKEN"

# cancel it (204 also when it is already cancelled)
curl -X DELETE http://localhost:8080/api/bookings/1 -H "Authorization: Bearer $TOKEN"

# log out
curl -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"
```

Date-times are ISO 8601 with an explicit offset and whole minutes. A time without an offset is a 400. Responses give the times in office time with their offset, for example `2030-01-15T09:00:00+01:00`.

## Errors

Every error is a `ProblemDetail` (`application/problem+json`) with `title`, `status`, `detail`, `instance` and a stable `code` to branch on:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Room 1 is already booked from 09:00 to 10:00 on 2030-01-15.",
  "instance": "/api/bookings",
  "code": "BOOKING_OVERLAP"
}
```

`VALIDATION_ERROR` also has an `errors` list of `{field, message}`.

| Status | Code | When |
|---|---|---|
| 400 | `MALFORMED_REQUEST` | Unreadable JSON, unknown property, bad date, time without an offset |
| 400 | `VALIDATION_ERROR` | A field is missing or invalid (for example `minCapacity`, `date`, `subject`) |
| 400 | `INVALID_TIME` | A time is not on a whole minute |
| 400 | `BAD_REQUEST` | Any other request error raised by Spring, such as a missing parameter |
| 401 | `UNAUTHENTICATED` | No token, an invalid, expired or revoked token, or a wrong login |
| 403 | `NOT_ORGANISER` | Cancelling a booking that belongs to someone else |
| 404 | `ROOM_NOT_FOUND` | The room does not exist |
| 404 | `BOOKING_NOT_FOUND` | The booking does not exist |
| 404 | `NOT_FOUND` | Unknown path (with a valid token; without one an unknown `/api` path is a 401) |
| 405 | `METHOD_NOT_ALLOWED` | The method is not supported on that path |
| 409 | `BOOKING_OVERLAP` | The room is already booked at that time |
| 409 | `DAILY_LIMIT_REACHED` | The organiser already has 3 active bookings that day |
| 409 | `BOOKING_ALREADY_STARTED` | Cancelling a booking that has started |
| 422 | `INVALID_TIME_RANGE` | The end is not after the start |
| 422 | `INVALID_DURATION` | Shorter than 15 minutes or longer than 4 hours |
| 422 | `OUTSIDE_OPENING_HOURS` | Weekend, outside 08:00 to 19:00, or not ending the day it starts |
| 422 | `START_IN_PAST` | The start is not after the current time |
| 500 | `INTERNAL_ERROR` | Anything unexpected. The real exception is logged, never returned |

The status policy: 400 for unreadable or invalid input, 403 for not being the organiser, 404 for a missing room or booking, 409 for a conflict with the current state, 422 for a well formed request that breaks a fixed rule.

## Assumptions

- One office, one time zone, configurable, default `Europe/Paris`. All rules run on office time.
- Bookings are Monday to Friday, 08:00 to 19:00, and start and end on the same day. Starting at 08:00 and ending at 19:00 are allowed.
- A booking lasts at least 15 minutes and at most 4 hours, both inclusive.
- Two active bookings of the same room cannot overlap. Touching bookings (one ends at 10:00, the next starts at 10:00) are allowed.
- An organiser has at most 3 active bookings per day. Active means not cancelled, including bookings that have already ended that day. The day is the booking's date in office time.
- Only a booking that has not started can be cancelled. At the exact start minute it has started.
- A start in the past is refused. Only the organiser can cancel. Back-to-back bookings by the same person are allowed.
- Public holidays are not handled.
- Users are the three demo users. There is no registration.

## Design choices

**JDBC over JPA.** The schema is three tables and the interesting queries are explicit: the overlap check and the conditional cancel update. `JdbcClient` with a plain `schema.sql` keeps the SQL visible and avoids a mapping layer that would add nothing here.

**No double booking under concurrency.** The datasource uses `transaction_mode=IMMEDIATE` and a 5 second busy timeout, and `BookingService.create` is one `@Transactional` method. The overlap check, the daily limit check and the insert all run under SQLite's write lock, so two requests for the same slot are serialised and the second one sees the first. A test starts 10 threads on the same slot at the same moment: exactly one succeeds and nine get `BOOKING_OVERLAP`. This relies on SQLite's single writer. On another database it would need an exclusion constraint or serializable transactions.

**UTC storage, office-time rules.** `start_at` and `end_at` are stored as fixed-width UTC text (`2030-01-15T08:00:00Z`), so comparing the text compares the instants, and `booking_date` holds the office-time date. The rules convert to the office zone, so opening hours and the daily limit are correct across daylight saving changes. The clock is always an injected `java.time.Clock`, so tests fix the time.

**Cancel is an idempotent soft delete.** A booking is never removed; it becomes `CANCELLED` with a `cancelled_at`. The cancel is one conditional `UPDATE` (right id, right organiser, still active, not yet started). If no row changed, the booking is read once to say why: 404, 403, 409, or 204 when it was already cancelled. Cancelled bookings are ignored by the overlap and daily limit checks, so the slot can be booked again.

**Stateless JWT with revocation.** Tokens are HS256 with `sub` = email, a random `jti` and a one hour expiry. Logout stores the `jti` until the token would have expired, in memory by default or in Redis. Errors raised in the security filter chain go through the same exception handler, so they have the same problem format as every other error.

## Not done, and next

- No public holidays, recurring bookings, or changing a booking (cancel and book again).
- Rooms can only be changed through `rooms.json`; there is no room management API.
- No list of a user's own bookings, and no pagination.
- No user registration, refresh tokens or rate limiting on login. The demo passwords are in `application.yml`.
- `schema.sql` uses `CREATE TABLE IF NOT EXISTS`, so there are no migrations.
- With the Redis blacklist, if Redis is down the request fails with a plain 500 instead of a problem body.
- No health or metrics endpoint, and no CI.

Next I would add a user's bookings listing, a database migration tool, health and metrics endpoints, login rate limiting, and a GitHub Actions workflow that runs `./gradlew test`.

## AI usage

- **Analysing the brief and making decisions (Claude).** Before writing code, I used Claude to analyse the brief, list edge cases, compare interpretations of the rules and prepare my questions to Hydatis. I chose the questions (Claude helped me word them), and I made the final call on every assumption listed in this README.
- **Code.** All the application code in this repository was written by me. The exception classes come from one of my previous projects, adapted for this API.
- **Tests (Claude Code).** I wrote the test plan (which rules and boundaries to cover) and used Claude Code to write the tests from it. I reviewed each test against the business rules and corrected the ones that didn't match.
- **README (Claude).** The first draft of this README was written with Claude from my code and decisions. I edited it and checked it against the code.

**How I checked the AI output:** every assumption that came out of the analysis is covered by a test; for the main rules, I broke the rule on purpose in the code and confirmed the matching test failed, so the tests really check something; I verified library versions and SQLite behaviour in the official documentation; and I ran every command and example in this README myself.
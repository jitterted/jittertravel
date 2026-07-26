---
name: run-jittertravel
description: Build, run, and drive the JitterTravel app. Use when asked to start JitterTravel, run it locally, run its tests, exercise a slice end-to-end in the real app, or interact with a running instance (plan/change a gathering, book a flight, hit a page).
---

JitterTravel is a Spring Boot 4 event-sourced travel planner with server-rendered pages
(j2html for read-only views, Thymeleaf for forms). Every page sits behind a form-login chain
with CSRF on every POST, so **bare `curl` cannot drive it** — use
`.claude/skills/run-jittertravel/driver.sh`, which keeps a cookie jar and pulls a fresh CSRF
token per form.

All paths below are relative to the repo root.

## Prerequisites

- **JDK 26** (`<java.version>26</java.version>`). Verified with `java version "26" 2026-03-17`.
- **Docker running.** Postgres is started automatically from `compose.yml` by
  spring-boot-docker-compose; the test suite starts its own via Testcontainers.
- No `apt-get`/`brew` installs were needed — Maven wrapper (`./mvnw`) pulls everything else.

## Setup

None. No `.env`, no `application-local.properties`, no manual database creation. The
`prod-preview` profile supplies local stand-in credentials and points at the compose Postgres.

## Run (agent path)

```bash
cd .claude/skills/run-jittertravel
./driver.sh start          # launches app + Postgres, polls /actuator/health until UP (~6s warm)
./driver.sh login          # ted / preview (OWNER). Or: ./driver.sh login family preview
```

Then drive it:

```bash
./driver.sh get /planned-gatherings

./driver.sh post /plan-gathering \
  "gatheringId=$(uuidgen | tr 'A-Z' 'a-z')" "title=LJC Meetup" "venueName=Some Hall" \
  "street=3 Test Rd" "city=Bristol" "region=" "postalCode=BS1 1AA" "country=GB" \
  "locationForMatching=Bristol" "date=2026-11-05" "startTime=19:00" "endTime=22:00" \
  "speaking=true" "infoUrl="
# → status: 302 -> http://localhost:8080/planned-gatherings

./driver.sh post "/planned-gatherings/$GID" "title=Renamed" "city=Bath" "date=2026-11-12" \
  "startTime=18:30" "endTime=21:00"

./driver.sh sql "select type, count(*) from event_log group by 1 order by 1;"
./driver.sh stop
```

| command | what it does |
|---|---|
| `start` | `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod-preview` in the background; waits for `/actuator/health`. No-op if already up. |
| `stop` | Kills whatever holds port 8080 (the forked JVM, not just the Maven parent). |
| `login [user] [pass]` | Form login + CSRF; **verifies** by re-requesting a protected page, so a bad password exits 1 instead of looking like success. |
| `get <path>` | Authenticated GET, prints the body. |
| `post <path> k=v …` | Fetches the page's CSRF token, POSTs url-encoded fields, prints `status: <code> -> <redirect>`. |
| `post-body <path> k=v …` | Same, but prints the response body — use when a validation error re-renders the form instead of redirecting. |
| `sql "<query>"` | `psql` inside the compose Postgres container (user `jitter`, db `jittertravel`). |

Logs → `${TMPDIR}/jittertravel-driver/app.log`. Cookie jar → same directory.

Login accounts (from `application-prod-preview.properties`, local stand-ins, not secrets):
`ted`/`preview` = OWNER (everything), `family`/`preview` = FAMILY (itinerary + calendar only).

## Run (human path)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod-preview   # → http://localhost:8080, Ctrl-C to stop
```

Same thing the driver does. Log in at `/login` with `ted` / `preview`.

## Test

```bash
./mvnw test                # 481 tests, ~2 min, needs Docker
./mvnw test -Pjs-tests     # the Playwright `js` tier, excluded from the default build
```

## Gotchas

- **The CSRF token's attributes are in different orders on different pages.** Spring's
  generated login page emits `name="_csrf" type="hidden" value="…"`; the Thymeleaf forms emit
  `name="_csrf" value="…"`. A grep for `name="_csrf" value="` silently matches nothing on
  `/login`, the token goes out empty, and **every POST then 302s to `/` — which looks like
  success**, not a 403. The driver matches the whole `<input>` tag instead. This one cost a
  full debugging cycle.
- **A failed login also returns 302.** Spring redirects to `/login?error`, so status alone
  can't distinguish it from a successful login redirecting to `/`. `driver.sh login` re-fetches
  a protected page to check.
- **The test Postgres has a fixed container name** (`jittertravel-test-postgres`). A stale one
  from an earlier run makes Testcontainers fail with `Status 409: Conflict … name is already in
  use`, which surfaces as `ApplicationContext failure threshold (1) exceeded` on ~7 tests — the
  real cause is buried ~2500 lines into the Maven log. Fix: `docker rm jittertravel-test-postgres`.
  Consequence: **you cannot run the test suite in two checkouts/worktrees at once.**
- **The compose container name follows the checkout directory**, e.g.
  `jittertravel-gathering-postgres-1` in a worktree named `jittertravel-gathering`. `driver.sh
  sql` matches on the `-postgres-1` suffix to avoid grabbing the test container.
- **Both Postgres instances want port 5432 / their own mapping.** The compose one binds 5432;
  the Testcontainers one gets a random high port. Running the app and the suite together is fine.
- **spring-boot-devtools is on the classpath**, so the app auto-restarts when classes change
  (the main thread is `restartedMain`). Recompiling under a running app restarts it — harmless,
  but it explains "why did my session log out."
- **j2html pages are nearly newline-free.** `grep -c` on rendered HTML counts *lines*, not
  matches, so it under-reports badly. Use `grep -o … | wc -l`.

## Troubleshooting

- **`login FAILED for ted (status 302)`**: wrong password, or the app was restarted by devtools
  mid-session. Re-run `./driver.sh login`.
- **`could not read CSRF token from /login — is the app up?`**: the app isn't listening. Run
  `./driver.sh start` and check `${TMPDIR}/jittertravel-driver/app.log`.
- **`Status 409: Conflict. The container name "/jittertravel-test-postgres" is already in use`**
  during `./mvnw test`: `docker rm jittertravel-test-postgres`, then re-run.
- **`no jittertravel-postgres container running`** from `./driver.sh sql`: the app is stopped, so
  compose tore its Postgres down. `./driver.sh start` first.

# JitterTravel — Deployment (Railway)

Status: **live on Railway** (first verified deploy 2026-07-26) at **<https://jittertravel.com>** —
a custom domain attached to the Railway service. A committed `Dockerfile` and `railway.json` drive
the build; see [Open items](#open-items) for what's still unconfirmed.

> The site is public in the sense that anyone can reach it, but it's a private family app: the
> data-entry and admin pages sit behind form login and only the read-only views are visible without
> one (see [Access control](#access-control-secured-profiles)).

## How deploys happen

**Pushing to `main` deploys.** Railway watches the repo and builds every commit that lands on
`main` — there is no manual promote step and no separate staging environment. Consequences worth
keeping in mind:

- A merged PR is a production deploy the moment it hits `main`.
- Anything you don't want in production stays on a branch until it's ready.
- The health check (`/actuator/health`, `healthcheckTimeout` 120s in `railway.json`) gates the
  rollout, and `restartPolicyType: ON_FAILURE` retries up to 3 times — a container that can't boot
  (e.g. missing `TED_PASSWORD`) fails the rollout rather than replacing a healthy instance.

## What this app is

- Spring Boot **4.0.6**, **Java 26**, packaged as an executable jar (`spring-boot-maven-plugin`).
- Persistence: **PostgreSQL** (Spring `JdbcClient` + Hikari). Schema is created automatically on
  startup (`spring.sql.init.mode=always` runs `src/main/resources/schema.sql`, which is
  idempotent — `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`).
- Event-sourced: rebuilds in-memory projections by replaying `event_log` on every boot.
- Health endpoint for platform health checks: **`/actuator/health`** (also `/actuator/metrics`).

## Secured by default (the important part)

Security has **one chain, always on**. `SecurityConfig` activates the secured form-login chain in
every profile, including local development. There is no permissive/no-auth variant:

| Profile | Auth | CSRF | Datasource | Intended use |
|---|---|---|---|---|
| **default** (no profile) | **form login** (`ted`, `family`) | enabled | `PGDATABASE`, `PGHOST`, `PGPASSWORD`, `PGPORT`, `PGUSER` | **production / Railway** |
| `prod-preview` | **form login** (like production) | enabled | localhost (with `SPRING_DATASOURCE_*` overrides), docker-compose | local dev / run the secured config locally |

`prod-preview` exercises the real production security path — but as an explicit, opt-in profile it
is **never** active on Railway when no profile is set. It supplies local stand-in DB settings and
dummy `TED_PASSWORD`/`FAMILY_PASSWORD` so the secured chain can start on your machine without the
real Railway variables.

> There is no way to run unauthenticated. Forgetting to set a profile yields the **secured**
> configuration (production); local development opts into `prod-preview`, which is the *same*
> secured chain with local stand-in credentials. You cannot accidentally deploy an open instance.

### Access control (secured profiles)
Data-entry and admin pages require login; read-only views stay public:

- **Login required:** `/admin/**`, `/book-flight*`, `/book-hotel*`, `/book-train*`,
  `/plan-conference*`, `/plan-gathering*`, `/clear-conflict*`, the per-flight edit
  `/booked-flights/{id}` (+ `/lookup`), and `/api/parse-address`.
- **Public:** home `/`, `/calendar`, `/itinerary`, the booking lists (`/booked-*`),
  `/planned-gatherings`, `/tentative-conferences`, `/schedule-problems`, `/actuator/**`,
  `/login`, and static assets.

Visiting a protected page while logged out redirects to the login form; a **failed login
returns to the login page with an error** (`/login?error`). The home page hides the "Book & Plan"
and "Admin" nav groups until you log in, and intentionally exposes no "Log in" link.

### Running locally
```
# Local dev = the secured production config (form login, CSRF, redaction):
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod-preview
#   log in with ted / preview  (or family / preview)
```
`prod-preview` uses the secured
form-login chain with dummy local passwords and starts Postgres via `compose.yml`
(spring-boot-docker-compose). A real `TED_PASSWORD`/`FAMILY_PASSWORD` env var overrides the
`prod-preview` defaults if you want to test specific credentials.

## Required environment variables (production)

### Database (from the Railway Postgres service)
These are **not** auto-injected. Railway scopes variables per service, so the database values
live on the Postgres service and the **app service must reference them** in its own variables.
Add these five (ascending order, exactly as Railway lists them) — the password is referenced,
never typed by hand:

```
PGDATABASE="${{Postgres.PGDATABASE}}"
PGHOST="${{Postgres.PGHOST}}"
PGPASSWORD="${{Postgres.PGPASSWORD}}"
PGPORT="${{Postgres.PGPORT}}"
PGUSER="${{Postgres.PGUSER}}"
```

(Railway may have created these references automatically when the DB was provisioned alongside
the service — check the app service's **Variables** tab first; add any that are missing.)

`PORT` is also required but is **injected by Railway automatically** (`server.port=${PORT:8080}`);
you do not set it.

> Do **not** set `SPRING_PROFILES_ACTIVE` in production — the default (unset) is already the
> secured profile with the real Railway DB references. `prod-preview` is for local use only.

### Application secrets (set by hand — private)

| Variable | Required | Secret | Notes |
|---|---|---|---|
| `TED_PASSWORD` | ✅ | **yes** | Login password for the `ted` user. **App fails to start if unset.** |
| `FAMILY_PASSWORD` | ✅ | **yes** | Login password for the `family` user. **App fails to start if unset.** |
| `AERODATABOX_API_KEY` | optional | **yes** | RapidAPI key for AeroDataBox flight lookups. If unset the app still starts but flight lookup is non-functional. |

> **Secrets:** `PGPASSWORD`, `TED_PASSWORD`, `FAMILY_PASSWORD`, `AERODATABOX_API_KEY` are
> private. Set them as Railway service variables; never commit them. There is no `.env` file in
> the repo and none should be added. (The two login passwords double as a fail-fast guard — a
> production boot without them errors immediately rather than coming up misconfigured.)

## Build & deploy: Dockerfile, not Nixpacks

The repo ships a **`Dockerfile`** and **`railway.json`** (builder = `DOCKERFILE`,
health check = `/actuator/health`). We deploy via the Dockerfile **on purpose**:

- **This app targets Java 26.** Build-pack/auto-detect tools like **Railway's Nixpacks lag new
  runtime releases** — they often don't offer a brand-new JDK (or a specific point release) until
  well after it ships. Relying on Nixpacks for a bleeding-edge JDK means the build can silently
  fall back to an older JDK or fail outright on a platform upgrade.
- A pinned base image (`eclipse-temurin:26-jdk` to build, `eclipse-temurin:26-jre` to run) makes
  the runtime **explicit and reproducible**, decoupled from whatever the platform's builder
  currently supports. We control the JDK; the platform just runs the container.

To change the JDK, edit the two `FROM` lines in the `Dockerfile` (and `<java.version>` in
`pom.xml`) together.

Local build sanity check:
```
docker build -t jittertravel . && docker run --rm -p 8080:8080 \
  -e PGHOST=... -e PGPORT=... -e PGDATABASE=... -e PGUSER=... -e PGPASSWORD=... \
  -e TED_PASSWORD=... -e FAMILY_PASSWORD=... jittertravel
```

## Open items

- [x] **`eclipse-temurin:26` tags exist** (jdk + jre) — confirmed by the 2026-07-26 deploy building
      successfully. Re-check when bumping the JDK; keep the two `FROM` lines and `pom.xml` in sync.
- [x] **DB privileges** — confirmed: the app boots, `schema.sql` runs, and the log shows
      `Replayed N events from persistent store`, so the Postgres user has `CREATE`/`ALTER`.
- [x] **Database SSL** — settled: production connects to Postgres over Railway's **private
      network**, so the URL `jdbc:postgresql://${PGHOST}:${PGPORT}/${PGDATABASE}` needs no
      `sslmode`. Only if you ever switch to the public proxy would you append `?sslmode=require`.
- [x] **AeroDataBox** — `AERODATABOX_API_KEY` is set in production; flight lookup is live.
- [ ] **Backups:** export/restore is manual via `/admin/export` & `/admin/import`. Use Railway
      Postgres backups as the durable mechanism.

## Quick start

1. Create a Railway project; add the **PostgreSQL** plugin.
2. Add this repo as a service (Railway uses the committed `Dockerfile` per `railway.json`).
3. Set variables: the five Postgres references (`PGDATABASE`, `PGHOST`, `PGPASSWORD`, `PGPORT`,
   `PGUSER` → `${{Postgres.*}}`), plus `TED_PASSWORD`, `FAMILY_PASSWORD`, and optionally
   `AERODATABOX_API_KEY`. **Leave `SPRING_PROFILES_ACTIVE` unset.**
4. Deploy; the health check (`/actuator/health`, from `railway.json`) gates the rollout.
5. Watch logs for `Replayed N events from persistent store` (DB connect + replay succeeded) and
   confirm `/` redirects to the login form.

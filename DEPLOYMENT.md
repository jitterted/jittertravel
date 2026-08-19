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

- **OWNER only:** `/admin/**`; `/actuator/**` except `/actuator/health`; the data-entry
  forms `/book-flight*`, `/book-hotel*`, `/book-train*`, `/plan-conference*`,
  `/plan-gathering*`, `/plan-private-event*`, `/clear-conflict*` and `/api/parse-address`;
  the lists `/booked-flights`, `/booked-trains`, `/booked-hotels`, `/conferences`,
  `/planned-gatherings` together with their per-item edit/action pages; and
  `/schedule-problems`.
- **FAMILY or OWNER:** `/itinerary` and `/itinerary/**`.
- **Public:** home `/`, `/calendar`, `/login`, `/actuator/health`, the token-gated
  `/calendar/feed/**` (the URL token is the credential, checked in the controller), and
  static assets.

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
- [x] **Backups** — Railway's built-in backups need a **Pro** subscription, which we're not buying,
      so backups are **manual**: run `scripts/backup-db.sh`. See [Backups](#backups) below. The one
      thing still on you is *remembering to run it* — there is no automatic schedule.

## Backups

Railway's automated Postgres backups are a **Pro**-plan feature and we don't subscribe, so backups
are manual and there is **no schedule** — nothing runs unless you run it.

### Taking a backup

```
railway link          # once per machine: pick the JitterTravel project
scripts/backup-db.sh  # → backups/jittertravel-YYYYMMDD-HHMMSSZ.dump
```

What the script does:

1. Reads `DATABASE_PUBLIC_URL` from the Postgres service via the Railway CLI. The private
   `*.railway.internal` host is unreachable from a laptop, so the dump goes over the public TCP
   proxy. (Service name override: `DB_SERVICE=...`; or set `DB_URL=...` yourself and Railway is
   skipped entirely, which also lets you dump a local database.)
2. Asks the live server its version and runs a **matching** `pg_dump` in Docker — nothing to install
   locally, and no "server version mismatch" failure when Railway upgrades Postgres.
3. Writes a compressed custom-format dump (`-Fc`, restorable whole or table-by-table).
4. **Verifies** the dump: `pg_restore --list` must succeed *and* contain data for `command_log` and
   `event_log`. A dump failing either check is deleted rather than left around looking like a
   backup.
5. Prunes to the most recent `KEEP` dumps (default 14): `KEEP=30 scripts/backup-db.sh`.

Prerequisites: Railway CLI (logged in + linked), Docker running, `jq`. `BACKUP_DIR` defaults to
`./backups`, which is **gitignored** — these dumps are real data, so keep them out of the repo and
copy them somewhere off this machine (the whole point of a backup is surviving the laptop).

Good moments to run it: before pushing a schema or event-shape change, before an `/admin/restore`,
and on whatever regular cadence you'll actually keep.

### Restoring

A restore overwrites live family data, so `scripts/restore-db.sh` makes the *safe* thing the default
and the destructive thing deliberate. It runs `pg_restore` in a version-matched Docker container over
the public proxy, exactly like the backup script — nothing to install locally.

```
# Inspect first (SAFE — real database untouched): restores into a throwaway scratch DB
# on the same server, prints command_log / event_log / max_seq counts, then drops it.
scripts/restore-db.sh backups/jittertravel-<stamp>.dump
#   --keep-scratch  keeps the scratch DB (jt_restore_check) so you can psql into it.

# Only once that looks right — restore OVER the real database (DESTROYS current prod data):
scripts/restore-db.sh backups/jittertravel-<stamp>.dump --to=prod
```

`--to=prod` is guarded: it prints the current prod row counts, refuses unless you type
`restore over production`, and **takes a fresh safety dump of the current prod DB first**
(via `backup-db.sh`) so even a bad restore leaves you a dump. It then runs
`pg_restore --clean --if-exists`.

After **any** restore over prod, redeploy (or restart) the app so it replays `event_log` into its
in-memory projections; watch for `Replayed N events from persistent store` and confirm `N` matches
expectations. (The script reminds you of this.)

Prefer a hands-on `pg_restore` instead? The manual equivalent: pull `DATABASE_PUBLIC_URL`
(`railway variables list --service Postgres --kv | grep DATABASE_PUBLIC_URL` — it contains the
password, so don't paste it anywhere shared), `pg_restore -d "<proxy-url-with-/scratch-db-name>"
--no-owner --no-privileges` into a fresh DB to inspect, then re-run over the real database adding
`--clean`.

### Second layer: the app's JSON export

`/admin/backup` downloads `jittertravel-backup.json` — every row of `command_log` **and**
`event_log` — and `/admin/restore` inserts them back **verbatim**. Unlike the old command-replay
export, events are restored byte-for-byte (same event ids, sequences and timestamps); commands are
kept as opaque history and never re-executed. Still **not** a substitute for `pg_dump`:

- After a restore (or a truncate), **restart the app** — the read models are rebuilt by the boot
  replay, not live.
- Its format is a versioned compatibility contract (`version: 2`); the old command-only export
  files are no longer restorable.

Use `pg_dump` as the real backup; use the JSON backup when you want a readable snapshot or to move
data into a scratch instance.

## Quick start

1. Create a Railway project; add the **PostgreSQL** plugin.
2. Add this repo as a service (Railway uses the committed `Dockerfile` per `railway.json`).
3. Set variables: the five Postgres references (`PGDATABASE`, `PGHOST`, `PGPASSWORD`, `PGPORT`,
   `PGUSER` → `${{Postgres.*}}`), plus `TED_PASSWORD`, `FAMILY_PASSWORD`, and optionally
   `AERODATABOX_API_KEY`. **Leave `SPRING_PROFILES_ACTIVE` unset.**
4. Deploy; the health check (`/actuator/health`, from `railway.json`) gates the rollout.
5. Watch logs for `Replayed N events from persistent store` (DB connect + replay succeeded) and
   confirm `/` redirects to the login form.

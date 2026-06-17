# Removing the `local` profile — converge on a single secured chain

## Motivation

The `local` profile activated a **second, permissive security chain** (`devFilterChain`:
`permitAll`, anonymous disabled, CSRF disabled). Having two chains let production and local
behavior diverge — and they did: `CalendarController` decides redaction with
`request.getRemoteUser() == null`, which means "public visitor" under the secured chain but
"no auth configured at all" under `local`. Result: running locally redacted the calendar while
every other page showed full data (the bug that started this).

A divergent trust-from-auth path can reintroduce that class of bug anywhere. We remove it.

## Key discovery: `prod-preview` already is the dev profile

`application-prod-preview.properties` already provides everything `local` did **except** the
no-auth shortcut: local Postgres defaults, `spring.docker.compose.enabled=true`, hikari logging,
**and** stand-in `TED_PASSWORD=preview` / `FAMILY_PASSWORD=preview` so the secured chain starts
on a dev machine. So this is not "build a new dev profile" — it's "**delete `local` and converge
day-to-day dev onto `prod-preview`.**"

(Renaming `prod-preview` → `dev` would read better now that it's *the* local profile, but it's
cosmetic and touches many strings/docs. Left as an optional follow-up; not done here.)

## Why the integration tests don't need auth plumbing

The four `@SpringBootTest` integration tests — `JitterTravelApplicationTests`,
`PostgresPersisterTest`, `CommandExportImportRoundTripTest`, `EventStorePerfTest` — **never touch
the web layer** (no `MockMvc`, no HTTP). They only rode `local` to keep the `userDetailsService`
bean from failing context startup when `TED_PASSWORD`/`FAMILY_PASSWORD` are unset. So they need
those two *properties*, not authenticated *requests*. Every `*WebIntegrationTest` /
`*ControllerTest` is a `@WebMvcTest` slice already running the secured chain with `@WithMockUser`
— unaffected.

## Changes

### Main code
1. **`SecurityConfig`** — delete `devFilterChain`; drop `@Profile("!local")` from
   `securedFilterChain`, `userDetailsService`, and `passwordEncoder` (now unconditional); update
   the class Javadoc (no more `local` opt-in; the secured chain is the only chain).
2. **`GeneralController`** — remove `isLocalProfile` and its arms in `isOwner`/`isFamily`
   (now pure role checks). Keep the "Running Locally" badge, driven by `prod-preview` only.
3. **`CalendarController`** — no change. The redaction bug disappears: under the secured chain a
   local dev logs in as `ted`, so `getRemoteUser()` is non-null and the calendar is not redacted.

### Resources
4. **Delete `application-local.properties`** — its datasource/compose/logging content is already
   duplicated in `application-prod-preview.properties`.

### Tests
5. **`AbstractTestcontainerIntegrationTest`** — remove `@ActiveProfiles("local")` (runs the
   default secured profile); add `TED_PASSWORD`/`FAMILY_PASSWORD` to the existing
   `@TestPropertySource` so the now-unconditional `userDetailsService` starts.
6. **Delete `GeneralControllerLocalProfileTest`** and **`CalendarLocalProfileRedactionTest`** —
   their premise (local = no-auth) no longer exists.
7. Tidy stale `!local` comments in `CalendarRedactionSecurityTest`, `SecurityAuthorizationTest`,
   `AuthorizationMatrixTest`.

### Docs
8. **`DEPLOYMENT.md`** — drop the `local` row/instructions; `prod-preview` is the local dev path.

## Local-run impact

`./mvnw spring-boot:run -Dspring-boot.run.profiles=prod-preview`, then log in as `ted` / `preview`
(or `family` / `preview`). Still one command; the only new step is a login — which is the point:
local now exercises the exact production security path.

# Plan: Cancel Hotel, Replace Hotel, cancel-by deadline, FUTURE/ALL filter

## Context

`/booked-hotels` today is a read-only j2html list with no way to act on a booking — no
cancel, no edit, and it shows every hotel including past stays. We want to:

1. Let the owner **cancel** a booked hotel (with an optional reason) and **replace** one
   (cancel the old + book a new one linked to it).
2. Record a hotel's **cancellation deadline** (`cancelBy`) so the details page can display
   the deadline and whether it has passed. Advisory/informational only — no fees, no behavior
   change, not recorded on the cancellation event.
3. Default `/booked-hotels` to a **FUTURE** view (check-in not yet passed) with an **ALL**
   toggle, mirroring `/booked-trains`.

Hard rules confirmed with the user:
- The hard action gate is **check-in time**: once check-in has passed you can neither
  cancel nor replace. `cancelBy` is purely advisory/informational — no hard block, no fee
  concept anywhere, not recorded on the cancellation event.
- `cancelBy` is an optional field on the normal Book Hotel form (and later Edit/Replace).
- `reason` is a single shared optional field used by both cancel and replace.
- FUTURE/ALL filter is based on **check-in**.

A new **hotel details page rendered in Thymeleaf** hosts the cancel/replace controls (POST
forms → Thymeleaf+CSRF per project rule) and intentionally sets up the upcoming Edit Hotel
slice.

### Key architectural constraints (from CLAUDE.md + memory)
- New application services MUST append events via `CommandExecutor`
  (`src/main/java/dev/ted/jittertravel/application/CommandExecutor.java`) — never `EventStore`
  directly. (`ChangeFlight` uses the legacy direct path; do NOT copy it.)
- Domain commands implement `DomainCommand<C extends DecisionContext>` and emit events from
  `execute(context)` (see `BookHotelCommand` + `BookHotelContext`).
- Optional `String` fields use `""` sentinel normalized in the compact constructor; optional
  non-String fields (`LocalDateTime cancelBy`, `HotelBookingId replacesHotelBookingId`) use
  `null`.
- Every new command type implementing `ImportableCommand` needs a `register(...)` line in
  `ImportableCommandTypes` and a case in `CommandExportImportRoundTripTest`.
- Projections must be covered by book→cancel/replace lifecycle scenario tests (not by sealing
  `Event`).
- Thymeleaf `@Controller` endpoints need `@WebMvcTest`; POSTs need `.with(csrf())` +
  `@WithMockUser`; `@DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")` on every datetime-local field.

---

## Phase 0 — Event logical-name registry (own focused commit, before any new event)

**Problem:** events are persisted in `event_log` with their fully-qualified class name
(`PostgresPersister.StoredEventRow.fromStoredEvent`, line 396: `event.type().getName()`) and
resolved on read via `Class.forName(type)` (line 407). Renaming or moving any `Event` class
breaks replay of every previously stored row. This is the same fragility `ImportableCommandTypes`
fixed for commands. (Backups are unaffected — they store commands and recompute events via
`ImportableCommand.events()`; this is purely about the live `event_log`.)

**Change:** introduce `EventTypes` (in `infrastructure/` or `domain/`, next to where events are
resolved), mirroring `ImportableCommandTypes`:
- `register("HotelBooked", HotelBooked.class)` etc. for **every existing `Event`**; `register`
  seeds the logical name AND the current FQCN as a wire id, so existing `event_log` rows
  (which hold FQCNs) keep resolving — no data migration.
- `logicalNameFor(Class)` for writing, `classFor(String wireId)` for reading, with the same
  alias/append-only-migration discipline as the command registry.
- `StoredEventRow.fromStoredEvent` writes `EventTypes.logicalNameFor(event.type())` into the
  `type` column; `toStoredEvent` resolves via `EventTypes.classFor(type)` instead of
  `Class.forName`.

**Tests:**
- A completeness test that fails if any `Event` implementation is missing from the registry
  (mirror `ImportableCommandTypesTest`); discover `Event` implementors via reflection/classpath
  scan as that test does, or an ArchUnit-style check.
- Round-trip: an event written under a logical name resolves back to the right class.
- Legacy: a `type` column holding an old FQCN still resolves (alias path).

This phase ships independently and unblocks giving the Phase 1/2 events stable identities from
birth.

---

## Phase 1 — `HotelBooked` schema change (highest backward-compat risk)

Add **two optional fields** to `HotelBooked`
(`src/main/java/dev/ted/jittertravel/domain/HotelBooked.java`) in one schema bump:

- `LocalDateTime cancelBy` — null = no/unknown deadline.
- `HotelBookingId replacesHotelBookingId` — null = not a replacement.

Keep the existing `mapsUrl` null→"" normalization; leave the two new fields nullable.

**Backward-compat analysis (verified):** the stack uses Jackson 3 (`tools.jackson`,
`PostgresPersister` line 409 `jsonMapper.readValue(payloadJson, eventClass)`). Old `event_log`
rows and old backup JSON simply lack the new keys → Jackson sets the missing creator
properties to `null`. No mapper config change needed. Risk is low but must be locked down by a
test.

**Touch points for the new `cancelBy` field (it flows through the live book path):**
- `BookHotelCommand` — accept `cancelBy`, validate `cancelBy == null || !cancelBy.isAfter(checkIn)`
  (new exception e.g. `InvalidCancelByDate` in `domain/`). `replacesHotelBookingId` is NOT set
  here (only Replace sets it) — pass `null`.
- `BookHotelHandler` (`application/`) — map `request.getCancelBy()` into the command.
- `BookHotelRequest` (`web/`) — add `cancelBy` field with `@DateTimeFormat(pattern=...)` +
  getter/setter; its `events()` passes through unchanged via the handler.
- `book-hotel.html` template — add optional `cancelBy` datetime-local input.
- `BookHotelControllerValidationTest` / `BookHotelCommandTest` — cover the `cancelBy ≤ checkIn`
  rule and the null/absent case.

**Backward-compat / contract test (lands the standing serialization-contract TODO):**
- Add a golden-JSON deserialization test: a pre-change `HotelBooked` payload (no `cancelBy`,
  no `replacesHotelBookingId`) must deserialize with both fields `null`. Inline the sample as a
  text block (<30 lines) per the inline-samples rule.
- Extend `CommandExportImportRoundTripTest` so a `BookHotel` with a `cancelBy` round-trips, and
  confirm an old backup without it still imports.

**Projectors:** the six `HotelBooked` consumers ignore the new fields automatically; only views
that display the deadline read it (see Phase 2 details view). No projector changes required in
Phase 1.

---

## Phase 2 — Cancel Hotel + FUTURE/ALL filter

### 2a. FUTURE/ALL filter (independent; reuses existing `TimeView`)
- `BookedHotelsProjector.views()` → `views(TimeView filter, LocalDateTime now)`; FUTURE keeps
  bookings whose `checkIn` is not before `now` (mirror `BookedTrainsProjector.views`).
- `BookedHotelsController` — add `@RequestParam(required=false) String filter` →
  `TimeView.fromParam(...)`; pass `now` + active filter to the renderer (mirror
  `BookedTrainsController`).
- `BookedHotelsRenderer.render(views, activeFilter)` — add the FUTURE/ALL `.time-toggle`
  markup + CSS copied from `BookedTrainsRenderer`. Default FUTURE.
- Update `BookedHotelsRendererTest`.

### 2b. Cancel
- **Domain:**
  - `HotelBookingCancelled(HotelBookingId hotelBookingId, String reason)` implements `Event`;
    reason null→"" in compact constructor (mirror `ConferenceCancelled`). Register it in the
    Phase 0 `EventTypes` registry (`register("HotelBookingCancelled", ...)`).
  - `CancelHotelCommand implements DomainCommand<CancelHotelContext>` + `CancelHotelContext`
    (record: `boolean bookingExists`, `LocalDateTime checkIn`, `LocalDateTime now`). Rules:
    throw `HotelBookingNotFound` if absent; throw `CannotCancelAfterCheckIn` if
    `now` is not before `checkIn`; else emit `HotelBookingCancelled`.
- **Application:** `CancelHotel` service using `CommandExecutor.execute(commandId, request,
  context, command)`. Build the context by folding the event stream for existence + checkIn
  (mirror `ChangeFlight.flightExists`, but read-only fold only; the append goes through
  `CommandExecutor`). Guard read-only mode.
- **Projections:** add `HotelBookingCancelled` handling (remove the entry) to all six
  `HotelBooked` consumers — `BookedHotelsProjector`, `TentativeHotelBookingProjector`,
  `TentativeHotelBookingsProjector`, `HotelCalendarProjector`, `ItineraryProjector`,
  `ScheduleGapProjector` — plus the new `HotelDetailsViewProjector` (below). Cover with
  book→cancel lifecycle scenario tests per projector.

### 2c. Hotel details page (Thymeleaf) — host for cancel/replace
- `HotelDetailsView` + `HotelDetailsViewProjector` (`application/`), modeled on
  `FlightDetailsViewProjector`: handle `HotelBooked` (upsert full snapshot incl. `cancelBy`,
  `replacesHotelBookingId`) and `HotelBookingCancelled` (remove). Expose `findById(...)`.
- `BookedHotelDetailsController` (`@Controller`, Thymeleaf):
  - `GET /booked-hotels/{hotelBookingId}` → details template; redirect to `/booked-hotels`
    with a flash message if not found; redirect to `/read-only` in read-only mode.
  - `POST /booked-hotels/{hotelBookingId}/cancel` (optional `reason`) → call `CancelHotel`;
    catch `HotelBookingNotFound` / `CannotCancelAfterCheckIn` → flash + redirect; success →
    redirect `/booked-hotels`.
  - Display the `cancelBy` deadline and a neutral passed/not-passed indicator (no fee wording).
- `booked-hotels.*` rows link to the details page (update `BookedHotelsRenderer.renderRow`).
- New page → add nav per the new-page-navigation rule (ask which nav, but the row link is the
  primary entry).
- **Security:** `SecurityConfig` already has `/booked-flights/*` patterns; add
  `/booked-hotels/*` (and `/booked-hotels/*/**`) to the `hasRole("OWNER")` matchers.
- **Tests:** `@WebMvcTest` for GET render + POST cancel (`@WithMockUser`, `.with(csrf())`);
  new rows in `AuthorizationMatrixTest` for `/booked-hotels/abc` (and cancel/replace if the
  matrix covers POSTs — follow existing convention).

---

## Phase 3 — Replace Hotel

- **Domain:** `ReplaceHotelCommand implements DomainCommand<ReplaceHotelContext>`.
  - `ReplaceHotelContext`: old booking `bookingExists` + old `checkIn`, `now`, and the new-stay
    inputs (or reuse a `BookHotelContext`-style now for the booking validation).
  - Rules: throw `HotelBookingNotFound` if old absent; throw `CannotReplaceAfterCheckIn` if
    `now` not before old `checkIn`; apply the normal book validation to the new stay
    (future check-in, valid date range, `cancelBy ≤ checkIn`).
  - `execute(...)` returns a `Stream.of(...)` of **two existing events**:
    1. `HotelBookingCancelled(oldId, reason)`
    2. `HotelBooked(newId, …, cancelBy, replacesHotelBookingId = oldId)`
  - Both event types already registered in `EventTypes` (Phase 0 + Phase 2). One `commandId`
    → atomic via `CommandExecutor.execute` (it appends the whole stream transactionally; no new
    plumbing).
- **Application:** `ReplaceHotel` service via `CommandExecutor`.
- **Projections:** NO new event handling — the cancel event removes the old booking, the book
  event adds the new one (both already handled after Phase 2). Optionally surface
  "replaced from …" using `replacesHotelBookingId` in the details view. Add a
  book→replace lifecycle scenario test confirming old gone + new present + link set.
- **Web:**
  - `ReplaceHotelRequest implements ImportableCommand` (its `events()` reconstructs both events
    with `IMPORT_BYPASS_NOW`); add `register("ReplaceHotel", ReplaceHotelRequest.class)` to
    `ImportableCommandTypes` + a `CommandExportImportRoundTripTest` case.
  - `POST /booked-hotels/{oldId}/replace` on `BookHotelDetailsController` (or a dedicated
    controller), launched from a book-hotel-style Thymeleaf form on the details page carrying
    `oldId`; same error→flash handling.
  - `AuthorizationMatrixTest` + `SecurityConfig` already covered by the `/booked-hotels/*`
    pattern from Phase 2.

---

## Verification

- **Unit/slice tests:** `./mvnw test` (Maven project) — new `CancelHotelCommandTest`,
  `ReplaceHotelCommandTest`, projector lifecycle tests, `HotelDetailsViewProjectorTest`,
  renderer tests, `@WebMvcTest` controller tests, round-trip + golden-JSON contract tests,
  `AuthorizationMatrixTest`. These are plain JUnit/`@WebMvcTest` (not the `js` group), so the
  default build runs them.
- **JS-behavior tests (only if needed):** if the Thymeleaf details page gains an inline script
  (e.g. a cancel/replace confirm dialog), that behavior goes in the Playwright `js` tier
  (`JsBehaviorTest`, `@Tag("js")`, `./mvnw test -Pjs-tests`) per CLAUDE.md — not a server test.
- **Phase 0:** `EventTypes` completeness test (every `Event` registered); events round-trip
  under logical names; legacy FQCN `type` values still resolve. Boot the app against an existing
  DB to confirm replay of pre-change `event_log` rows still works.
- **Backward-compat:** golden pre-change `HotelBooked` JSON deserializes with new fields null;
  an existing exported backup imports unchanged.
- **End-to-end (manual / run app):**
  1. Book a hotel with and without a `cancelBy`; confirm it appears under FUTURE.
  2. Open its details page from `/booked-hotels`; verify the deadline display.
  3. Cancel with a reason → disappears from `/booked-hotels`, calendar, itinerary, schedule-gap.
  4. Attempt to cancel a past-check-in booking (use ALL view) → blocked with message.
  5. Replace a future booking → old gone, new present and linked; verify atomicity (both or
     neither) by checking the event/command log.
  6. Toggle FUTURE/ALL and confirm filtering is by check-in.

## Open follow-ups (out of scope, noted)
- Edit Hotel slice (the Thymeleaf details page is built to host it).
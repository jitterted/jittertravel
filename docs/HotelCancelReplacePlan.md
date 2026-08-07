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
  cancel nor replace. `cancelBy` is purely advisory/informational — no fee concept anywhere,
  not recorded on the cancellation event. (One exception, added in Phase 1: a `cancelBy`
  *after* check-in is rejected at entry as nonsense — see `InvalidCancelByDate`. That is
  input validation, not a behavioural gate.)
- `cancelBy` is an optional field on the normal Book Hotel form (and later Edit/Replace).
- `reason` is a single shared optional field used by both cancel and replace.
- FUTURE/ALL filter is based on **check-out** (`TemporalView.relevantUntil()` — a stay in
  progress stays under FUTURE). *(Corrected 2026-08-07: the shared toggle convention landed
  on end-of-item, and hotels follow it.)*

Decisions confirmed 2026-08-07 (drive Phases 2–3 below):
- **Cancel hard-removes the booking from every read model.** No tombstone, no "Cancelled"
  row in the ALL view; the event log is the only record. Consequence for Phase 3: the old
  booking a replacement points at is not renderable anywhere, so `replacesHotelBookingId`
  stays a data-only link (see Phase 3).
- **Replace stays in scope** even though Edit Hotel shipped: Edit overwrites history in
  place, Replace preserves the cancelled stay and its reason as separate events.
- **Cancel is a form on the existing edit page**, not a new details page.
- **`cancelBy` gets a column in `/booked-hotels`** in addition to the deadline display on
  the edit page.

The **hotel details page is the Edit Hotel page that already shipped** (`GET /booked-hotels/{id}`
→ `change-hotel.html`, `ChangeHotelController`). It hosts the cancel/replace controls (POST
forms → Thymeleaf+CSRF per project rule). *(Rewritten 2026-08-07: this plan predated the Edit
Hotel slice and described building the page from scratch.)*

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

## Phase 0 — Event logical-name registry (own focused commit, before any new event) — DONE

Shipped as `infrastructure/EventTypes.java`.

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

## Phase 1 — `HotelBooked` schema change (highest backward-compat risk) — DONE (uncommitted)

*Implemented in the working tree and code-reviewed 2026-08-07: no correctness defects; the
review's three nits are in "Open follow-ups" at the end of this doc.*

**Refreshed 2026-08-07, three deviations from the plan as first written:**

1. `cancelBy` is a **`ZonedTimestamp` in the hotel's zone**, not a `LocalDateTime`. The UTC
   migration landed after this plan was written and made `checkIn`/`checkOut` `ZonedTimestamp`;
   a bare wall-clock would be the only naked `LocalDateTime` left in an event and could only be
   compared against server-local time when the details page asks "has the deadline passed?".
2. **Edit Hotel gets the field in the same change.** `ChangeHotelCommand`/`HotelChanged` shipped
   after this plan was written, and `HotelChanged` is a full snapshot — an edit form ignorant of
   `cancelBy` would silently erase the deadline on every edit. `HotelDetailsView` therefore
   carries it so the form round-trips it.
3. `replacesHotelBookingId` is **deferred to Phase 3**. Nothing sets or reads it until Replace
   exists, and Jackson treats added fields as additive, so a second schema bump costs nothing.

Added one optional field to both `HotelBooked` and `HotelChanged`:

- `ZonedTimestamp cancelBy` — null = no/unknown deadline.

Kept the existing `mapsUrl` null→"" normalization; the new field stays nullable.

**Backward-compat analysis (verified):** the stack uses Jackson 3 (`tools.jackson`,
`PostgresPersister` line 409 `jsonMapper.readValue(payloadJson, eventClass)`). Old `event_log`
rows and old backup JSON simply lack the new keys → Jackson sets the missing creator
properties to `null`. No mapper config change needed. The event/command mapper is built in one
place — `EventJsonMapperFactory.create()` (`infrastructure/`), exposed as the `@Bean JsonMapper`
in `EventSourcingConfig`; any contract test must build its mapper from that factory so it matches
what we persist. Risk is low but must be locked down by a test.

**Touch points for the new `cancelBy` field (it flows through the live book and edit paths):**
- `BookHotelCommand` / `ChangeHotelCommand` — accept `cancelBy`, validate
  `cancelBy == null || !cancelBy.utc().isAfter(checkIn.utc())`, else throw `InvalidCancelByDate`
  (new, in `domain/`). Equal to check-in is accepted — cancelling right up to arrival is a real
  hotel policy.
- `BookHotelHandler` / `ChangeHotelHandler` (`application/`) — read the wall-clock in the hotel's
  already-resolved zone; a null request field stays a null `ZonedTimestamp`.
- `BookHotelRequest` / `ChangeHotelRequest` (`web/`) — `LocalDateTime cancelBy` with
  `@DateTimeFormat(pattern=...)` + getter/setter; `events()` passes through via the handler.
- `HotelDetailsView` + `HotelDetailsViewProjector` — carry `cancelBy` back to the edit form.
- `BookHotelController` / `ChangeHotelController` — catch `InvalidCancelByDate` →
  `rejectValue("cancelBy", ...)`.
- `book-hotel.html` and `change-hotel.html` — optional `cancelBy` datetime-local input.

**Tests written:** `BookHotelCommandTest` / `ChangeHotelCommandTest` (carried onto the event,
absent stays null, equal-to-check-in accepted, after-check-in rejected, edit-clears-on-omit);
`BookHotelHandlerTest` (the deadline is read in the hotel's zone, not the server's);
`HotelDetailsViewProjectorTest`; `BookHotelControllerValidationTest`;
`GoldenEventDeserializationTest` (pre-change payloads read back null; a payload carrying the
deadline keeps its zone); `CommandExportImportRoundTripTest` (a booking with a deadline
round-trips; a backup with no `cancelBy` key still imports).

**Backward-compat / contract tests (reuse existing infra; see
`docs/Event_Serialization_Contract_Tests.md`):**
- **Read side (required):** extend the existing `GoldenEventDeserializationTest` with a
  `HotelBooked` case — a pre-change payload (no `cancelBy`, no `replacesHotelBookingId`) must
  deserialize with both fields `null`. (That test uses a stricter `FAIL_ON_UNKNOWN_PROPERTIES`
  mapper; missing creator props still resolve to `null`, so adding fields stays compatible.)
  Inline the sample as a text block (<30 lines) per the inline-samples rule.
- **Round-trip (required):** extend `CommandExportImportRoundTripTest` so a `BookHotel` with a
  `cancelBy` round-trips, and confirm an old backup without it still imports.
- **Write side (OPTIONAL — Strictland, not yet committed):** the repo has a Strictland spike
  (`io.event-driven:strictland:0.3.0`, test scope) proven viable via a Jackson-3 adapter
  (`contract/JsonMapperMessageSerializer` + `ConferenceCancelledContractTest`). *If* Ted adopts
  Strictland, this plan's new events (`HotelBooked` with the new fields, `HotelBookingCancelled`)
  would each get a snapshot contract test (`thenContractIsUnchanged()` + an approved file) built
  via that adapter, catching accidental write-format drift the golden read test can't. **Do not
  implement this for this plan** — adoption is undecided (see
  `docs/Event_Serialization_Contract_Tests.md` open questions); ship the required golden +
  round-trip tests above regardless, since they stand whether or not Strictland is adopted.

**Projectors:** the six `HotelBooked` consumers ignore the new fields automatically; only views
that display the deadline read it (see Phase 2 details view). No projector changes required in
Phase 1.

---

## Phase 2 — `cancelBy` display + Cancel Hotel (FUTURE/ALL filter already done)

### 2a. FUTURE/ALL filter — DONE (arrived via the shared `TimeFilterToggle` convention)
`BookedHotelView implements TemporalView` (`relevantUntil()` = `checkOut.utc()`),
`BookedHotelsProjector.views(TimeView, Instant)` filters via `timeView.includes(...)`,
`BookedHotelsController` reads `?filter=`, and `BookedHotelsRenderer` calls
`TimeFilterToggle.render("/booked-hotels", activeFilter)`. Covered by `BookedHotelsRendererTest`.

### 2b. `cancelBy` column in `/booked-hotels`
Phase 1 left `cancelBy` write-only (captured, validated, round-tripped through the edit form,
rendered nowhere) — the code review flagged the gap, and this closes it.
- `BookedHotelView` — carry `ZonedTimestamp cancelBy` (nullable).
- `BookedHotelsProjector.put(...)` — pass it through from `HotelBooked` / `HotelChanged`.
- `BookedHotelsRenderer` — a "Cancel By" column: `ZonedTimeTag.render(...)` when set, an em-dash
  when not, and a neutral past-deadline style (no fee wording). `ZonedTimeTag` is fine here —
  `/booked-hotels` is OWNER-only, so the UTC `datetime=` attribute is not a redaction concern.
  **Do not** let `cancelBy` reach `CalendarEntry`: it is a travel time of day and would have to
  be redacted for anonymous viewers.
- `BookedHotelsRendererTest` + `BookedHotelsProjectorTest` cases for set/absent/past.

### 2c. Cancel
- **Domain:**
  - `HotelBookingCancelled(HotelBookingId hotelBookingId, String reason)` implements `Event`;
    reason null→"" in compact constructor (mirror `ConferenceCancelled`). Register it in
    `EventTypes` (`register("HotelBookingCancelled", ...)`) — the completeness case in
    `EventTypesTest` fails until you do.
  - `CancelHotelCommand implements DomainCommand<CancelHotelContext>` + `CancelHotelContext`
    (record: `boolean bookingExists`, `ZonedTimestamp checkIn`, `Instant now` — mirror
    `ChangeHotelContext`, which is `(boolean bookingExists, Instant now)`; **not** the
    `LocalDateTime` pair this plan originally said, since the UTC migration). Rules: throw
    `HotelBookingNotFound` if absent; throw `CannotCancelAfterCheckIn` (new, `domain/`) if
    `now` is not before `checkIn.utc()`; else emit `HotelBookingCancelled`.
- **Application:** `CancelHotel` service using `CommandExecutor.execute(commandId, request,
  context, command)`. Build the context from `HotelDetailsViewProjector.findById(...)` — the
  read model already carries `checkIn`, so mirror `ChangeHotel` (which reads the projector),
  **not** `ChangeFlight` (which folds the raw stream and appends directly — legacy path).
  `commandId` is a fresh `UUID` from the controller, not the `hotelBookingId`.
- **Export/import (required, easy to forget):** `CancelHotelRequest implements ImportableCommand`
  in `web/` — fields `hotelBookingId` + `reason`, `commandId()` a stored fresh UUID (a booking can
  only be cancelled once, but keep the id explicit so replay is deterministic), `events()`
  returning `new CancelHotelCommand(...).execute(new CancelHotelContext(true, checkIn,
  IMPORT_BYPASS_INSTANT))` — the same shape `BookHotelRequest.events()` uses. Add
  `register("CancelHotel", CancelHotelRequest.class)` to `ImportableCommandTypes` and a case in
  `CommandExportImportRoundTripTest`.
  *Open detail:* the command needs the booking's `checkIn` to build its context during import
  validation, but the importer replays commands without a read model. Simplest resolution:
  carry `checkIn` on the request record (as `MigrateConferenceToGathering` carries the fields its
  events need), so `events()` is self-contained. Decide this when implementing.
- **Projections:** add `HotelBookingCancelled` handling — **remove the entry** (decision above:
  hard removal, no tombstone) — to all six `HotelBooked` consumers: `BookedHotelsProjector`,
  `TentativeHotelBookingProjector`, `TentativeHotelBookingsProjector`, `HotelCalendarProjector`,
  `ItineraryProjector`, `ScheduleGapProjector` — plus `HotelDetailsViewProjector`. Also check
  `LocationAuditProjector` (it consumes hotel events and its test moved in Phase 1). Cover with
  book→cancel lifecycle scenario tests per projector.

### 2d. Cancel form on the existing edit page

`HotelDetailsView`, `HotelDetailsViewProjector`, `ChangeHotelController`
(`GET`/`POST /booked-hotels/{id}` → `change-hotel.html`) and the `/booked-hotels/*` security
matcher all shipped with Edit Hotel. No new page and no nav change — the `/booked-hotels` row
already links here, as does the itinerary edit pencil.

- `change-hotel.html` — add a cancel block **outside** the edit `<form>` (HTML forbids nested
  forms): its own form → `POST /booked-hotels/{id}/cancel` with an optional `reason` input, a
  confirm step, and the `cancelBy` deadline shown with a neutral passed/not-passed indicator.
- `CancelHotelController` (`@Controller`, new — controllers stay one-slice/I-O-only):
  `POST /booked-hotels/{hotelBookingId}/cancel`, `commandId`/`now` captured here; catch
  `HotelBookingNotFound` / `CannotCancelAfterCheckIn` → flash + `redirect:/booked-hotels`;
  success → `redirect:/booked-hotels`. `ReadOnlyModeException` is already enforced inside
  `CommandExecutor`.
- **Security — the existing matcher does NOT cover this.** Spring's `/booked-hotels/*` matches
  one path segment only (hence the explicit `/booked-flights/*/lookup` entry alongside it). Add
  `/booked-hotels/*/cancel` (and `/booked-hotels/*/replace` in Phase 3) to the `hasRole("OWNER")`
  matchers, or widen to `/booked-hotels/*/**`.
- **Tests:** `@WebMvcTest` for POST cancel (`@WithMockUser`, `.with(csrf())`) — success redirect,
  not-found flash, after-check-in flash; a new `AuthorizationMatrixTest` row for
  `/booked-hotels/abc/cancel` (the matrix currently lists `/booked-hotels/abc` — follow its
  convention for POST-only routes).

---

## Phase 3 — Replace Hotel

Confirmed still in scope (2026-08-07) despite Edit Hotel shipping: Edit rewrites a booking in
place, Replace keeps the cancelled stay and its reason as their own events.

- **Second schema bump:** add `HotelBookingId replacesHotelBookingId` (nullable) to `HotelBooked`
  — deferred out of Phase 1 because nothing set or read it. Jackson treats it as additive, same
  backward-compat story as `cancelBy`; extend `GoldenEventDeserializationTest` again so a
  pre-Phase-3 payload reads back `null`. `HotelChanged` does **not** get the field (a plain edit
  never creates a replacement link, and `HotelChanged` is a full snapshot — carrying it would mean
  the edit form has to round-trip it, exactly the trap Phase 1 hit with `cancelBy`). *Consequence:*
  editing a replacement booking is fine, but the link lives only on the original `HotelBooked`.
- **Domain:** `ReplaceHotelCommand implements DomainCommand<ReplaceHotelContext>`.
  - `ReplaceHotelContext`: old booking `bookingExists`, old `checkIn` (`ZonedTimestamp`), and
    `Instant now` for both the cancel gate and the new stay's booking validation.
  - Rules: throw `HotelBookingNotFound` if old absent; throw `CannotReplaceAfterCheckIn` if
    `now` not before old `checkIn.utc()`; apply the normal book validation to the new stay
    (future check-in, valid date range, `cancelBy ≤ checkIn` → `InvalidCancelByDate`).
  - `execute(...)` returns a `Stream.of(...)` of **two existing events**:
    1. `HotelBookingCancelled(oldId, reason)`
    2. `HotelBooked(newId, …, cancelBy, replacesHotelBookingId = oldId)`
  - Both event types registered in `EventTypes` (Phase 0 + Phase 2c). One `commandId`
    → atomic via `CommandExecutor.execute` (it appends the whole stream transactionally; no new
    plumbing).
- **Application:** `ReplaceHotel` service via `CommandExecutor`, reading the old booking from
  `HotelDetailsViewProjector` (mirror `ChangeHotel`/`CancelHotel`). Zone resolution for the new
  stay goes through a handler, as `BookHotelHandler` does.
- **Projections:** NO new event handling — the cancel event removes the old booking, the book
  event adds the new one (both handled after Phase 2c). Add a book→replace lifecycle scenario
  test confirming old gone + new present + link set on the event.
  - **`replacesHotelBookingId` is data-only.** Because cancel hard-removes (decision above), the
    old booking is absent from every read model, so a "replaced from …" line could only print a
    bare UUID. Do not build that display; the link's value is in the event log / timeline. If a
    visible provenance line is ever wanted, that needs a read model that survives cancellation —
    revisit the tombstone decision then.
- **Web:**
  - `ReplaceHotelRequest implements ImportableCommand` (its `events()` reconstructs both events
    with `IMPORT_BYPASS_INSTANT`, carrying whatever the context needs — same self-containment
    question as `CancelHotelRequest` in Phase 2c); add
    `register("ReplaceHotel", ReplaceHotelRequest.class)` to `ImportableCommandTypes` + a
    `CommandExportImportRoundTripTest` case.
  - `POST /booked-hotels/{oldId}/replace` on a dedicated `ReplaceHotelController`, launched from a
    book-hotel-style Thymeleaf form on `change-hotel.html` (third form on the page, alongside edit
    and cancel — again un-nested) carrying `oldId`; same error→flash handling.
  - **Security:** add `/booked-hotels/*/replace` to the OWNER matchers (the `/booked-hotels/*`
    pattern does not span the extra segment) + an `AuthorizationMatrixTest` row.

---

## Verification

- **Unit/slice tests:** `./mvnw test` (Maven project) — new `CancelHotelCommandTest`,
  `ReplaceHotelCommandTest`, projector lifecycle tests, `HotelDetailsViewProjectorTest`,
  renderer tests, `@WebMvcTest` controller tests, round-trip + golden-JSON contract tests,
  `AuthorizationMatrixTest`. These are plain JUnit/`@WebMvcTest` (not the `js` group), so the
  default build runs them. Finish with the "All Tests" IDEA run configuration.
- **JS-behavior tests (only if needed):** if `change-hotel.html` gains an inline script (a
  cancel/replace confirm dialog is the likely one), that behavior goes in the Playwright `js` tier
  (`JsBehaviorTest`, `@Tag("js")`, `./mvnw test -Pjs-tests`) per CLAUDE.md — not a server test.
  A plain `onsubmit="return confirm(...)"` is cheap to test there; prefer it to a bespoke modal.
- **Phase 0 (done):** `EventTypesTest` — completeness (every `Event` registered), round-trip under
  logical names, legacy FQCN `type` values still resolve. Boot the app against an existing DB to
  confirm replay of pre-change `event_log` rows still works.
- **Backward-compat:** golden pre-change `HotelBooked` JSON deserializes with new fields null;
  an existing exported backup imports unchanged. Both `cancelBy` (Phase 1) and
  `replacesHotelBookingId` (Phase 3) get their own golden case.
- **End-to-end (manual / run app):**
  1. Book a hotel with and without a `cancelBy`; confirm it appears under FUTURE and that the
     Cancel-By column shows the deadline (and an em-dash when there is none).
  2. Open its edit page from `/booked-hotels`; verify the deadline display and the passed
     indicator (book one with a deadline already in the past to see it).
  3. Edit the booking without touching `cancelBy` → the deadline survives (the full-snapshot trap).
  4. Cancel with a reason → disappears from `/booked-hotels` (**including ALL**), calendar,
     itinerary, schedule-gap.
  5. Attempt to cancel a past-check-in booking (use ALL view) → blocked with message.
  6. Replace a future booking → old gone, new present; verify atomicity (both events or neither)
     and the `replacesHotelBookingId` link in the event log / timeline.
  7. Toggle FUTURE/ALL and confirm filtering is by **check-out** (a stay in progress stays under
     FUTURE).
  8. Export a backup after cancel and replace, wipe, re-import → same end state.

## Open follow-ups (out of scope, noted)
- **Book/Change hotel form hint is wrong** (`book-hotel.html` / `change-hotel.html`, cancel-by
  hint): it says the deadline "never blocks anything", but a deadline after check-in is rejected
  with `InvalidCancelByDate`. Reword to something like "must be on or before check-in; otherwise
  informational only." *(Found by code review of the Phase 1 working tree, 2026-08-07.)*
- **Duplicated `cancelBy(LocalDateTime, ZoneId)` helper** in `BookHotelHandler` and
  `ChangeHotelHandler` — byte-identical; will drift if the null-preserving zone rule changes.
- **Editing check-in earlier than an existing `cancelBy`** now fails on a field the user never
  touched (the form prefills it). Acceptable, but if it becomes annoying the alternative is to
  clamp `cancelBy` to the new check-in rather than reject.
- Private social event kind (unrelated, tracked in `docs/Cleanup_Tasks.md`).
# Plan: Change Hotel Booking (in-place edit)

## Context

`/booked-hotels` today is a read-only j2html list (with the shared FUTURE/ALL toggle) and no
way to correct a booking. We want to let the owner **edit an existing booked hotel in place**
— same `HotelBookingId`, full-snapshot overwrite — to fix things like the check-in time or a
wrong location. Modeled directly on the just-shipped **Change Train** slice
(`docs/ChangeTrainPlan.md`, `ChangeTrainCommand` / `ChangeTrain` / `ChangeTrainController`).

This is the *in-place change* model (one `HotelChanged` event overwrites every hotel
projection), **not** the cancel+replace model in `docs/HotelCancelReplacePlan.md` — that work
stays shelved. Decisions confirmed with the user:

1. **Model:** in-place — `ChangeHotel` command → single `HotelChanged` full-snapshot event,
   keyed by the unchanged `hotelBookingId`. No cancellation, no new id, no replacement linkage.
2. **Action gate:** none on the *original* check-in (matches Change Train). Validation matches
   Book Hotel: the *new* check-in must be in the future and check-out at least one calendar day
   after check-in. You may edit a booking whose original check-in already passed, as long as the
   new check-in is in the future.
3. **Edit UI:** a new Thymeleaf edit page reached **from a `/booked-hotels` row link only** (no
   `index.html` nav card), pre-filled from a new `HotelDetailsView` projection. POST form via
   Thymeleaf + CSRF per project rule.

### What's already in place (no work)
- **`EventTypes` Phase 0** is done (`infrastructure/EventTypes.java`). `HotelChanged` just gets
  one `register(...)` line — no separate phase, no FQCN-migration concern.
- **FUTURE/ALL filter** on `/booked-hotels` already exists — `BookedHotelsProjector.views(TimeView,
  now)` is wired and `BookedHotelView implements TemporalView`. No filter work in this plan.

### What hotels lack that trains/flights had (extra vs Change Train)
- Hotels have **no details-view projector**. Trains/flights both have `TrainDetailsView` /
  `FlightDetailsView` for pre-filling the edit form and checking existence; hotels only have the
  list projector (`BookedHotelsProjector`) and the tentative-booking projectors. So this slice
  must add `HotelDetailsView` + `HotelDetailsViewProjector` (Phase 2c), modeled on
  `TrainDetailsViewProjector`.

### Key architectural constraints (from CLAUDE.md + memory)
- New application services MUST append events via `CommandExecutor`
  (`application/CommandExecutor.java`) — never `EventStore` directly. Follow the
  `ChangeTrain` -> `CommandExecutor.execute(...)` shape. (`ChangeFlight` uses the legacy direct
  path; do NOT copy it.)
- Domain commands implement `DomainCommand<C extends DecisionContext>` and emit events from
  `execute(context)` (see `BookHotelCommand` + `BookHotelContext`). The command record carries
  the new field values; the context carries decision facts (`bookingExists`, `now`).
- Optional `String` fields use `""` sentinel normalized in the compact constructor
  (`HotelBooked.mapsUrl` already does this).
- Every new `ImportableCommand` needs a `register(...)` line in `ImportableCommandTypes` and a
  case in `CommandExportImportRoundTripTest` (internal commands use typed records, never `Map`).
- Projections must be covered by book→change lifecycle scenario tests (not by sealing `Event`).
- Thymeleaf `@Controller` endpoints need `@WebMvcTest`; POSTs need `.with(csrf())` +
  `@WithMockUser`; `@DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")` on every datetime-local field.
- New page navigation: row link is the entry point; no `index.html` card this slice (confirmed).

---

## Phase 1 — Domain: `HotelChanged` event + `ChangeHotel` command

### `HotelChanged` (`domain/`)
Full snapshot, identical shape to `HotelBooked`:

```
HotelChanged(HotelBookingId hotelBookingId,
             String hotelName,
             Address address,
             LocalDateTime checkIn,
             LocalDateTime checkOut,
             BookingIntent bookingIntent,
             String mapsUrl)  implements Event
```
- Compact constructor normalizes `mapsUrl` null→"" (copy from `HotelBooked`).
- Register in `EventTypes`: `register("HotelChanged", HotelChanged.class)`.

### `HotelBookingNotFound` (`domain/`)
- New runtime exception (mirror `TrainNotFound` / `FlightNotFound`). There is no existing
  hotel "not found" exception.

### `ChangeHotelCommand implements DomainCommand<ChangeHotelContext>` (`domain/`)
- `ChangeHotelContext(boolean bookingExists, LocalDateTime now) implements DecisionContext`.
- The command record carries the same seven fields as `BookHotelCommand` (the new values).
- `execute(ChangeHotelContext context)` rules (reuse existing booking exceptions):
  - `if (!context.bookingExists())` → throw `HotelBookingNotFound("No hotel booking exists with
    that id")`.
  - `if (checkIn == null || !checkIn.isAfter(context.now()))` → `CheckInNotInFuture` (existing).
  - `if (checkOut == null || !checkOut.toLocalDate().isAfter(checkIn.toLocalDate()))` →
    `InvalidHotelDateRange` (existing).
  - else `Stream.of(new HotelChanged(... full snapshot ...))`.
- **Tests:** `ChangeHotelCommandTest` — happy path + each of the three error rules
  (booking-not-found, check-in-not-in-future, invalid-date-range).

---

## Phase 2 — Application + projections + edit page

### 2a. Application service `ChangeHotel` (`application/`)
- Constructor deps: `CommandExecutor` + `HotelDetailsViewProjector` (for existence). Do NOT take
  `EventStore` directly.
- `changeHotel(UUID commandId, ChangeHotelRequest request, LocalDateTime now)`:
  - guard read-only.
  - `commandId` and `now` are captured at the controller boundary and passed in (the service does
    no clock/UUID I/O). `commandId` is a **fresh** id (NOT the `hotelBookingId`, which is the
    aggregate id) because a booking may be changed many times — same reasoning as `ChangeTrain`.
  - build `ChangeHotelContext(bookingExists, now)` where
    `bookingExists = detailsProjector.findById(command.hotelBookingId()).isPresent()`.
  - `commandExecutor.execute(commandId, request, context, command)`.
- `ChangeHotelHandler` (mirror `ChangeTrainHandler` / `BookHotelHandler`) maps
  `ChangeHotelRequest` → `ChangeHotelCommand`, building `Address` from the request fields
  (street, city, region, country, postalCode, locationForMatching).

### 2b. Projector updates — handle `HotelChanged` everywhere `HotelBooked` is consumed
Each consumer gets a `HotelChanged` branch that overwrites/re-evaluates by `hotelBookingId`
exactly as the `HotelBooked` branch does (full snapshots). The six existing consumers:
- `BookedHotelsProjector`
- `TentativeHotelBookingProjector`
- `TentativeHotelBookingsProjector`
- `HotelCalendarProjector`
- `ItineraryProjector`
- `ScheduleGapProjector`

Plus the new projector in 2c. **Tests:** a book→change lifecycle scenario test per projector
(guards the silently-missed-event risk). No sealing of `Event`.

> Note: the `HotelBooked` and `HotelChanged` branches in each projector are identical
> field-for-field. Factor the snapshot-building into a shared private helper per projector to
> avoid drift between the two branches.

### 2c. `HotelDetailsView` + `HotelDetailsViewProjector` (`application/`) — NEW
- Model on `TrainDetailsView` / `TrainDetailsViewProjector`: handle `HotelBooked` and
  `HotelChanged` as upserts keyed by `HotelBookingId` (both full snapshots → `put`). Expose
  `findById(HotelBookingId)`. Carries the full snapshot needed to pre-fill the edit form
  (hotelName, full `Address`, checkIn, checkOut, bookingIntent, mapsUrl).
- **Test:** `HotelDetailsViewProjectorTest` (book then change → reflects the new snapshot).

### 2d. Web — edit page (Thymeleaf, mirrors `ChangeTrainController`)
- `ChangeHotelRequest implements ImportableCommand` (`web/`): the same fields as
  `BookHotelRequest` (hotelBookingId, hotelName, street, city, region, country, postalCode,
  locationForMatching, mapsUrl, checkIn, checkOut, bookingIntent) with
  `@DateTimeFormat(pattern="yyyy-MM-dd'T'HH:mm")` on both `LocalDateTime`s. Keep the
  `setState(...)` backward-compat alias for old exports if relevant.
  - `commandId()` returns `UUID.randomUUID()` — each change is a distinct command (NOT derived
    from `hotelBookingId`; this is the one deliberate divergence from `BookHotelRequest`, which
    reuses the bookingId). Copy the `ChangeTrainRequest` rationale comment.
  - `events()` → `new ChangeHotelHandler().handle(this).execute(new ChangeHotelContext(true,
    IMPORT_BYPASS_NOW))` (on import the booking is assumed already booked).
  - Register `register("ChangeHotel", ChangeHotelRequest.class)` in `ImportableCommandTypes`.
- `ChangeHotelController` (`@Controller`):
  - `GET /booked-hotels/{hotelBookingId}` → pre-fill from `HotelDetailsViewProjector.findById`;
    render `change-hotel` template. Not found → flash + `redirect:/booked-hotels`. Read-only →
    `redirect:/read-only`.
  - `POST /booked-hotels/{hotelBookingId}` → path is source of truth for `hotelBookingId`; call
    `ChangeHotel` with `UUID.randomUUID()` + `LocalDateTime.now(clock)` captured here; map
    `HotelBookingNotFound` → flash+redirect, `CheckInNotInFuture` →
    `bindingResult.rejectValue("checkIn", ...)`, `InvalidHotelDateRange` →
    `rejectValue("checkOut", ...)`, `ReadOnlyModeException` → `redirect:/read-only`; success →
    `redirect:/booked-hotels`.
- `change-hotel.html` template: copy `change-train.html` structure; fields match `book-hotel.html`
  (address block + check-in/check-out + booking intent). No external-lookup form.
- `BookedHotelsRenderer.renderRow` — link each row to `/booked-hotels/{hotelBookingId}` (primary
  entry point). Update `BookedHotelsRendererTest`.
- **Security:** add `/booked-hotels/*` (and `/booked-hotels/*/**` if needed) to the
  `hasRole("OWNER")` matchers in `SecurityConfig`, mirroring the `/booked-trains/*` entries. Add
  rows to `AuthorizationMatrixTest` for `/booked-hotels/{id}` GET + POST.

---

## Backward-compat / contract tests

`HotelChanged` is a new event (no old rows to read), so read-side risk is low, but follow the
established infra:
- **Round-trip (required):** add a `ChangeHotel` case to `CommandExportImportRoundTripTest`
  (export a ChangeHotel command, re-import, assert the `HotelChanged` event reconstructs).
- **Golden (optional but cheap):** a `HotelChanged` case in `GoldenEventDeserializationTest`
  alongside the existing `HotelBooked` cases — inline the sample JSON as a text block (<30 lines).
- **Write-side (Strictland):** out of scope — adoption is paused.

---

## Verification

- **Unit/slice:** `./mvnw test` — `ChangeHotelCommandTest`, the six projector lifecycle tests,
  `HotelDetailsViewProjectorTest`, `BookedHotelsRendererTest` (row link), `@WebMvcTest`
  `ChangeHotelControllerTest` (GET render + POST change, `@WithMockUser`, `.with(csrf())`),
  round-trip + golden tests, `AuthorizationMatrixTest`.
- Run the **"All Tests"** IDEA run configuration when done.
- **End-to-end (run app):**
  1. Book a hotel; open its edit page from a `/booked-hotels` row (pre-filled with full address +
     dates).
  2. Change check-in/check-out/location → `/booked-hotels`, calendar, itinerary, schedule-gaps,
     and tentative views all reflect the new details under the same `hotelBookingId`.
  3. Try a past/invalid new check-in → `CheckInNotInFuture` shown on the form.
  4. Try check-out not after check-in → `InvalidHotelDateRange` shown.
  5. Edit a booking whose original check-in already passed but with a future new check-in →
     allowed (no original-check-in gate).
  6. Export then import a backup containing a ChangeHotel → booking shows the changed details.

## Out of scope
- Cancel / replace semantics for hotels (`docs/HotelCancelReplacePlan.md` — in-place edit only here).
- The `cancelBy` advisory-deadline field from the cancel/replace plan.
- `index.html` nav card (row link is the only entry point this slice).

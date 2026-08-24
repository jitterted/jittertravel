# Scheduled Transit Trip — Plan

**Status:** planned 2026-08-23 (Ted), nothing built. Small: one new enum, ~14 edits, **no migration,
no schema bump, no new route**.

A train trip becomes a **scheduled transit trip** that knows whether it is a train or a bus. That is
the whole feature: one enum on `TrainBooked` / `TrainChanged`, defaulting to `TRAIN`, plus the
labels and icons that read it.

## Why

A ticketed, low-frequency bus to the airport is a **scheduled leg**, not a hop. It has a departure
you can miss, a fare bought in advance, and a connection worth checking. Today the only way to
record one is a ground transfer, and a transfer is the wrong shape for it on purpose: its times are
approximate by design ("normally *just before* or *just after* a flight",
`archived/GroundTransferPlan.md`), it carries no service identifier, and its `mode` is free text
nothing reads. So the bus is invisible — it occupies no calendar entry of its own, and nothing
warns when the flight it feeds moves earlier.

The case that raised it (Ted, 2026-08-23): staying with a friend in Estes Park through Monday
28 September, the friend drives to Boulder, then the 12:26 pm **AB1** bus from 1800 14th St, Gate 2
reaches DEN at 1:50 pm. Ted had already hit the same shape once before — an airport bus that
required a ticket purchase and ran on a sparse timetable.

**The line is "does it have a schedule I have to catch?"** — not train-versus-road, and not
vehicle type. A London tube ride stays a ground transfer; a 40-minute-headway airport coach is a
trip. Which side a given ride falls on is **Ted's judgment at entry time**; the app must not try to
test frequency or ticketing to decide for him. See D6.

### It already works, unofficially

`TrainBooked` is *structurally* a scheduled bus today: two named stops with free-text names, two
`ZonedTimestamp`s, and a free-text `serviceId`. Recording the AB1 as a train trip right now gives
Ted the real leg, the real times, the DEN connection check, and a Boulder endpoint the Estes Park
ground transfer can point at (`train:<tripId>:departure` is already offered in the "Train
departures" optgroup). The only thing wrong with it is that it says *Train*. **That is what this
plan fixes, and nothing else** — which is why the plan is so small.

## Decisions (Ted, 2026-08-23)

**D1 — The only addition is an enum.** `TransitMode { TRAIN, BUS }` on `TrainBooked` and
`TrainChanged`. No new event, no new `EntryKind`, no split of the type. `FERRY` is expected
eventually and costs one constant plus one icon.

**D2 — `TRAIN` is the default, and the default *is* the backfill.** `null → TRAIN` in the compact
constructor, exactly as `GroundTransferPlanned.mode` normalizes `null → ""`. Every stored row and
every existing backup is a train, which is true, so there is nothing to migrate and no one-off
task to declare.

**D3 — No renames.** Not the classes (`Train*` is 26 types across 55 files in `src/main`), not the
event names, not the URLs. Worth stating why the tidy version is *not* on the table: the stored
logical name in `EventTypes.register("TrainBooked", TrainBooked.class)` lives in every `event_log`
row and every backup file, so it has to stay `TrainBooked` whatever the Java class is called.
Renaming the class therefore buys a permanent name/stored-name mismatch rather than removing one.

**D4 — Bus-versus-train is public.** It renders on the anonymous calendar. See *Redaction* below
for why that is publishable and what still is not.

**D5 — `/booked-trains` keeps its URL.** Only headings and labels become bus-aware. No
`SecurityConfig` matcher, no `AuthorizationMatrixTest` row, no broken bookmarks.

**D6 — Ground transfer keeps its free-text `mode`; the two do not merge.** Note the deliberate
asymmetry, because it is the reason one is text and one is an enum: a transfer's `mode` **drives
nothing** — it renders and stops — so free text costs nothing and an enum would only fight the open
set ("a friend is driving"). This one **drives the icon and the word**, and a value that picks an
icon must not be free text, or `bus` / `Bus` / `coach` each render differently.

## Model

**`TransitMode` (new, `domain`)** — `TRAIN`, `BUS`. Pure: an enum with no methods that format
anything. No `label()`, no `icon()` — presentation picks the word and the glyph (CLAUDE.md,
"Presentation formatting stays out of the domain").

**Events.** `TrainBooked` and `TrainChanged` gain `TransitMode mode`, normalized `null → TRAIN` in
the compact constructor beside the existing `serviceId` normalization. This is additive in the
strict sense `GoldenEventDeserializationTest` was built to allow: old JSON has no such property, so
the component arrives null and becomes `TRAIN`. **No `schema_version` bump, no upcaster**, and
backup files at v2 and v3 keep restoring. (Contrast `ConferenceFormat`, which needed both.)

**Commands.** `BookTrainCommand` and `ChangeTrainCommand` carry it through to the event.

**`EntryKind` does not change.** A bus is a `TRAIN` entry kind in a `TRAIN` calendar lane. A
`BUS` kind would mean a new lane band, a new branch in `PublicCalendarProjector`, a new row in
`PublicCalendarProjectorTest.oneOfEveryKind()`, and a split in what the schedule timeline counts as
a scheduled leg — all to say something the icon already says.

## What changes on screen

**Owner**

- `TrainCalendarProjector.buildEntries` — the hardcoded `"🚄 "` on the route line becomes per-mode
  (🚄 / 🚌). Everything else about the entry is unchanged, including the two-entry overnight split.
- `ItineraryRenderer.renderTrain` — `kindLabel` is `"Train"`/`"Arriving"` today; the departure-day
  label becomes `"Bus"` for a bus. `TRAIN_SVG` needs a bus sibling (Font Awesome Pro fill-based, from
  the travel-icons row in `index.html`, in the train's `#9a3412`).
- `BookedTrainsRenderer` — page title and `h1` become bus/train aware, and each card gets the mode.
  *Exact wording is Ted's call*; "Booked Bus & Train Trips" is a placeholder, not a decision.
- `book-train.html` and `change-train.html` — a Train/Bus control defaulting to Train. **Two
  radios, not a dropdown**: two choices are rendered inline (CLAUDE.md, dropdowns only above three).
- Nav cards in `index.html` — `/book-train` reads "Train · Record a train journey" and
  `/booked-trains` reads "Booked trains"; both want the bus in their wording.

**Public**

- `PublicCalendarProjector.train(...)` takes the mode and picks the icon for the route title. The
  cities are unchanged and there is still no subtitle. Keep the glyph **in the title string** rather
  than adding a component to `EntryDetails.PublicTrain` (which today has none): the title already
  carries the 🚄, and a new publishable component should be reserved for a value a renderer must
  branch on.

**Untouched, and worth knowing they are untouched:** `ScheduleGapProjector` (a bus already counts as
travel the moment it is a trip — that is the whole point), `GroundTransferEndpointResolver` and its
`train:<tripId>:arrival|departure` tokens (transient form values, never stored), the iCal feed
(`CalendarFeedAssembler` carries CFP and hotel-cancel deadlines only — no legs), and
`LocationAuditProjector`.

## What must not change

- The **stored logical names** `TrainBooked` / `TrainChanged` (D3).
- The **record component names** — `departureStation`, `arrivalDateTime`, … — which are the JSON
  field names in every stored payload.
- The **station `name` field stays private.** A bus stop is address-shaped ("1800 14th St, Gate 2")
  where a station name is a proper noun, but both go in `name`, which `PublicCalendarProjector` does
  not read. `serviceId` stays private too: `AB1` is a service identifier exactly as `ICE 123` is.

## Redaction

**New public value: the mode.** Publishable because it adds nothing to what the day column and the
two city names already say — a stranger who can see "Boulder → Denver on 28 September" learns
nothing further from the vehicle. Note what that argument rests on, in case a future variant
strains it: the mode says *what kind of vehicle*, never *which service*, *which stop*, or *when*.

Both tiers are still required (CLAUDE.md rule 5), and the second is the one that matters:

1. `PublicCalendarProjectorTest` — a `BUS` trip publishes the bus glyph and the two cities.
2. `PublicCalendarProjectorTest` **and** `CalendarRedactionSecurityTest` — re-run the existing
   station-name and `serviceId` absence assertions **with a `BUS` fixture**, so the new field cannot
   quietly carry an old secret out with it.

CLAUDE.md's "Public by decision" list gains a line when this ships.

## Files

**Create (1 + tests):** `domain/TransitMode.java`.

**Edit — domain (4):** `TrainBooked`, `TrainChanged`, `BookTrainCommand`, `ChangeTrainCommand`.

**Edit — application (5):** `TrainCalendarProjector`, `PublicCalendarProjector`,
`BookedTrainsProjector` + `BookedTrainView`, `ItineraryProjector` + `TrainItineraryEntry`.

**Edit — web (7):** `BookTrainRequest`, `BookTrainController`, `ChangeTrainRequest`,
`ChangeTrainController`, `BookedTrainsRenderer`, `ItineraryRenderer`, plus `book-train.html`,
`change-train.html` and the two nav cards in `index.html`.

**Edit — infrastructure (0).** Deliberately: no `EventTypes` schema bump, no upcaster, no
`OneOffTaskRegistry` entry. If this list grows an infrastructure row, something has stopped being
additive — stop and re-read D2.

## Tests

- **Golden** (`GoldenEventDeserializationTest`): assert the *existing* `TrainBooked` sample — which
  has no `mode` — reads back as `TRAIN`, and add a second sample carrying `"mode": "BUS"`. Same
  pairing used for `GroundTransferPlanned.mode` on 2026-08-23: the untouched old sample is the
  proof that the addition is additive, so it must not be edited.
- `TrainCalendarProjectorTest` — a bus entry leads with the bus glyph; a train is unchanged.
- The two redaction cases above.
- `BookedTrainsRendererTest`, `ItineraryRendererTest` — the label and the badge, asserted as whole
  elements (`contains("<title>…</title>")`, not bare words).
- `BookTrainControllerTest` / `ChangeTrainControllerTest` (`@WebMvcTest`, since the templates are
  Thymeleaf) — the control binds, and a form that omits it books a `TRAIN`.
- Mutation-verify every one: flip the production glyph, the default, and the normalization, and
  watch each go red.

## Build order

1. Enum, events, commands, golden tests. Nothing visible changes; every existing trip is a `TRAIN`.
2. Forms accept it.
3. Owner surfaces render it.
4. Public calendar plus both redaction tiers.
5. *Optional:* the ground-transfer endpoint labels. The "Train arrivals" / "Train departures"
   optgroups and the option text (`Hamburg Hbf — Hamburg · arrive Wed Sep 16, 11:00 AM (ICE 573)`)
   come from `TransferEndpointProjector` → `TransferEndpointRow` → `GroundTransferEndpointOptions`,
   so saying "bus" there means carrying the mode into that row. Everything works without it; only
   the wording is stale.

## Deferred

- **`FERRY`** — one constant and one icon, the day a ferry is booked.
- **Renaming `Train*` to `Transit*`** (D3). If it ever happens, the stored names still stay.
- **A rule that decides transfer-versus-trip for Ted** (D6). No.
- **Per-mode colour.** A bus keeps the train lane's orange. Revisit only if a week with both becomes
  hard to read — and note that problem colouring beats taxonomy here too: the lane colour means
  "scheduled leg", and splitting it would say less, not more.

## Related

- `archived/GroundTransferPlan.md` — the other half of the distinction. **Its D7 ("no `mode`") was
  reversed on 2026-08-23**, though not in the shape it predicted: what shipped is free text, not the
  `GroundTransferMode { TAXI, TRANSIT, … }` enum it sketched, for the reason in D6 above. That doc
  carries the amendment, including why this plan re-cuts the line it drew.
- CLAUDE.md, *Redaction* — carrier/service identifiers, and the ground transfer `mode` line added
  2026-08-23.

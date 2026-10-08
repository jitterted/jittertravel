# Location Data Cleanup Plan

**Status:** `in progress` — direction agreed with Ted 2026-10-06; the fix list in §5.2 approved
2026-10-07. Steps 1 and 2 committed together in `7ccc180` (2026-10-08), not yet pushed; the
pre-push preflight and the post-rollout `MIGRATE` are in `Pre-Push-Tasks.md`. Step 3 waits on the
migration having run in production. Mockup:
<https://claude.ai/artifact/KvPTqeESSdR6H29rL5EgkQ>.

## 1. Why

Ted asked for US places to read "Denver, CO" and everywhere else "Vienna, Austria". The first
attempt (see §7) did it by teaching the display to tolerate every spelling in the log:
six names for the United States, states by name or by code. That is a second copy of a workaround
that already exists — `LocationZoneResolver.defaultRegionScopeTable` and its region table, keyed
"under both the postal abbreviation and the spelled-out name — stored data uses both". Ted's
objection: clean the data instead of adding special cases.

The free-text fields are the cause. Across the 2026-09-18 production backup (139 events, ~130
addresses):

| What | Count |
|---|---|
| United States spelled `USA` / `United States` | 7 / 11 |
| US and Canadian regions spelled out (`Colorado`, `Ontario`, `Quebec`) beside codes (`CO`, `NY`) | 20 |
| Country missing — written by the app itself for airport endpoints (§4.3) | 4 |
| One venue stored under two different cities (§5) | 1 |
| A county in a city box, a typo, a wrong country, a missing state | 1 each |

## 2. Decisions (Ted, 2026-10-06)

- **D1 — Country is picked, never typed.** A select of the countries Ted has been to, alphabetical,
  then **Another country…**, which opens a second select of **every** country. Nothing typed means
  nothing misspelt. The short list is read from stored events, so it is only clean after §5 runs.
- **D2 — Country is stored as its ISO 3166-1 alpha-2 code** (`US`, `GB`, `DE`). A rename (Czechia,
  Türkiye) never leaves two spellings; pages turn the code into a name with the same table that fills
  the picker. Cost accepted: `/admin/eventlog` reads `"AT"`, not `"Austria"`.
- **D3 — Region keeps one field and stays free text outside the US, Canada and Australia.** In those
  three it is a select of full names storing the postal code (`Colorado` → `CO`), and required: the
  US label needs it, and the Canadian and Australian time zones do. Everywhere else Region is
  whatever Ted typed and is **never rewritten** — Abingdon, Altona, Westminster Borough and Bavaria
  are information he relies on (UK addresses especially). No new field, no event shape change. A
  separate "Area" field was proposed and dropped in favour of this.
- **D4 — City is the place Ted is physically in**, because that is the value schedule matching
  compares. A UK post town or a county is not a city (Steventon is the city; Abingdon its post town;
  "Oxford" in its address is the postcode area, not the city).

## 3. Order of work — the door first

1. **Write path** (§4). Otherwise every fix is re-dirtied by the next booking.
2. **Migration** (§5), after a fresh production backup.
3. **Delete the workarounds** (§6).

**Steps 1 and 2 ship in one push** (found while building step 1, 2026-10-07). Once step 1 is
deployed the forms submit codes and the commands refuse anything else, while every stored event
still holds a name. Opening any existing hotel or gathering would show its country as a leftover
option, and saving it would fail with "Unknown country" until the migration had run. The upcaster
rung in §5 converts on read, so the two together leave no window.

**Step 1 is built** (2026-10-07, committed in `7ccc180`). Where it differs from §4:

- The zone table gained the **ISO codes of its existing 23 countries**, not every country. A
  country picked from "Another country…" that the table lacks falls back to the form's time-zone
  select, as Brazil always will.
- **Without the script**, Region is the control for the country the page was loaded with, rather
  than both controls side by side. A mismatch is refused by the command.
- The mockup's "Not a state of Germany" cannot happen, because Region is free text outside the US,
  Canada and Australia. The real messages are "State required for United States",
  "Province required for Canada" and "Not a state of United States".
- **Parse ▶ and the Sessionize prefill** were filling in "United States" and "Colorado"; the
  Nominatim response's street order shows that most of the log's full names came from Parse ▶.
  Both now return codes. Sessionize also keeps the middle part of "Atlanta, Georgia,
  United States" when it is exactly one of the country's states, so Devnexus would now arrive
  as GA.

## 4. Write path

### 4.1 Forms

Country select per D1 on every form with an address: Book/Change Hotel, Book/Change Train (both
stations), Plan Gathering, Change Gathering, Plan Conference, Plan Private Event. Region per D3 on
those with a Region (not trains — a station has none). A few lines of inline script switch Region
between the state list and the text box when Country changes; without it both render and the
server uses the one that fits. JS tier (`JsBehaviorTest`) covers the switch.

### 4.2 Commands

Extend `EnteredLocation` (reject in the command, never in the record — Address must keep binding
every stored event):

- the country is a known ISO code;
- for US/CA/AU the region is a code of that country's list, and present;
- errors land under the input that fixes them: "State required for United States", "Not a state
  of Germany".

Conferences, gatherings and private events are not wired to `EnteredLocation` today; this plan
wires them for these two rules. Their existing venue/city rules are not extended here.

### 4.3 Airport endpoints write an incomplete address

`GroundTransferEndpointResolver.airportEndpoint` builds `new Address("", city, "", "", "", city)` —
city only, no country or state — because `StaticAirportCityResolver` knows only a city per code.
That is where the four `Denver, ""` transfers come from; no one typed them. The airport table needs
a country (and US/CA/AU state) per airport, so the frozen endpoint is a complete address.

**Decided (Ted, 2026-10-07): extend the existing table.** Each of the 58 entries in
`StaticAirportCityResolver` carries the whole place — `DEN → Denver, CO, US`, `FRA → Frankfurt, DE`
— rather than a second table that would have to agree with it on every code. The city lookup keeps
its current signature, so its seven other callers (`Place`, `AirportZoneResolver`,
`ScheduleGapProjector`, `TransferEndpointProjector`, `FamilyNotificationMessages`,
`BookFlightController`, `EventSourcingConfig` wiring) are untouched; `airportEndpoint` uses a new
lookup for the full place.

### 4.4 Zone table

`LocationZoneResolver`'s country table is keyed by name (`"germany"`, `"uk"`, `"england"`). It
becomes keyed by ISO code and gains every country D1 offers. Most are one zone. Multi-zone countries
other than US/CA/AU (Brazil, Mexico, Russia, Indonesia…) keep failing to derive and fall back to the
form's existing time-zone select.

## 5. Migration

An upcaster rung on each type carrying an `Address` or a station address (`HotelBooked`,
`HotelChanged`, `TrainBooked`, `TrainChanged`, `ConferencePlanned`, `GatheringPlanned`,
`GatheringChanged`, `GroundTransferPlanned`, `PrivateEventPlanned`), plus the eager migration so
stored rows are rewritten. `PrivateEventMatchingLocationChanged` carries only a
`locationForMatching` string, no address, and needs no rung. Destructive by CLAUDE.md's definition:
red, typed `MIGRATE`, backup first.

**Extraction re-run 2026-10-07** against `jittertravel-backup-production-2026-10-08T052706Z.json`
(172 events). Every sequence number in §5.2 still names the same event. New since 2026-09-18: a
second Ottawa/Kawartha trip (Ontario ×10 more), **British Columbia** ×2 (Vancouver gatherings #147,
#148), a fifth blank-country airport endpoint (**#162**, YOW), and **#172** Dallas, already stored as
`US`/`TX` — so the rung must pass a code through unchanged.

### 5.1 Mechanical (the rung itself)

| From | To | Count |
|---|---|---|
| `Germany` (incl. `"Germany "`) | `DE` | 66 |
| `Belgium` | `BE` | 12 |
| `United States` / `USA` | `US` | 18 |
| `UK` | `GB` | 10 |
| `Canada` | `CA` | 22 |
| `Netherlands` | `NL` | 3 |
| `Austria`, `Morocco`, `Sweden` | `AT`, `MA`, `SE` | 1 each |
| `US` (already a code) | unchanged | 1 |
| region `Colorado` (US) | `CO` | 10 |
| region `Ontario` / `Quebec` / `British Columbia` (CA) | `ON` / `QC` / `BC` | 19 / 1 / 2 |

Every other region is untouched (D3).

**How the rung decides (agreed 2026-10-07).** One `LocationCodesUpcaster` serves all nine types,
each bumped one schema version. It trims, then maps a country through `Countries.codeFor` plus the
aliases `USA` and `UK`, and a US/CA/AU region through `Subdivisions.findByName`. A value that is
already a code passes through. **A country name it cannot map fails loud** (Ted): boot replay and
restore stop and name the event, and the boot-replay preflight against the backup taken just before
the push is what catches it before production does. The approved fixes below and the airport rule
need lookups beyond the payload, so per R7a they are in the eager migration, not the rung.
Each fix is keyed by event id and **compare-and-set**: it rewrites a field only while it still holds
the expected old value.

### 5.2 Value fixes — all approved by Ted, 2026-10-07

Per-event, by event id, applied by the eager migration only. These are corrections, not format, so
a restore of an older backup brings the old values back; that is acceptable and keeps them out of
the rung.

- [x] **#19 HotelBooked — Prize by Radisson, Antwerp City.** country `Brussels` → `BE`. **Moved
  into the rung** as an alias: a rung that fails loud on an unknown name would otherwise stop boot on
  this row, so the correction has to be on the read path.
- [x] **#26, #27 TrainBooked — Didcot Parkway** (to and from SoCraTes UK, June 2026). city
  `Oxfordshire` → `Didcot`. The station is in Didcot; Oxfordshire was where Ted was heading, which
  the venue already records. Consequence: the June trip may show a missing **Didcot → Steventon**
  transfer on `/schedule-problems` — the same gap that is there now as "Oxfordshire → Steventon"
  (ground transfers did not exist until August).
- [x] **#58 HotelBooked, #86 GroundTransferPlanned (origin) — Best Western Plus St. Raphael.** city
  `St. Georg` → `Hamburg`, region `Hamburg` → `St. Georg`. A swap; nothing lost. Its
  `locationForMatching` is already `Hamburg`.
- [x] **#98 ConferencePlanned — Devnexus 2027, Atlanta.** region `""` → `GA`. (No venue-change event
  exists, so this is the only way to fix it.)
- [x] **#107, #111, #115 Train, #112 GroundTransferPlanned — Aschaffenberg.** city and
  `locationForMatching` `Aschaffenberg` → `Aschaffenburg`, and the names too: station name
  `Aschaffenberg Hbf` (#107, #111, #115) and transfer `originName` (#112) → `Aschaffenburg Hbf`.
  **#115's station is actually named `Asch`** (and its arrival `Frank`), so only its city is
  corrected; renaming it was not part of the approval.
- [x] **#123 ConferencePlanned — The Last Coder 2027.** city and `locationForMatching`
  `Rückersbach` → `Johannesberg`, region `""` → `Rückersbach`. The same venue is `Johannesberg` for
  Play4Agile, so today a Johannesberg hotel does not count as covering this conference. The venue
  name "SeminarZentrum Rückersbach" is unchanged, and Rückersbach moves to Region rather than being
  dropped.
- [x] **Airport endpoints — by rule, not by number** (Ted, 2026-10-07). A `GroundTransferPlanned`
  endpoint whose `originAirportCode`/`destinationAirportCode` is set and whose country is blank takes
  its region and country from the airport table: #132, #133, #135, #136 (DEN → `CO`, `US`) and #162
  (YOW → `ON`, `CA`), plus any airport transfer planned between the backup and the deploy, which a
  list of numbers would miss. An endpoint without an airport code is never touched. After §4.3 no
  new ones are written.

Untouched on purpose: Altona, Mitte, Westminster Borough, Abingdon, Bavaria, Baden-Württemberg,
Hesse, North Rhine-Westphalia, and blank regions for Aachen/Munich/Frankfurt (Region is optional
outside US/CA/AU).

## 6. What gets deleted afterwards

- `LocationZoneResolver`: the country-name aliases (`usa`, `u.s.a.`, `england`…) and the
  spelled-out state/province keys.
- `UsStates.isUnitedStates` and the name→code half of `stateCode`; what remains is the list of
  states that fills the select and validates the command.
- `CityLabel` reduces to "US → region, else the country's name"; the public-calendar fallback for
  an unrecognised US region goes, since the command no longer admits one.
- The rung's own translation table is the one list of old spellings left. It goes when the rung is
  retired (`EventPayloadUpcasterDesign.md`, "How to retire a rung"), i.e. once pre-migration backups
  no longer need restoring.

## 7. The display change

Held back until the migration was built, then committed with it in `7ccc180`. Kept: one display rule in `CityLabel` and every call site moved onto it,
`cityLine()` on the conference and hotel views (eight Thymeleaf forms used to build "City, Country"
themselves), the `qualifier` renames, and the redaction tests. Simplified after §5 as in §6. The
family-email wording was approved by Ted on 2026-10-07: a US conference reads "<venue>, Denver, CO"
("Atlanta, USA" with no state), a non-US one is unchanged ("<venue>, Johannesberg, Germany"), and
the preview's sample says "Germany" rather than "DE".

## 8. Open

_nothing open_ — the CLAUDE.md redaction section says a US state code is public (done in `9cc7d2f`).

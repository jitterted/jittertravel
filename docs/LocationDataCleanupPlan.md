# Location Data Cleanup Plan

**Status:** `decision` — direction agreed with Ted 2026-10-06; the fix list in §5.2 approved
2026-10-07. Nothing built. Mockup:
<https://claude.ai/artifact/KvPTqeESSdR6H29rL5EgkQ>.

## 1. Why

Ted asked for US places to read "Denver, CO" and everywhere else "Vienna, Austria". The first
attempt (uncommitted, see §7) did it by teaching the display to tolerate every spelling in the log:
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
`GatheringChanged`, `GroundTransferPlanned`, `PrivateEventPlanned`, and check
`PrivateEventMatchingLocationChanged`), plus the eager migration so stored rows are rewritten.
Destructive by CLAUDE.md's definition: red, typed `MIGRATE`, backup first.

**Re-run the extraction against a fresh backup before building.** The counts and sequence numbers
below are from `jittertravel-backup-production-2026-09-18T222302Z.json`; events added since are not
in them.

### 5.1 Mechanical (the rung itself)

| From | To | Count |
|---|---|---|
| `Germany` (incl. `"Germany "`) | `DE` | 65 |
| `Belgium` | `BE` | 12 |
| `United States` / `USA` | `US` | 18 |
| `UK` | `GB` | 10 |
| `Canada` | `CA` | 10 |
| `Netherlands` | `NL` | 3 |
| `Austria`, `Morocco`, `Sweden` | `AT`, `MA`, `SE` | 1 each |
| region `Colorado` (US) | `CO` | 10 |
| region `Ontario` / `Quebec` (CA) | `ON` / `QC` | 9 / 1 |

Every other region is untouched (D3).

### 5.2 Value fixes — all approved by Ted, 2026-10-07

Per-event, by event id, applied by the eager migration only. These are corrections, not format, so
a restore of an older backup brings the old values back; that is acceptable and keeps them out of
the rung.

- [x] **#19 HotelBooked — Prize by Radisson, Antwerp City.** country `Brussels` → `BE`.
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
- [x] **#123 ConferencePlanned — The Last Coder 2027.** city and `locationForMatching`
  `Rückersbach` → `Johannesberg`, region `""` → `Rückersbach`. The same venue is `Johannesberg` for
  Play4Agile, so today a Johannesberg hotel does not count as covering this conference. The venue
  name "SeminarZentrum Rückersbach" is unchanged, and Rückersbach moves to Region rather than being
  dropped.
- [x] **#132, #133, #135, #136 GroundTransferPlanned — DEN endpoints.** country `""` → `US`, region
  `""` → `CO`. After §4.3 no new ones appear.

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

## 7. The uncommitted display change

Held, not committed. Kept: one display rule in `CityLabel` and every call site moved onto it,
`cityLine()` on the conference and hotel views (eight Thymeleaf forms used to build "City, Country"
themselves), the `qualifier` renames, and the redaction tests. Simplified after §5 as in §6. The
family-email wording change ("<venue>, Denver, CO") still needs Ted's approval before it ships.

## 8. Open

- CLAUDE.md redaction section: say that a US state code is public (it is a city-level fact).

# Behavior Slices for "Change an Existing Flight"

This document describes a slice of behavior that lets a user change an existing
flight. It is split into two Event Modeling slices that can be built and tested
independently:

1. **Display all booked flights** — a view slice that lists every booked flight
   in its latest state.
2. **Edit a selected flight** — a state-change slice that submits changes and
   produces a new event.

The two slices are connected only through the shared event stream and through
the user's navigation from the list to the detail screen. There is no direct
call between them.

---

## Shared building blocks

These elements appear in both slices. They are one source of truth; each slice
shows only the subset it consumes.

### Events

**FlightBooked** (existing)
The event recorded when a flight is first booked. Carries the flight identity
and all flight details: airline, flight number, departure airport and date/time,
arrival airport and date/time. Uses the `FlightId` and `AirportCode` value
objects.

**FlightChanged** (new)
The event recorded when an existing flight's details are changed. It is a
**full snapshot**, not a delta: it carries the complete new set of field values
for every field except `flightId`, which is immutable. It uses the same
`FlightId` and `AirportCode` value objects as `FlightBooked`.

Because `FlightChanged` is a full snapshot, any read model that consumes it can
simply overwrite the row keyed by `flightId`. No merging against prior state is
required in the projection.

### Value objects

- **FlightId** — wraps a `UUID`; required (non-null).
- **AirportCode** — a 3-letter, letters-only code, normalized to uppercase.

---

## Slice 1 — Display all booked flights

A view slice. No command and no new event are produced here; it only projects
existing events into a read model that powers the list UI.

### Flow

`FlightBooked` + `FlightChanged` → **BookedFlights** read model → list wireframe

### Wireframe

A screen listing all booked flights in their latest state. Each row shows a
summary suitable for selection:

- airline
- flight number
- departure airport
- departure date/time

Selecting a row navigates the user to the edit slice's detail screen, handing
over the `flightId` of the chosen flight.

### Read model — BookedFlights

A projection holding one entry per flight, with just the fields the list needs:

- `flightId` (used by the UI to select a flight)
- airline
- flight number
- departure airport
- departure date/time

It is built by consuming both `FlightBooked` and `FlightChanged`. A
`FlightBooked` event inserts a new entry; a `FlightChanged` event overwrites the
matching entry, so the list always reflects the latest state.

---

## Slice 2 — Edit a selected flight

A state-change slice. It is entered from slice 1 by selecting a flight. The
detail screen is hydrated from its own read model, the user submits changes,
and a command produces the `FlightChanged` event.

### Flow

`FlightBooked` + `FlightChanged` → **FlightDetailsView** read model → detail
wireframe → **ChangeFlightCommand** → `FlightChanged` event

### Read model — FlightDetailsView

A projection holding the full set of details for a single flight, keyed by
`flightId`:

- airline
- flight number
- departure airport and date/time
- arrival airport and date/time

Like `BookedFlights`, it is built from both `FlightBooked` and `FlightChanged`,
overwriting per `flightId` so the detail screen shows the latest state.

### Wireframe

A detail/edit screen opened from the list selection. It is populated from
`FlightDetailsView` for the selected `flightId`. Every field is editable except
`flightId`:

- airline
- flight number
- departure airport and date/time
- arrival airport and date/time
- (new information) reason for change (string, optional)

Submitting the form issues a `ChangeFlightCommand`.

### Command — ChangeFlightCommand

Carries the `flightId` and the complete set of new field values. Its handler
reads the current state of the flight from the event stream before deciding
whether to act. On success it emits a single `FlightChanged` event; if any rule
fails, no event is produced.

### Validation rules

The same rules that apply when booking a flight apply here.

**Rule: the flight must already exist**

> **Given** no flight exists for the submitted `flightId`
> **When** a `ChangeFlightCommand` is received
> **Then** the command is rejected and no `FlightChanged` event is emitted.

**Rule: airport codes must be valid**

> **Given** a `ChangeFlightCommand` whose departure or arrival airport code is
> not a 3-letter, letters-only code
> **When** the command is handled
> **Then** the command is rejected and no `FlightChanged` event is emitted.

**Rule: arrival must be after departure**

> **Given** a `ChangeFlightCommand` whose arrival date/time is not after its
> departure date/time
> **When** the command is handled
> **Then** the command is rejected and no `FlightChanged` event is emitted.

**Rule: a valid change produces an event**

> **Given** a flight exists for the submitted `flightId`
> **And** all submitted field values satisfy the rules above
> **When** a `ChangeFlightCommand` is received
> **Then** a `FlightChanged` event is emitted carrying the full new snapshot of
> the flight's details including the reason for the change (if any).

---

## How the slices connect

- **Navigation:** selecting a flight in slice 1 hands its `flightId` to slice 2's
  detail screen. This is a UI navigation, not a data dependency.
- **Event feedback:** the `FlightChanged` event emitted in slice 2 flows back
  into the `BookedFlights` read model in slice 1 (and into `FlightDetailsView`).
  Editing a flight therefore updates the list automatically on the next read.

This feedback loop is invisible within either slice on its own: the event
produced at the end of slice 2 is the same event consumed by the read model in
slice 1.
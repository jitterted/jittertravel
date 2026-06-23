# Plan: Roll UTC + zone storage out to Trains, then Flights

Continuation of `docs/UtcDatetimeStoragePlan.md`. Hotels already ship the full pattern
(`ZonedTimestamp` events, boundary zone resolution, read-time upcaster, `<time>` rendering, audit).
This slice extends it to **trains first, then flights**, following the hotel template exactly.

## Decisions (confirmed 2026-06-23)

1. **Flight endpoint zone source: `AirportZoneResolver` + `CommonZone` fallback** — not a new
   AeroDataBox timezone fetch. One source of truth, already used by the audit and required by the
   upcaster anyway (legacy flight events stored neither offset nor city — only the IATA code), and
   it handles manual / no-API entry identically. `AeroDataBoxClient` keeps returning wall-clock
   `LocalDateTime`; the boundary resolves each airport's zone via `AirportZoneResolver`.
2. **Order: trains first, then flights.** Train stations carry city/country, so they resolve via the
   existing `LocationZoneResolver` exactly like hotels — no API, no airport-code wrinkle. Trains
   prove the per-endpoint *two independent zones* pattern; flights then reuse it with the airport
   resolver.

## The new wrinkle vs. hotels: two independent zones per item

Hotel check-in/out share one zone. A train (and a flight) resolves **departure and arrival zones
independently** (e.g. Frankfurt→Paris). So each handler resolves twice and each form offers **two**
`CommonZone` pickers (departure, arrival), each required only when its own endpoint fails to resolve.

## Train rollout steps (mirror the hotel files)

1. **Domain** — `TrainBooked`, `TrainChanged`: `departureDateTime`/`arrivalDateTime`
   `LocalDateTime → ZonedTimestamp`. `BookTrainCommand`, `ChangeTrainCommand`: same field change;
   move the validations onto instants/entry-zone (departure-in-future = `utc().isAfter(now)`;
   arrival-after-departure compares `utc()`), per `BookHotelCommand`.
2. **Application** — `BookTrainHandler`/`ChangeTrainHandler` take a `LocationZoneResolver`, resolve
   departure & arrival zones independently (explicit `CommonZone` wins per endpoint, else
   `resolve(city, country)`), build the two `ZonedTimestamp`s. `BookedTrainsProjector` stores
   `ZonedTimestamp`s in `BookedTrainView`; drop the pre-formatted display strings.
3. **View** — `BookedTrainView` holds `ZonedTimestamp departure/arrival`; `relevantUntil()` returns
   `departure.utc()` (removes the documented server-zone STOPGAP). Sort by `departure.utc()`.
4. **Web** — `Book/ChangeTrainRequest` gain `departureZone`/`arrivalZone` (`CommonZone` names);
   `events()` passes a real `LocationZoneResolver`. `BookedTrainsRenderer` renders each time via
   `ZonedTimeTag.render(view.departure(), "EEE, MMM d, h:mm a")`. Train Thymeleaf forms gain two
   `CommonZone` selectors; prefill edit form from `utc.atEntryZone()` and preselect the zone.
5. **Upcaster** — add `"TrainBooked"`/`"TrainChanged"` cases to `EventPayloadUpcaster`: resolve the
   departure zone from `departureStation.{city,country}` and the arrival zone from
   `arrivalStation.{city,country}` **independently** via `LocationZoneResolver`. (Hotel already
   injects it.)
6. **Other consumers** — update any train datetime use in `ItineraryProjector`,
   `CalendarViewBuilder`, `ScheduleGapProjector` to bucket by `utc.atZone(entryZone).toLocalDate()`.
7. **Tests** — command/handler/projector/renderer unit tests to `ZonedTimestamp`; golden +
   `CommandExportImportRoundTripTest` for old scalar train payloads upcasting and new shape round-
   tripping; `TimeViewTest`/projector future-filter tests on instants. Run "All Tests" +
   `./mvnw test -Pjs-tests`.

## Flight rollout steps (after trains; same shape, airport zone source)

- `Flight{Booked,Changed}`, `Book/ChangeFlightCommand`: departure/arrival `→ ZonedTimestamp`.
- Boundary resolves each airport's zone via **`AirportZoneResolver`**; per-endpoint `CommonZone`
  picker required when the code isn't curated. `AeroDataBoxClient.parseLocal` stays as-is.
- `EventPayloadUpcaster` flight cases use `AirportZoneResolver` (inject it into the upcaster) keyed
  off `departureAirport`/`arrivalAirport`.
- Flight view/renderer/forms/projectors mirror the train changes; golden + round-trip for old
  scalar flight payloads.

## Backward-compat guardrails (unchanged from master plan)
- Upcaster is idempotent (object passes through, scalar rewrites). New rows untouched.
- `/admin/zone-audit` already green (2026-06-21) over locations **and** airport codes, so no stored
  train/flight location is unresolvable — the no-default upcaster can't throw on real data.
- Old export files stay importable (round-trip test); no rewrite of stored rows.

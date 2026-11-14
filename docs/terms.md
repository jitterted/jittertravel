# Ubiquitous Language — Glossary

> This glossary defines the shared vocabulary for this project. When naming things in code,
> tests, templates or docs, use these terms consistently.
>
> Last reviewed: 2026-10-10
>
> Paths are relative to `src/main/java/dev/ted/jittertravel/` unless they start with `docs/`,
> `src/` or a root-level file name. Line numbers are where the type is declared and drift as the
> code moves; the type name is the stable reference.
>
> This document **renames nothing**. The Commentary section lists drift; each fix is its own
> decision.

## Agreed: flight vocabulary

Settled with Ted, 2026-10-13. These are the words to use from now on; where the code still says
something else, the Commentary entry for it is marked **Decided** and the rename is its own change.

| Term | Means | Example |
|------|-------|---------|
| **Leg** | One scheduled ride: a flight or a train ride. A **flight leg** is one flight number, from the airport Ted boards at to the airport he gets off at. A stop on the way is not recorded — this bends the industry meaning ("segments are what you buy, legs are what you fly", under which a one-stop flight is two legs), knowingly, because Ted almost never flies one. Not *segment*, *hop*, *movement* or *member*. | UA 1234 SFO→DEN→BOS, landing at DEN, is one leg SFO→BOS. VIA 45 Ottawa→Toronto is a train leg. |
| **Stop** | A landing on the way within one leg, same flight number. Not recorded. | DEN, above. |
| **Connection** | Changing from one leg to the next within a journey: **24 hours or less** on the ground. **Layover** is the time spent at it. | SFO→ORD lands 14:10, ORD→YOW leaves 16:05. Landing at ORD 21:00 and leaving 07:30 next day is still a connection. |
| **Journey** | One direction of an itinerary: consecutive legs joined by connections. **More than 24 hours on the ground ends a journey**, whether or not a hotel is booked — a rule based on hotels would merge the whole itinerary into one journey whenever the hotel is not booked yet, which is most of the months before a trip. Worked out from the legs, never stored (`domain/Journey`). No longer a synonym for itinerary. | MD7LKB: SFO→ORD→YOW (outbound journey), YYZ→SFO (return journey). |
| **Itinerary** | The legs under **one confirmation code**. A codeshare leg sold under the same code belongs to it; a leg bought separately, even with the same airline, is another itinerary. | SFO→ORD, ORD→YOW, YYZ→SFO on MD7LKB, the last operated by Air Canada. |
| **Confirmation code** | The 6-character code the airline gives a booking; how Ted refers to it with the airline. Also called *record locator*, *PNR* or *booking reference*. Not *booking code*. | MD7LKB |
| **Codeshare** | A leg sold by one airline (the **marketing carrier**) and flown by another (the **operating carrier**). | YYZ→SFO sold by United on MD7LKB, flown by Air Canada. |
| **Trip** | Ted's **whole time away from home**: every itinerary, hotel, train and transfer from leaving home to getting back. Not an itinerary, and not a train ride. Still has no type. | 2026-11-02 to 11-09: fly to Ottawa, hotel, train to Toronto, hotel, fly home. |
| **Home city** | Any configured home city (`HomeCities`); all of them count as one place. | SFO, SJC and OAK. |

**Itinerary types**, worked out from its journeys and never stored. A type describes **one
itinerary**, so it says how Ted *booked* the travel: legs under separate confirmation codes are
separate itineraries, each with its own type, and never add up to an open jaw or a round trip.

| Type | Rule | Example |
|------|------|---------|
| **One-way** | One journey. | SFO→ORD→DEN |
| **Round trip** | Two journeys; the second starts where the first ended and ends where the first started — or at any home city, when the first started at one. | SFO→BER, BER→SFO; SFO→YOW, YOW→OAK; SJC→YYZ, YYZ→SFO |
| **Open jaw** | Two journeys; the second starts somewhere other than where the first ended. | SFO→YOW, YYZ→SFO |
| **Multi-city** | Three or more journeys, whether or not the last returns to the start. | LHR→CDG, CDG→AMS, AMS→BCN; SFO→LHR, LHR→AMS, AMS→SFO |

**Changing an itinerary:**

| Term | Means |
|------|-------|
| **Schedule change** | The airline changed the itinerary: times, flight numbers or legs. |
| **Rebooking** | Ted changed the itinerary himself, under the same confirmation code. A different fact from a schedule change, so a different event (`FlightItineraryRebooked`). Ted says which on the change page ("Who changed it?"), because the email cannot. |
| **Dropped** (leg) | Removed from its itinerary by a schedule change or a rebooking. A later email that lists it again books it again. Close to the conference meaning: removed as a consequence, not by Ted cancelling it directly. |
| **Cancelled** (leg) | Ted cancelled that leg on its own. A later email listing it again is refused. |

## Nouns

### Flight

One scheduled air hop: airline, flight number, two airports, a departure and an arrival
`ZonedTimestamp`.

| Type | File | Role |
|------|------|------|
| `FlightId` | `domain/FlightId.java:5` | Identity |
| `FlightBooked` | `domain/FlightBooked.java:3` | Event |
| `FlightChanged` | `domain/FlightChanged.java:11` | Event — full snapshot plus `reason` |
| `FlightCancelled` | `domain/FlightCancelled.java:22` | Event — `reason`, `cancelledOn`, `cause` |
| `FlightCancellationCause` | `domain/FlightCancellationCause.java:11` | `MANUAL`, `AIRLINE_SCHEDULE_CHANGE` |
| `BookFlightContext` / `ChangeFlightContext` / `CancelFlightContext` | `domain/` | Decision contexts |
| `BookedFlightView` | `application/BookedFlightView.java:24` | `/booked-flights` row; keeps cancelled flights |
| `FlightDetailsView` | `application/FlightDetailsView.java:14` | Prefills the change form |
| `FlightItineraryEntry` | `application/FlightItineraryEntry.java:8` | A card on the `/itinerary` **page** — not a flight itinerary (see Homonyms) |
| `EntryDetails.Flight` / `PublicFlight` | `application/EntryDetails.java:84,155` | Calendar entry halves |
| `StayingFlight` | `application/StayingFlight.java:10` | A flight that survives cancelling its itinerary |
| `FlightLookupResult` | `infrastructure/FlightLookupResult.java:5` | AeroDataBox result (where it is called a *segment*) |

- **Aliases**: *leg* (inside an itinerary), *segment* (AeroDataBox only), *journey* (comments).
- **Related to**: Flight itinerary, Leg, Airport.

### Flight itinerary

The flights one airline confirmation code booked together. It groups existing `FlightBooked`s by
id and never replaces them; a hand-booked flight never joins one. Both models are live.

| Type | File | Role |
|------|------|------|
| `FlightItineraryId` | `domain/FlightItineraryId.java:10` | Identity |
| `FlightItineraryBooked` | `domain/FlightItineraryBooked.java:16` | Event — `airline`, `confirmationCode`, `flightIds` |
| `FlightItineraryChanged` | `domain/FlightItineraryChanged.java:19` | Event — membership snapshot after the airline's schedule change |
| `FlightItineraryRebooked` | `domain/FlightItineraryRebooked.java:20` | Event — membership snapshot after Ted's own rebooking |
| `ItineraryChangeKind` | `domain/ItineraryChangeKind.java:8` | `SCHEDULE_CHANGE` or `REBOOKING`: which of the two facts a change records |
| `FlightItineraryCancelled` | `domain/FlightItineraryCancelled.java:17` | Event |
| `FlightItineraryHasDeparted` / `NotFound` / `Unchanged` / `Refused` | `domain/` | Refusals |
| `ChangeFlightItineraryContext` / `CancelFlightItineraryContext` | `domain/` | Decision contexts (`liveMembers` vs `liveLegs`) |
| `ItineraryChangePlan` | `domain/ItineraryChangePlan.java:38` | Diff for a change of either kind: `UNCHANGED`, `CHANGED`, `ADDED`, `REMOVED` (the page says "Moved" and "Dropped") |
| `PastedItinerary` | `application/PastedItinerary.java:13` | Parser output, before zones |
| `ItineraryEvaluation` | `application/ItineraryEvaluation.java:22` | Preview outcome |
| `ItineraryPreview` | `web/ItineraryPreview.java:32` | Preview rows |
| `BookedItineraryView` | `application/BookedItineraryView.java:16` | Read model |
| `FlightTrip` / `FlightTrips` | `application/FlightTrip.java:21` | The itinerary seen from one list row (`legNumber`, `legCount`, `hue`) |
| `TripOnTheBooks` | `application/TripOnTheBooks.java:33` | The itinerary folded for a decision |

- **Aliases**: *trip* (`FlightTrip`, `TripOnTheBooks`), *booking* (tests).
  Flagged below.
- **Related to**: Flight, Leg, Confirmation code (no type).

### Leg

One timetabled movement — a flight within an itinerary, or a train ride — as seen by the overlap
rule or a list. The most heavily represented concept in the codebase.

| Type | File | Role |
|------|------|------|
| `ItineraryLeg` | `domain/ItineraryLeg.java:7` | Command input, also fold state; same fields as `FlightBooked` |
| `ScheduledLeg` | `domain/ScheduledLeg.java:10` | `id`, `departure`, `arrival` — the slice the overlap rule needs |
| `ScheduledLegs` / `LiveScheduledLegs` | `domain/ScheduledLegs.java:14`, `application/LiveScheduledLegs.java:34` | Collection and its stream fold |
| `ScheduledLegId` | `domain/ScheduledLegId.java:15` | Sealed: `Flight`, `Train` (no transfer) |
| `TravelLeg` | `application/TravelLeg.java:17` | Sealed: `Flight`, `Train`, `Transfer`, with a label |
| `PastedItinerary.Leg` | `application/PastedItinerary.java:20` | Wall-clock times plus `number` |
| `ItineraryEvaluation.Leg` | `application/ItineraryEvaluation.java:54` | Preview |
| `ItineraryChangePlan.LegDiff` | `domain/ItineraryChangePlan.java:24` | One diff row |
| `ItineraryPreview.Row` | `web/ItineraryPreview.java:52` | Rendered preview row |
| `ScheduleProblem.OverlappingLeg` | `application/ScheduleProblem.java:177` | One side of an overlap problem |
| `ScheduleTimeline.Movement` | `application/ScheduleTimeline.java` | The timeline's own name for it |
| `FamilyNotificationMessages.LegView` | `infrastructure/FamilyNotificationMessages.java:182` | Email template value |

- **Aliases**: *flight* (preview: "Overlaps flight N"), *journey* (44 comments), *trip*
  (`TrainTripId`), *segment*, *movement*, *hop* (tests). Flagged below.
- **Why so many**: each exists for a different stage (parse → preview → decide → display); the
  count is defensible, the word drift around them is not.

### Hotel booking

A reserved stay at one hotel, check-in to check-out, `TENTATIVE` or `FINAL`.

| Type | File | Role |
|------|------|------|
| `HotelBookingId` | `domain/HotelBookingId.java:5` | Identity |
| `HotelBooked` / `HotelChanged` | `domain/HotelBooked.java:8`, `domain/HotelChanged.java:9` | Events; `HotelChanged` is a full snapshot |
| `HotelBookingCancelled` | `domain/HotelBookingCancelled.java:12` | Event — `reason` |
| `BookingIntent` | `domain/BookingIntent.java:3` | `TENTATIVE`, `FINAL` |
| `BookedHotelView` | `application/BookedHotelView.java:29` | List row; keeps a cancelled tombstone |
| `HotelDetailsView` | `application/HotelDetailsView.java:20` | Prefills the change form |
| `TentativeHotelBookingView` | `application/TentativeHotelBookingView.java:7` | Built by two projectors nothing reads (below) |
| `HotelItineraryEntry` | `application/HotelItineraryEntry.java:10` | `/itinerary` card |
| `OngoingStay` | `application/OngoingStay.java:11` | A stay spanning an itinerary day |
| `HotelDayRole` | `application/HotelDayRole.java:3` | `CHECK_IN`, `CHECK_OUT` |
| `EntryDetails.Lodging` / `PublicLodging` | `application/EntryDetails.java:124,179` | Calendar entry halves |
| `ScheduleContext.Stay`, `ScheduleTimeline.Stay` | `application/` | Schedule's view |
| `ScheduleProblem.DuplicateHotel` holding `DuplicateStay` | `application/ScheduleProblem.java:85,107` | Problem |

- **Aliases**: *stay*, *lodging* (`EntryKind.LODGING`), *bed* (`Marker.BED`,
  `nightWithoutABed`), *reservation*, *accommodation*. Flagged below.

### Train trip

One booked train ride from a departure station to an arrival station.

| Type | File | Role |
|------|------|------|
| `TrainTripId` | `domain/TrainTripId.java:5` | Identity |
| `TrainBooked` / `TrainChanged` / `TrainCancelled` | `domain/` | Events; the id field is `tripId` |
| `BookedTrainView`, `TrainDetailsView`, `TrainItineraryEntry`, `TrainDayRole` | `application/` | Read models |
| `ScheduledLegId.Train`, `TravelLeg.Train` | | The same ride, called a leg |

- **Aliases**: *train*, *leg*, *trip*.

### Station

Where a train departs or arrives: a name, the city it stands in, a country.

| Type | File | Role |
|------|------|------|
| `TrainStationAddress` | `domain/TrainStationAddress.java:13` | Value — not an `Address` (no street, region, postcode) |
| `TrainStations` | `domain/TrainStations.java:18` | Both ends, checked together |
| `TrainEndpoints` | `application/TrainEndpoints.java:30` | Zone resolution and validation for both ends |
| `StationZone`, `TrainZones` | `application/` | Zone resolution |
| `UnresolvedStationZone` | `domain/UnresolvedStationZone.java:13` | `COUNTRY_MISSING`, `COUNTRY_UNRECOGNISED` |

### Ground transfer

A short unbooked trip (taxi, subway, a lift) that fills a gap in the travel record. **Planned**,
not booked, because nothing is reserved.

| Type | File | Role |
|------|------|------|
| `GroundTransferId` | `domain/GroundTransferId.java:5` | Identity |
| `GroundTransferPlanned` | `domain/GroundTransferPlanned.java:29` | Event — `origin`/`destination`, `departsAt`/`arrivesAt`, `mode` (private) |
| `GroundTransferCancelled` | `domain/GroundTransferCancelled.java:16` | Event — no reason, by decision |
| `GroundTransferDetailsView`, `GroundTransferItineraryEntry`, `TravelLeg.Transfer` | `application/` | Read models |

### Transfer endpoint

One end of a ground transfer: an airport, hotel, station, gathering, conference or private event.

| Type | File | Role |
|------|------|------|
| `TransferEndpoint` | `application/TransferEndpoint.java:20` | The endpoint itself |
| `TransferEnd` | `application/TransferEnd.java:23` | Which end of which thing it is (`FLIGHT_ARRIVAL`, `HOTEL_CHECK_OUT`, `GATHERING_START`, …) |
| `TransferEndpointWindow` | `domain/TransferEndpointWindow.java:15` | Inside the command only |
| `TransferEndpointRow` | `application/TransferEndpointRow.java:44` | Read-model row |
| `TransferEndpointOption` / `Label` | `application/` | Form option and its text |
| `GroundTransferEndpointChoices` / `Options` / `Resolver` | `application/` | Form lists; token → endpoint |
| `SameTransferEndpoints`, `UnknownTransferEndpoint` | `application/` | Refusals |

### Address, Place, Location

Three related ideas that are easy to blur:

- **Address** — a postal address as typed. `Address` (`domain/Address.java:23`): `street`, `city`,
  `region`, `postalCode`, `country`, `locationForMatching`.
- **Place** — the city the schedule believes Ted is in, compared case-insensitively.
  `Place` (`domain/Place.java:48`), derived from `Address.locationForMatching`.
- **Entered location** — a building plus its city, checked on the write path.
  `EnteredLocation` (`domain/EnteredLocation.java:61`), `EnteredCountry`, `LocationRole`
  (`DEPARTURE`, `ARRIVAL`, `STAY`, `VENUE`), `LocationField` (`VENUE_NAME`, `CITY`, `COUNTRY`,
  `REGION`), `InvalidLocationEntry`, `InvalidEnteredLocation`.

Supporting: `Country`/`Countries`, `Subdivision`/`Subdivisions`, `CityLabel`, `CityCountry`
(audit), `HomeCities`, `LocationZoneResolver`, `AirportCode`, `AirportZoneResolver`,
`AirportCityResolver`.

- **Aliases**: the same `Address` field is `address` (hotel), `venueAddress` (conference) and
  `location` (gathering, private event). Flagged below.

### Matching location

The city the schedule uses for a private event when it differs from the venue's city (a dinner
in Centennial during a conference in Lone Tree). Stored as `Address.locationForMatching`; changed
by `PrivateEventMatchingLocationChanged`. Displayed nowhere, by decision.

### Conference

A public event of one or more whole days that Ted may attend or speak at. Public in full except
its submission pipeline.

| Type | File | Role |
|------|------|------|
| `ConferenceId` | `domain/ConferenceId.java:5` | Identity |
| `ConferencePlanned` | `domain/ConferencePlanned.java:36` | Event — `name`, `venueAddress`, `startDate`, `endDate`, `format`, `infoUrl` |
| `ConferenceDatesChanged` | `domain/` | Event — one fact |
| `ConferenceCancelled` | `domain/` | Event — **the organisers** cancelled; no command writes it yet |
| `ConferenceFormat` | `domain/ConferenceFormat.java:14` | `CALL_FOR_PAPERS`, `ACCEPTANCE_REQUIRED`, `OPEN_SPACE` |
| `ConferenceState` | `application/ConferenceState.java:28` | Folded state of both axes (attendance and speaking) |
| `ConferenceView` / `ConferenceDetailView` | `application/` | `/conferences` row and detail page |
| `ConferenceDashboard`, `DashboardGroup`, `DashboardRow`, `DashboardSection`, `DroppedView` | `application/` | The owner's triage page |
| `ConferenceStanding` / `ConferenceNews` | `application/` | Fold that drives family email |
| `LiveConferencePlan` | `application/LiveConferencePlan.java:25` | Decision fold: still planned and not dropped |
| `EntryDetails.Conference` / `PublicConference` | `application/EntryDetails.java:46,215` | Calendar entry halves |

`DashboardGroup`: `CFP_CLOSES_SOON`, `INVITED`, `CFP_DATE_UNKNOWN`, `DECIDE`, `NOTHING_TO_SUBMIT`,
`WAITING_TO_HEAR`, `GOING`, `DROPPED`.

### CFP

A conference's call for papers: when it closes and the page talks are submitted on. Private.

`CfpOpened` (`domain/CfpOpened.java:49`), `OpenCfpCommand`, `OpenCfpContext`, `CfpDeadlineView`,
`CfpDeadlineMissing`, `ConferenceHasNoCfp`, `web/CfpDeadlineSource` (iCal).

### Talk (the speaking axis)

Where Ted's talk stands at one conference. Keyed by conference — there is no talk entity and no
invitation entity.

| Type | File | Role |
|------|------|------|
| `TalkSubmitted`, `TalkAccepted`, `TalkRejected`, `TalkWithdrawn`, `InvitedToSpeak` | `domain/` | Events |
| `SubmitTalkCommand` … `InviteToSpeakCommand` | `domain/` | Commands |
| `TalkPipelineContext` | `domain/TalkPipelineContext.java:29` | Shared decision context for all five |
| `SpeakingStatus` | `domain/SpeakingStatus.java:31` | `NOT_SPEAKING`, `SUBMITTED`, `ACCEPTED`, `REJECTED`, `WITHDRAWN`, `INVITED` |
| `TalkOutcome` | `web/TalkOutcome.java:15` | Form value: same list minus `NOT_SPEAKING` |
| `TalkTracking` | `application/TalkTracking.java:43` | Service; `record` dispatches all five |
| `NoTalkToDecide`, `NoTalkToWithdraw`, `TalkAlreadyAccepted` | `domain/` | Refusals |

- **Aliases**: *pipeline*, *tracking*, *submission*, *speaking axis*.

### Attendance

Whether Ted is going to a conference. Derived from events and never stored as a value.

| Type | File | Role |
|------|------|------|
| `ConferenceAttendanceConfirmed` / `Declined` | `domain/` | Events |
| `AttendanceCommitment` | `application/AttendanceCommitment.java:24` | `WATCHING`, `GOING`, `NOT_GOING` — the **collapsed** value that may be published |
| `AttendanceBasis` | `domain/AttendanceBasis.java:25` | `SPEAKING_ACCEPTED`, `SPEAKING_INVITED`, `TICKET_PURCHASED` — **why** he is going; never published |

- **UI words**: `WATCHING` renders as the **"Maybe" chip**; `GOING` renders nothing;
  `NOT_GOING` is "Not going" and the dashboard group `DROPPED`.

### Speaking (published)

A collapsed yes/no: committed to the conference **and** speaking. On a conference it is derived
(`ConferenceState.speaking()`); on a gathering it is a stored boolean on `GatheringPlanned`.
Rendered as the **"A Ted Talk" badge** on `/calendar`, **"Speaking"** on `/itinerary` and
**"Speaker"** on `/conferences` and the detail page — the last two choices are explained in
their renderers' comments.

### Gathering

A public event of a few hours on one day (a meetup, a user group). Public in full.

`GatheringId`, `GatheringPlanned` (`title`, `location`, `startsAt`, `endsAt`, `speaking`),
`GatheringChanged` (full snapshot), `PlanGatheringCommand` with `GatheringPlanningContext`,
`ChangeGatheringCommand` with `ChangeGatheringContext`, `PlannedGatheringView`,
`GatheringDetailsView`, `GatheringItineraryEntry`, `EntryDetails.Gathering`/`PublicGathering`.

- **Aliases**: *meetup* (97 test hits).

### Private event

A private social occasion (a dinner with friends). Shown to strangers only as `Busy`.

`PrivateEventId`, `PrivateEventPlanned`, `PrivateEventCancelled`,
`PrivateEventMatchingLocationChanged`, `PlannedPrivateEventView`, `PrivateEventDetailsView`,
`PrivateEventMatchingLocationView`, `EntryDetails.PrivateEvent`, `EntryDetails.Busy`.

- **Aliases**: *dinner* (199 test hits), *Busy* (the public lane).

### Calendar entry

One dated thing drawn on `/calendar`.

| Type | File | Role |
|------|------|------|
| `CalendarEntry` | `application/CalendarEntry.java:28` | Core fields plus one `EntryDetails` |
| `EntryDetails` | `application/EntryDetails.java:18` | Sealed kind-specific half: owner records and `Publishable` records |
| `EntryKind` | `application/EntryKind.java:7` | `CONFERENCE`, `GATHERING`, `PRIVATE_EVENT`, `FLIGHT`, `TRAIN`, `GROUND_TRANSFER`, `LODGING` — also the lane order |
| `SubtitleLine` | `application/SubtitleLine.java:17` | `Text`, `At`, `Range`, `FixedRange` |
| `CalendarAggregator` | `application/CalendarAggregator.java:6` | Owner **and family** calendar |
| `PublicCalendarProjector` | `application/` | The anonymous calendar — the redaction allow-list |

### Itinerary (the page)

The `/itinerary` day-by-day list. Not to be confused with a flight itinerary.

`ItineraryDay` (`ongoingStay`, `nightWithoutABed`, `atHome`), `ItineraryEntry` (sealed; seven
`*ItineraryEntry` records), `ItineraryProjector`, `web/ItineraryRenderer`. Day roles:
`FlightDayRole` and `TrainDayRole` (`DEPARTURE`, `ARRIVAL`), `HotelDayRole` (`CHECK_IN`,
`CHECK_OUT`).

### Schedule problem

Something wrong with where Ted will be. Recomputed from events on every batch; it has no id, only
a content-derived `ProblemKey`.

| Variant | Means | List heading | Band text |
|---------|-------|--------------|-----------|
| `MissingTravel` | Ted is in one city, then another, with no travel between | Missing Travel | "No travel —" |
| `MissingHotel` | A night away with no bed | Missing Hotel | "No hotel —" |
| `DuplicateHotel` | Two bookings for one night | Duplicate Hotels | — |
| `OverlappingTravel` | Two legs at once | — | "Two trips at once" |
| `SchedulingConflict` | Two occupancies overlap (gatherings **or private events**) | Scheduling Conflicts | "Clash —" |
| `DifferentCityConflict` | An occupancy in a city Ted is not in | City Conflicts | "City clash —" |

Supporting: `ScheduleContext` (the facts a problem came from: `Conference`, `Gathering`,
`PrivateEvent`, `Travel`, `Stay`), `ScheduleTimeline` (where Ted is, in order: `Movement`,
`Stay`, `Occupancy`, `CityNight`), `ScheduleGapProjector`, and in `web/`: `ProblemBand`,
`ContextBand`, `ProblemFix`, `ProblemKey`, `FixOrigin`, `ProblemView`, `ProblemContextView`.

### Away day

A day Ted is out of town. `ScheduleTimeline.awayDays()` → `Set<LocalDate>`; drawn as the
turquoise **away band**. No type.

### Home city

Where Ted lives. `HomeCities` (`primaryCity`, `includes`, `sameLocation`); `ItineraryDay.atHome`.

### Zone

Five separate ideas share the word:

| Idea | Types |
|------|-------|
| A moment with its local zone | `ZonedTimestamp` (`utc`, `zone`); `atEntryZone` = the entry's own zone |
| The zones a form offers | `CommonZone` |
| Working out an endpoint's zone | `VenueZone`, `StationZone`, `FlightEndpointZone`, `TrainZones`, `LocationZoneResolver`, `AirportZoneResolver` |
| Which zone times are shown in | `DisplayZone` (`ENTRY`, `BROWSER`) — UI "Event time" / "My time"; `ZoneDisplay`; `ViewerZonePolicy`; `web/ZoneToggle` |
| Which day is "today" | `ViewerTodayZone` |

### Time filter

`TimeView` (`FUTURE`, `ALL`), rendered "Upcoming" / "All" on `?filter=`; `TemporalView` is the
interface a list row implements (`relevantUntil()`); `web/TimeFilterToggle`.

### Audience

Who is viewing: **owner** (Ted), **family**, or **anonymous**. No type: Spring role strings
`"OWNER"`/`"FAMILY"`, booleans `isOwner`/`isPublicUser`, and `web/Page.NavAudience`. Docs and
comments also say *public user*, *stranger*, *visitor*.

### Event (domain)

A past-tense fact, immutable once stored (R2, R2a). Marker `domain/Event.java:3`. Wrapped with its
log envelope as `infrastructure/StoredEvent`.

### Command

A pure function from a decision context to events or a refusal (R3). `domain/DomainCommand`;
29 `*Command` records, all in `domain`.

Also, not domain commands: `NotifyFamilyCommand` (application), and `ClearDifferentCityConflict`
and `CompleteOneOffTask` (web) — internal-action records written to the command log.

### Decision context

The facts a command decides on, folded from the event stream. `domain/DecisionContext`;
26 records, normally named `<CommandStem>Context`. Exceptions: `PlanGatheringCommand` uses
`GatheringPlanningContext`; `BookFlightItineraryCommand` uses `BookFlightContext`; five talk
commands share `TalkPipelineContext` (deliberate).

- **Docs alias**: *decider* (`docs/DuplicatedEventHandlingPlan.md:222`) — no type.

### Command log

The `command_log` table: every command, written before its events, with a status (`PENDING`,
`FAILED_DOMAIN`, …, bare strings). Called *WAL* (`PostgresPersister`), *write-ahead log* (R4),
*command log* (`/admin/commandlog`) and *Timeline* (`TimelineController`, `TimelineCommand`).
A **pending command** is a row with no outcome (`PendingCommandStartupCheck`,
`PendingCommandsController`).

### Projector, read model, view

A projector folds events into a read model that serves one page (R8, R10, R12). Thirty
`*Projector` classes implement `infrastructure/EventStreamConsumer`; they hold `*View` records.
Composition a layer up is an **aggregator** — only `CalendarAggregator` carries the name,
though `ConferenceDashboard` does the same job.

### Reactor, Actuator

A reactor is an asynchronous consumer never replayed into (`infrastructure/EventReactor`). An
**actuator** is a stateful reactor that issues a command on a state
(`FamilyNotificationActuator`). A **translator** (stateless, republishes) exists in the docs
only.

### Upcaster, migration

An **upcaster** brings an old stored payload up to the current shape at read time; each step is a
**rung** (`EventUpcaster`, `EventPayloadUpcaster`, `ConferenceFormatUpcaster`,
`LocationCodesUpcaster`). A **migration** rewrites `event_log` rows once (`LegacyEventMigration`).

### Family notification

An email telling family about a trip or a conference decision, sent once per fact.
`FamilyNotified` (event), `NotifiedFact`, `NotifiedSubject`, `NotifyFamily`,
`NotifyFamilyCommand`, `ExternalAction`, `FamilyNotificationActuator`,
`FamilyNotificationMessages`, `FamilyMessage`, `EmailTemplates`.

### One-off task

A post-deploy chore declared in code, latched done by `OneOffTaskCompleted`. `OneOffTask`,
`OneOffTasks`, `OneOffTaskRegistry`, `OneOffTaskProjector`, `OneOffTaskView`,
`web/CompleteOneOffTask`.

## Verbs

### Book

Record a reservation Ted already made with a provider. Flights, flight itineraries, hotels,
trains.

| Expression | Where | Form |
|------------|-------|------|
| `BookFlightCommand` → `FlightBooked` | `/book-flight` | "Book Flight" |
| `BookFlightItineraryCommand` → n×`FlightBooked` + `FlightItineraryBooked` | `/book-flight/itinerary` | "Paste an Itinerary", "Book N flights" |
| `BookHotelCommand` → `HotelBooked` | `/book-hotel` | "Book Hotel" |
| `BookTrainCommand` → `TrainBooked` | `/book-train` | "Book Train Trip" (h1), "Book Train" (button) |
| `FlightBooking`, `FlightItineraryBooking`, `HotelBooking`, `TrainBooking` | `application/` | Services |

### Plan

Record something Ted intends to be at, where nothing is reserved. Conferences, gatherings,
private events, ground transfers.

| Expression | Where | Form |
|------------|-------|------|
| `PlanConferenceCommand` → `ConferencePlanned` | `/plan-conference` | "Plan Conference" |
| `PlanGatheringCommand` → `GatheringPlanned` | `/plan-gathering` | "Plan Gathering" |
| `PlanPrivateEventCommand` → `PrivateEventPlanned` | `/plan-private-event` | "Plan Private Event" |
| `PlanGroundTransferCommand` → `GroundTransferPlanned` | `/plan-ground-transfer` | "Plan Ground Transfer" |

### Change

Correct a recorded booking or plan.

| Expression | Event | Notes |
|------------|-------|-------|
| `ChangeFlightCommand` | `FlightChanged` | Edit by hand, `/booked-flights/{id}` |
| `ChangeFlightItineraryCommand` | `FlightChanged`/`FlightBooked`/`FlightCancelled` + `FlightItineraryChanged` or `FlightItineraryRebooked` | "Record schedule change" / "Record rebooking", `/booked-itineraries/{id}/change`. Re-pasting a booked code on Book no longer applies it (2026-11-14, `FlightItineraryRebookingPlan.md` D11) |
| `ChangeHotelCommand` | `HotelChanged` | `/booked-hotels/{id}` |
| `ChangeTrainCommand` | `TrainChanged` | `/booked-trains/{id}` |
| `ChangeGatheringCommand` | `GatheringChanged` | `/planned-gatherings/{id}` |
| `ChangeConferenceDatesCommand` | `ConferenceDatesChanged` | One fact |
| `ChangePrivateEventMatchingLocationCommand` | `PrivateEventMatchingLocationChanged` | One fact, "Match location" |

No change exists for a ground transfer.

### Cancel

Record that a booking or plan is off. Appends an event; destroys nothing (no typed word).

| Expression | Event | UI |
|------------|-------|----|
| `CancelFlightCommand` | `FlightCancelled(MANUAL)` | "Cancel this flight" |
| `CancelFlightItineraryCommand` | n×`FlightCancelled` + `FlightItineraryCancelled` | link "Cancel itinerary", page "Cancel Flight Itinerary" |
| `CancelHotelCommand` | `HotelBookingCancelled` | "Cancel this booking"; badge "Canceled" |
| `CancelTrainCommand` | `TrainCancelled` | "Cancel this trip" |
| `CancelGroundTransferCommand` | `GroundTransferCancelled` | "Cancel this transfer" |
| `CancelPrivateEventCommand` | `PrivateEventCancelled` | "Cancel this event" |
| — | `ConferenceCancelled` | The organisers cancelled; no command, route or UI |

### Open a CFP, submit, accept, reject, withdraw, invite

Record the talk's progress. Opening, accepting and rejecting are things the **organisers** did;
Ted records them. Submitting and withdrawing are Ted's own acts.

| Expression | Event | UI (at `/conferences/{id}/talk?outcome=`) |
|------------|-------|----|
| `OpenCfpCommand` | `CfpOpened` | `/conferences/{id}/cfp`, "CFP Deadline", "Save deadline" |
| `SubmitTalkCommand` | `TalkSubmitted` | "Submitted" |
| `AcceptTalkCommand` | `TalkAccepted` | "Accepted" |
| `RejectTalkCommand` | `TalkRejected` | "Rejected" |
| `WithdrawTalkCommand` | `TalkWithdrawn` | "Withdrawn" |
| `InviteToSpeakCommand` | `InvitedToSpeak` | Form only — no dashboard link |

### Confirm attendance, decline

| Expression | Event | UI |
|------------|-------|----|
| `ConfirmConferenceAttendanceCommand` | `ConferenceAttendanceConfirmed` with an `AttendanceBasis` | "Ticket Bought", "Invitation Accepted"; button "Confirm I'm going" |
| `DeclineConferenceCommand` | `ConferenceAttendanceDeclined` | "Decline" |

### Clear a city conflict

Tell the schedule a `DifferentCityConflict` is fine. `ClearDifferentCityConflict` (web) →
`DifferentCityConflictCleared`; `/clear-conflict`; "Clear this conflict".

### Fix a problem

Follow a link from a schedule problem to the form that resolves it. Navigation, not a command:
`ProblemFix` builds `…?problem=<key>&from=<origin>`.

### Notify family

`NotifyFamily` decides; `FamilyNotificationActuator` sends; `FamilyNotified` records it.

## Commentary

### Strengths

- **Event names are disciplined.** R2a is visible everywhere: `CfpOpened`, `InvitedToSpeak`,
  `TalkWithdrawn`, `PrivateEventMatchingLocationChanged`. No `*Recorded`/`*Updated` survives.
- **Book vs plan is a real distinction, held consistently.** "Book" means a provider holds a
  reservation; "plan" means nothing is reserved. Every command, event, route and button agrees.
- **Privacy is in the types.** `EntryDetails.Publishable`, `Busy`, and the split between
  `AttendanceCommitment` (publishable) and `AttendanceBasis` (never) make the redaction rules
  part of the vocabulary rather than a convention.
- **Commands and decision contexts are almost perfectly regular** — `<Verb><Noun>Command` with
  `<Verb><Noun>Context`, all in `domain`, with three exceptions listed under Decision context.
- **Problem keys and typed leg ids are named for what they are** — `ProblemKey` (a string that
  survives in a URL) and `TravelLeg` (an in-memory identity) were deliberately split from
  `ProblemRef`.

### Issues Found

#### "Itinerary" names a flight booking and a page

- **Type**: Homonym
- **Where**: `FlightItinerary*`, `ItineraryLeg`, `ItineraryChangePlan`, `PastedItinerary`,
  `ItineraryEvaluation` (a booking) vs `ItineraryDay`, `ItineraryEntry`, `ItineraryProjector`,
  `FixOrigin.ITINERARY`, `/itinerary` (the page). Worst case: `FlightItineraryEntry` is a flight's
  card on the page and has nothing to do with `FlightItineraryId`. `FlightCancelled`'s javadoc
  says "removed from … the itinerary", meaning the page.
- **Problem**: `ItineraryEvaluation` (paste flow) and `ItineraryEntry` (page) sit side by side
  in `application/` and read as one family.
- **Suggestion**: give one of them another name. The page is a day-by-day agenda; the booking
  is what the airline calls an itinerary — so the page is the one that can move more cheaply.
- **Decided (2026-10-13)**: an itinerary is the legs under one confirmation code (see Agreed:
  flight vocabulary). The page's name is still open.

#### "Trip" means four things

- **Type**: Homonym
- **Where**: a flight itinerary (`FlightTrip`, `TripOnTheBooks`); one train ride (`TrainTripId`, "Cancel this trip");
  a chained through-flight (`infrastructure/FlightLookupCandidates.java:36`); any leg
  (`LiveScheduledLegs.java:31`); and in tests, Ted's whole time away (`aTripWithNoReturnBooked…`).
- **Problem**: "Trip" is the word Ted would use for the *whole* time away — which is the one
  meaning with no type (see Missing terms).
- **Suggestion**: reserve "trip" for the time away from home; use "itinerary" or "train ride"
  for the others.
- **Decided (2026-10-13)**: trip = the whole time away from home. `FlightTrip` and
  `TripOnTheBooks` are itineraries and are now misnamed; `TrainTripId` and "Cancel this trip" are
  a train leg. The "Cancel trip" link on `/booked-flights` became **Cancel itinerary** on the
  itinerary's heading band (2026-10-14, `FlightItineraryRebookingPlan.md` D10).

#### "Event" means four things

- **Type**: Homonym
- **Where**: domain `Event`; `PrivateEvent*` and the button "Cancel this event";
  `ICalEvent`; `TimelineEvent`; and "venue event" for gatherings plus private events
  (`EventCalendarSubtitle`). `PrivateEventCancelled`'s javadoc uses two meanings in one sentence.
- **Problem**: unavoidable in an event-sourced app about events, but `PrivateEvent` makes it
  acute: `EventReference` sits beside `PrivateEventDetailsView`.
- **Suggestion**: none needed for the domain `Event`. Consider whether the private kind's
  noun could be something other than "event" (the plan doc and tests say "dinner").

#### The leg vocabulary drifts

- **Type**: Synonym
- **Where**: *leg* (`ScheduledLeg`, "leg N of M" on `/booked-flights`), *flight*
  ("Overlaps flight N", `web/ItineraryPreview.java:188`, against the domain's "Overlaps leg N",
  `domain/OverlapsAnotherItineraryLeg.java:12`), *journey* (44 comments, `sameJourney`),
  *segment* (AeroDataBox, selected by `selectLeg`), *movement* (`ScheduleTimeline`),
  *member* (`ChangeFlightItineraryContext.liveMembers` beside
  `CancelFlightItineraryContext.liveLegs`), *hop* (tests).
- **Suggestion**: "leg" everywhere outside the AeroDataBox adapter.
- **Decided (2026-10-13)**: "leg" for any scheduled ride, flight or train. *Journey* now means one
  direction of an itinerary, so the 44 comments that use it for a leg, and `sameJourney`, are
  misnamed.

#### The trip chip's "one-way" means "one leg"

- **Type**: Wrong term
- **Where**: `BookedFlightsRenderer.tripChip` labels an itinerary with one leg "one-way" and
  any other "leg N of M".
- **Problem**: one-way is an itinerary type and means one *journey* (see Agreed: flight
  vocabulary). SFO→ORD→YOW alone is one-way but shows "leg 1 of 2"; one leg of a round trip
  bought under its own code is not one-way in Ted's sense, but shows it.
- **Suggestion**: show the itinerary type worked out from its journeys, or drop the word and keep
  "leg N of M".
- **Decided (2026-10-24)**: the word is dropped. Rows read "leg N of M" where M is the legs still
  flying and is two or more; the only leg still flying carries no place at all, since one leg is
  no sequence. The itinerary type from its journeys stays a possible slice of its own.

#### Overlap, collision, conflict and clash

- **Type**: Synonym
- **Where**: domain comments say "collide" 16 times; tests have both `aConnectionIsNotACollision`
  and `aConnectionIsNotAConflict`; `SchedulingConflict` is "Clash —" on its band and keyed
  `clash`; `DifferentCityConflict` is "City Conflicts" in the list and "City clash" on its band.
- **Suggestion**: "overlap" for two legs; pick one of conflict/clash for the problems and use it
  in the type, the heading and the band.

#### Hotel, stay, lodging, bed, reservation

- **Type**: Synonym
- **Where**: `Hotel*` events; `EntryKind.LODGING`, `EntryDetails.Lodging`; `OngoingStay`,
  `ScheduleContext.Stay`, `LocationRole.STAY`; `DuplicateHotel` holding `List<DuplicateStay>`;
  `Marker.BED`, `nightWithoutABed`; "reservation" in `ScheduleProblem.java:81`.
- **Problem**: `DuplicateHotel` of `DuplicateStay` uses two words for one thing inside one type.
  And "staying" also means a flight that survives a cancel (`StayingFlight`).
- **Suggestion**: hotel booking for the record, stay for the nights it covers. Lodging only if
  a non-hotel kind ever arrives.

#### Hotel events disagree with each other and with trains

- **Type**: Inconsistent granularity
- **Where**: `HotelBooked`, `HotelChanged`, but `HotelBookingCancelled`; `TrainCancelled` and
  `GroundTransferCancelled` name the thing, not the booking. Id fields: `hotelBookingId`,
  `tripId` (not `trainTripId`), `groundTransferId`; `BookTrainRequest.trainTripId` vs
  `CancelTrainRequest.tripId`; `DuplicateStay.bookingId`. Time fields: `checkIn`/`checkOut`,
  `departureDateTime`/`arrivalDateTime`, `departsAt`/`arrivesAt`.
- **Problem**: event names are contract (R7), so this is fixed for stored events — but new
  events and requests can stop widening it.
- **Suggestion**: pick one id-field rule for new code (`<type>Id`).

#### Conference, gathering and private event name the same fields differently

- **Type**: Synonym
- **Where**: `ConferencePlanned` has `name`, `venueAddress`, `startDate`, `endDate`;
  `GatheringPlanned` and `PrivateEventPlanned` have `title`, `location`, `startsAt`, `endsAt`;
  `HotelBooked` has `address`.
- **Problem**: the date/instant split is real (whole days vs hours). The name/title and
  address/location splits are not.

#### Attendance: watching, Maybe, tentative, watch list

- **Type**: Synonym
- **Where**: `AttendanceCommitment.WATCHING`; every UI says "Maybe"; javadoc says
  "watch list"; tests say "watched"; the retired wire id `ConferenceTentativelyPlanned` and
  `Tentatively-Plan-Conference-slice.txt` say "tentative" — which also means a hotel
  `BookingIntent`.
- **Suggestion**: the UI word is the one Ted reads. Either the enum follows ("MAYBE") or the
  glossary records `WATCHING` ≡ the "Maybe" chip, which this file now does.

#### Decline, dropped, not going

- **Type**: Synonym (with a subset)
- **Where**: `DeclineConferenceCommand`; `AttendanceCommitment.NOT_GOING` ("Not going");
  `DashboardGroup.DROPPED`, `DroppedView`, `ConferenceState.dropped()`.
- **Problem**: "dropped" is wider than "declined" — it also covers the drop after a rejection on
  an `ACCEPTANCE_REQUIRED` conference. `ConferenceNews.Exit` keeps them apart; the dashboard
  does not.

#### "Accepted" names two facts

- **Type**: Homonym
- **Where**: talk link "Accepted" (`TalkAccepted`, the organisers' decision) beside attendance
  link "Invitation Accepted" (`ConferenceAttendanceConfirmed` with `SPEAKING_INVITED`, Ted's
  yes), `web/ConferenceActions.java:140`. And `AttendanceBasis.SPEAKING_ACCEPTED` means
  *talk* accepted.
- **Problem**: two adjacent links with the same past participle and different actors.

#### State, standing, status

- **Type**: Awkward names
- **Where**: `ConferenceState` (both axes), `ConferenceStanding` (the email fold),
  `SpeakingStatus` (talk axis). Each one's javadoc defines it as "where … stands".
- **Problem**: the names do not say which one you have.

#### Talk, pipeline, tracking

- **Type**: Synonym
- **Where**: `TalkPipelineContext`, `TalkTracking`, `RecordTalkController`, "speaking
  pipeline", "speaking axis", "submission status" (javadoc throughout); `TalkOutcome.SUBMITTED`
  calls submitting an outcome.

#### The speaking badge has three labels

- **Type**: Synonym — **deliberate**
- **Where**: "A Ted Talk" (`web/CalendarViewBuilder.java:574,579,606`), "Speaking"
  (`web/ItineraryRenderer.java:346`), "Speaker" (`web/ConferenceDetailRenderer.java:219`). Each
  renderer's comment explains its choice.
- **Problem**: only documentation drift: `CLAUDE.md` says gatherings show a "Speaking" badge on
  `/calendar`; the calendar renders "A Ted Talk" for both kinds.

#### Gap, problem and the projector's name

- **Type**: Awkward name
- **Where**: `ScheduleGapProjector` detects all six problem kinds; tests say "gap" for
  `MissingTravel` only.
- **Suggestion**: the projector's name is narrower than its job.

#### The clear-conflict flow has five names

- **Type**: Synonym
- **Where**: `/clear-conflict`, `ClearConflictController`, `ClearConflictRequest`,
  `ClearDifferentCityConflict`, `DifferentCityConflictCleared`, plus projector state
  `ClearedConflict`. Only the last two say which conflict.

#### `SchedulingConflict` is described too narrowly

- **Type**: Awkward name (stale)
- **Where**: javadoc says "Two gatherings whose instants overlap"; its side type is
  `ConflictingGathering` (`application/ScheduleProblem.java:115,134`). Private events are
  included (`ScheduleGapProjector.java:439-446`).

#### "View" means four things

- **Type**: Homonym
- **Where**: read-model records (`BookedFlightView`); `TemporalView` (an interface for "has an
  end in time", implemented by `ScheduleProblem`); `TimeView` (a filter); `ProblemView`
  (list/calendar layout); `DroppedView` (show/hide).
- **Suggestion**: keep `*View` for read models; the toggles are filters or layouts.

#### Zone names that read like values

- **Type**: Awkward names
- **Where**: `VenueZone`, `StationZone`, `FlightEndpointZone` are resolvers with `resolve()`,
  while the domain's are `LocationZoneResolver`, `AirportZoneResolver`. `DisplayZone` and
  `ZoneDisplay` are mirror names for different things. `VenueZone` also resolves hotels,
  though its javadoc says gatherings only.

#### "Lane", "band", "chip", "badge", "context"

- **Type**: Homonyms
- **Where**: *lane* is a calendar row in `EntryKind` order and a `ProblemBand.Lane`
  (`BED`, `DUPLICATE`, `TRAVEL`, `CLASH`), and a swimlane in the event-model docs. *Band* is the
  away band, `ProblemBand`/`ContextBand`, and an empty lane row. *Chip* and *badge* swap freely
  in comments and CSS (`entry-maybe-badge` is the "Maybe" chip). *Context* is
  `ScheduleContext`, `ProblemContextView`, and the 26 decision contexts.

#### Command log, WAL, timeline

- **Type**: Synonym
- **Where**: `PostgresPersister` ("WAL"), R4 ("write-ahead log"), `/admin/commandlog`,
  `TimelineController`, `TimelineCommand`/`Entry`/`Event`. And `ScheduleTimeline` is an
  unrelated "timeline".

#### Handler, handle

- **Type**: Homonym
- **Where**: `*Handler.handle(request)` builds a command and touches no state (`BookFlightHandler`,
  `ChangeTrainHandler`, …; `HotelHandler` does two); `EventStreamConsumer.handle(Stream)` folds
  events. `docs/ApplicationLayerNamingPlan.md:50-53` already says "Handler names the wrong class"
  and recommends `*CommandFactory` (open, undecided).
- **Note**: that plan's inventory is stale — it omits `CancelFlight`, `CancelFlightItinerary`,
  `FlightItineraryBooking` and `NotifyFamily`.

#### Reactor, actuator, processor, translator, subscriber

- **Type**: Synonym
- **Where**: the interface is `EventReactor`; its javadoc says "the Actuator/Processor
  pattern"; `EventStore` says `subscribe`/`subscribeAsync`; `docs/FamilyEmailNotificationsPlan.md`
  still says "Translator" for what is now an Actuator. "Notify" also names subscriber dispatch
  (`EventStore.notifySynchronousSubscribers`, "persist before notify").

#### Technical words in domain comments

- **Type**: Technical leak (comments only — `DomainIsPureTest` keeps the code clean)
- **Where**: "Jackson binds a stored payload" (`domain/Address.java:18`), "read-time upcaster"
  (`domain/ConferencePlanned.java:14`), `ScheduleGapProjector` by name
  (`domain/CancelTrainCommand.java:11`), "store's envelope" (`domain/FamilyNotified.java:15`),
  "read from the details projection" (`domain/ChangeGatheringContext.java:7`).
- **Note**: these explain *why* the domain is shaped as it is, and several are the trap-warnings
  this project values. Flagged for awareness, not removal.

#### Form-shaped names in application

- **Type**: Technical leak
- **Where**: `GroundTransferEndpointChoices` (12 lists "only so the form can label the two
  optgroups"), `TransferEndpointOption.token`, `TrainEndpoints(departurePick, arrivalPick)`;
  35 `application` classes import `web` (`*Request`, `ClearDifferentCityConflict`).
- **Note**: the import direction is an architecture question more than a language one.

#### Missing terms

- **Type**: Missing term
- **Where**:
  - **Trip (time away)** — "Two trips at once", `wentQuiet`, "trip ended" (`ScheduleTimeline`),
    the year overview's blocks, and tests all assume one. No type.
  - **Audience / viewer** — role strings and booleans; only `web/Page.NavAudience`.
  - **Away day** — `Set<LocalDate>`.
  - **Confirmation code** — a plain `String`, though the docs treat it as a private booking
    reference.
  - **Schedule change** — the UI noun ("Airline schedule change") is a free-text `REASON`
    constant that also separates the two meanings of `FlightChanged`.
  - **Hand-entered flight** — a flight with no itinerary; comments and tests only.
  - **Invitation** — only the event `InvitedToSpeak`.
  - **Command status** — bare strings (`PENDING`, `FAILED_DOMAIN`, …).
  - **Decider**, **translator**, **slice**, **stream** — in docs only.
  - **Journey**, **connection**, **itinerary type**, **rebooking** — defined in Agreed: flight
    vocabulary (2026-10-13); no type yet. *Trip*, *confirmation code* and *schedule change*
    above are defined there too, and still have none.

#### Dead or stale terms

- **Type**: Awkward name (legacy)
- **Where**:
  - `ConferenceSpansMultipleDays` (`domain/`) — referenced nowhere.
  - `TentativeHotelBookingProjector` (by id) and `TentativeHotelBookingsProjector` (a list) —
    both registered in `EventSourcingConfig`, read by no page or service; one letter apart.
  - `ConferenceCancelled` — handled by every projector, written by nothing in `src/main`.
  - `docs/lodging-event-model.md` — `HotelBookingFinalized`, "Finalize Hotel Booking": the code
    has only `BookingIntent.FINAL`.
  - `Tentatively-Plan-Conference-slice.txt` — "waitlisted" (dropped) and a moved doc path.
  - `UnknownTransferEndpoint` javadoc — "neither airport: nor hotel:"; six prefixes exist now.
  - `ProblemView` javadoc — "four-column card LIST"; six headings now.
  - `ProblemFix` — still says "reference" after the `ProblemRef` → `ProblemKey` rename.
  - `HotelBookingCancelled` javadoc — "every read model drops the booking entirely";
    `BookedHotelView` keeps a tombstone.
  - UI spells "Canceled"; events spell "Cancelled".

#### Test vocabulary that differs from production

- **Type**: Synonym
- **Where**:
  - "trip" for a flight itinerary (`FlightTripsTest`, `BookedFlightsRendererTest`, which coins
    "untripped") and for a train request (`aTrip()`, `submitTrip()`).
  - "booking" for a flight itinerary (`FlightTripsTest:41,65`).
  - "meetup" (97) and "dinner" (199) for gathering and private event.
  - "watched" for `WATCHING`; "speaker" for the badge; "attending" for `TICKET_PURCHASED`.
  - "stay" for a hotel request (`aStay()`); `DuplicateStays` for `DuplicateHotel`.
  - "gap" for `MissingTravel`; "clash" for conflicts; "rerouted" for `CHANGED`/"Moved";
    "resurrect" for `LegCancelledEarlier`; "dropped by the airline" for `REMOVED`.
  - One `CommandExecutor` spy has two names: `RecordingCommandExecutor` (10 private copies) and
    `SpyCommandExecutor`. (`StubExecutor` in `LiveScheduledLegsTest` only returns programmed
    events, so it is a stub and correctly named.)
  - Contract tests call events "messages" (`MessageContract`), while production's only
    "message" is an email.
  - "anonymous" in 36 test names; production says `isPublicUser`.

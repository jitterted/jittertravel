# Active read models: one "is it active?" per kind

**Status:** designed 2026-10-18 (Ted), nothing built. Decisions D1–D3 are Ted's; D4 and D5 are
recommendations awaiting his word. The D4 fragments, file by file, are in an HTML doc Ted reviewed
the same day (`ActiveReadModelsChangeServices`).

**The word is "active", not "live"** (Ted, 2026-10-18): *live* already means other things in this
app (a live leg in `LiveScheduledLegs`, a live plan in `LiveConferencePlan`, "live state" in R3), so
the new read models and their query are `Active*Projector` / `isActive(id)`. The two existing `Live*`
classes keep their names until this plan replaces or retires them.

## Where this came from

`Cleanup_Tasks.md` carried an item saying the four Change services (`ChangeFlight`, `ChangeHotel`,
`ChangeTrain`, `ChangeGathering`) were wrong to decide existence from the details projector that
prefills their edit form, and should fold existence from the stream the way every `Cancel*` service
does. That rested on R3's "fold first, read model second" ranking, which Ted withdrew on 2026-10-18:

> if there's already a synchronous read model, then that is obviously the cheapest way to answer a
> question as it's already been computed! any retrieval from the event log has a cost, so it can't
> possibly be cheaper.

The concern that survives is **coupling**, not cost:

> using a read model that was not built to be general-purpose and built for some other specific
> need should not be used to make a decision. [...] coupling within a "slice" is natural, as
> coupling across slices should be only through events.

By that rule the four Change services were never wrong: the details projector and the Change command
are one slice. What is missing is a read model **built on purpose** to answer "is this item still
active?", so that no decision has to borrow a page's projector and no service has to fold the
question itself. R3 now says this (rewritten the same day).

## What exists today

Fifteen decision contexts carry an `exists` boolean. The fact behind every one of them is the same:
**booked or planned, and not since cancelled.** It is computed in two shapes.

| Kind | Made active by | Ended by | Services deciding it | Source today |
|---|---|---|---|---|
| Flight | `FlightBooked` (`FlightChanged` keeps it) | `FlightCancelled` | `CancelFlight` | fold |
| | | | `ChangeFlight` | `FlightDetailsViewProjector` |
| Flight itinerary | `FlightItineraryBooked` (`FlightItineraryChanged` restates members) | `FlightItineraryCancelled` | `CancelFlightItinerary` | fold, and it needs the member flight ids too |
| Hotel | `HotelBooked`, `HotelChanged` | `HotelBookingCancelled` | `CancelHotel` | fold |
| | | | `ChangeHotel` | `HotelDetailsViewProjector` |
| Train | `TrainBooked` (`TrainChanged` keeps it) | `TrainCancelled` | `CancelTrain` | fold |
| | | | `ChangeTrain` | `TrainDetailsViewProjector` |
| Ground transfer | `GroundTransferPlanned` | `GroundTransferCancelled` | `CancelGroundTransfer` | fold |
| Private event | `PrivateEventPlanned` | `PrivateEventCancelled` | `CancelPrivateEvent`, `ChangePrivateEventMatchingLocation` | fold, twice |
| Gathering | `GatheringPlanned` | nothing (no cancel event yet) | `ChangeGathering` | `GatheringDetailsViewProjector` |
| Conference | `ConferencePlanned` | `ConferenceCancelled`, `ConferenceAttendanceDeclined` (a `TalkRejected` does **not** end it) | `DeclineConference`, `ConfirmConferenceAttendance`, `OpenCfp`, `ChangeConferenceDates`, `TalkTracking` | `LiveConferencePlan`, one shared fold returning the active `ConferencePlanned` |

Fourteen folds, each a pass over `EventStore.findAll()` on every command. `LiveConferencePlan` is the
one place the duplication was already collapsed, and it is the model for the rest.

**A claim in the code that this plan retires.** `CancelTrain`'s javadoc says the stream is read
because "only the stream cannot be one batch stale". A synchronous subscriber is notified inside the
same `synchronized (transactionLock)` block that appends to the in-memory list, before `append`
returns. It cannot be staler than the list. The argument only ever applied to asynchronous reactors,
which R1 already covers.

## Decisions

**D1 — The read model answers "active", not "editable" (Ted, 2026-10-18).** Active is
time-independent and precomputes cleanly. Editability depends on `now` and differs by command:
`ChangeTrain` refuses a departure in the past, `CancelHotel` deliberately dropped its check-in gate,
and a cancel recorded late is still a cancel. Those rules stay in the commands, with the `now` they
receive from the boundary. Example: a train booked, then departed yesterday, is **active**;
`ChangeTrain` alone refuses it, as `DepartureNotInFuture`. Then `TrainCancelled` arrives and it is
**not active** for everyone.

**D2 — One read model per kind (Ted, 2026-10-18).** `ActiveTrainsProjector`,
`ActiveHotelsProjector`, and so on, each a small projector beside its kind's other read models, not
one class typed over seven ids. Seven classes, seven bean methods, seven unit tests.

**D3 — A conference Ted declined is not active (Ted, 2026-10-18).** This is what
`LiveConferencePlan` does today, so no behaviour changes: `ConferenceCancelled` and
`ConferenceAttendanceDeclined` both end it, and a `TalkRejected` does not (the talk pipeline is where
that happened, and a later move on it is still Ted's to record). The conference read model keeps the
active `ConferencePlanned`, not a boolean, because `OpenCfp` and `ChangeConferenceDates` read its
format and zone.

**D4 — Every existence decision reads its kind's active read model, Change and Cancel alike
(recommendation).** The four Change services move off their details projectors too. They were not
wrong to read them, but once a read model exists whose whole job is this question, a details
projector that also answers it is a second definition of "active" per kind, and the two can drift:
a details projector that one day keeps a cancelled booking visible on its page (to let Ted correct
a typo on it, say) would make `ChangeHotel` accept a change to a cancelled stay, with no test going
red. The details projectors go back to serving their forms only. One source per kind is the point
of building it. Per service the change is one field, one constructor parameter, one line in the
method, one bean parameter; the controllers, the forms, the `@WebMvcTest`s and the details
projectors are untouched (the services are `@MockitoBean`s in those tests, so the `findById` stubs
there feed the GET form only). None of the four has a service-tier test today, so each gains one:
the projector fed real events, the service built on it, `*NotFound` asserted after the cancel event.

**D5 — Prerequisite: projectors must be resettable (recommendation, and the open "production half"
item in `Cleanup_Tasks.md`).** `EventStore.reload()` refills the in-memory list and deliberately
rebuilds no read model. `AbstractTestcontainerIntegrationTest` calls it before every test, which is
how the `Cancel*` folds stay isolated today. A decision read from a projector gets no such reset, and
**`FlightItineraryBookingIntegrationTest` runs the real `CancelFlightItinerary` in a shared Spring
context**, so an `ActiveItinerariesProjector` there would carry state from whichever test ran
before. Per CLAUDE.md's isolation rule the fix is in production code, not the fixture:
`EventStreamConsumer` gains `reset()` (no default, so each of the 30 consumers says what clearing
means, usually one `clear()`), and `reload()` then resets and replays every synchronous subscriber
after refilling the list. That is also the fix for `/admin/database` truncate and restore leaving
read models stale in a running app, which `Cleanup_Tasks.md` already says wants answering together
with this. **It is a slice of its own and goes first.**

## Shape of one

```java
public class ActiveTrainsProjector implements EventStreamConsumer {
    private final Set<TrainTripId> active = ConcurrentHashMap.newKeySet();

    @Override public void handle(Stream<StoredEvent> eventStream) {
        eventStream.forEach(stored -> {
            switch (stored.payload()) {
                case TrainBooked e -> active.add(e.tripId());
                case TrainCancelled e -> active.remove(e.tripId());
                default -> { }
            }
        });
    }

    @Override public void reset() { active.clear(); }

    public boolean isActive(TrainTripId tripId) { return active.contains(tripId); }
}
```

- Keeps the `*Projector` suffix so `ProjectorsDependOnEventsAloneTest` scans it (R12: it reads
  events and nothing else, in particular never a details projector).
- Registered in `EventSourcingConfig` through `ProjectorBootstrapper.register`, like every other.
- Lives in `application` beside the kind's other read models.
- A service takes it as a constructor dependency and builds its context from `isActive(id)`.
  `ApplicationServicesUseCommandExecutorTest` is untouched: it bans `EventStore`, not a projector.
- The conference one holds `Map<ConferenceId, ConferencePlanned>` and exposes
  `Optional<ConferencePlanned> active(ConferenceId)`; the itinerary one holds the member ids.
- The `exists` booleans on the fifteen decision contexts keep their names for now; renaming them
  `*Active` is a domain-wide rename that can ride along later or not at all.

## Slices, in order

0. **Resettable projectors** (D5). `reset()` on `EventStreamConsumer`, implemented by all 30;
   `reload()` resets and replays synchronous subscribers. Tests: `EventStoreTest` gains a case that a
   reload rebuilds a subscriber from the durable log (mutation: drop the reset, drop the replay); the
   `@BeforeEach` guard in the integration base class can then assert a projector is empty too, closing
   the "projectors, in tests too" note in `Cleanup_Tasks.md`.
1. **Trains, end to end.** `ActiveTrainsProjector`; `CancelTrain` and `ChangeTrain` read it; the
   fold and the `TrainDetailsViewProjector` dependency go. Proves the shape on the kind with both a
   Cancel and a Change. Rewrite `CancelTrain`'s javadoc and the Backlog's "deliberately unlike its
   sibling" line in the same change.
2. **Flights, itineraries, hotels, ground transfers, private events, gatherings.** Same shape; the
   itinerary one carries members.
3. **Conferences.** `ActiveConferencesProjector` replaces the `LiveConferencePlan` fold; its five
   callers switch. `TalkTracking` folds other facts in the same pass today, so check it still has a
   reason to pass the stream at all afterwards.

Each slice: a projector unit test (planned makes it active, cancelled ends it, a change keeps it, an
unknown id is not active), the service tests re-pointed from `RecordingCommandExecutor` fixtures to
a projector fed the same events, and the kind's `*CancellationPropagationTest` counting one more
consumer where it enumerates them. Mutation-verify every removal branch before the commit; run
`-Ppit-spring` narrowed to the touched classes before the push.

## What this does not do

- **LSN-seen on read-model results and conditional appends** stay open in
  `CommandConsistencyEventStore.md`; the active read models are synchronous and R1 allows them today.
- **`GroundTransferEndpointResolver`'s window read** is a different question (a window, not
  existence) and keeps its own item in `Cleanup_Tasks.md`.
- **Nothing becomes editable or uneditable.** D1: every time rule stays where it is.
- **No event changes, no schema bump, backup stays v3.** Read models only.

## Cost, measured

Seven projectors of about 25 lines, seven bean methods, seven unit tests; fourteen folds and four
details-projector dependencies deleted across about fifteen services. Roughly line-neutral. The win
is one definition of "active" per kind, and an existence decision that is a set lookup instead of a
pass over the whole log.

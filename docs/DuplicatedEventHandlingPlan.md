# Duplicated Event Handling — conferences first, then the general case

**Status: plan only, nothing built** (2026-09-13). Written after the pre-push review of `6eb097b`
found the conference state machine being folded five times on the read side, and after one of those
copies (the itinerary) had been three events short for months without anything failing.

The guard that shipped the same day is `ConferenceLifecyclePropagationTest`. This plan is about
whether to go further than a guard, and how.

---

## 1. The problem, measured

### 1.1 Read side: five copies of one dispatch

Each of these writes its own switch over the same nine events and hands each one to
`ConferenceProgress`:

| Projector | Arms | What "dropped" does there |
|---|---|---|
| `ConferenceProjector` | 9 (+ `CfpOpened`) | keeps the row at `NOT_GOING`, behind `?dropped=show` |
| `ConferenceCalendarProjector` | 9 | removes the entry |
| `PublicCalendarProjector` | 9 | removes the entry (declined via `forget`, rejection via `dropped()`) |
| `ItineraryProjector` | 9 | removes the days (since 2026-09-09; before that, 3 arms) |
| `ScheduleGapProjector` | 9 | removes the occupancy |

**What is already shared** is the *rules*: `ConferenceProgress` decides that an acceptance commits
attendance, that a rejection drops an `ACCEPTANCE_REQUIRED` conference, that an invitation commits
nothing. Its own javadoc says so, and says the switches were deliberately left per-projector.

**What is not shared** is everything around the rules, written five times:

- which event maps to which transition (`TalkSubmitted` → `submitted`, …);
- that an event for a conference never planned, or already cancelled, is ignored;
- that `ConferencePlanned` starts from `ConferenceProgress.planned(format)`;
- that `ConferenceCancelled` bypasses the state machine entirely;
- the "hold the progress beside the view, rebuild the view when it moves" record: `Tracked`,
  `TrackedConference` (twice), and a bare `Map<ConferenceId, ConferenceProgress>` in the public
  projector.

The argument for leaving the switches per-projector was independence (H2). The itinerary shows the
cost: an arm is not a *view* decision you might want to make differently, it is a transcription of
the state machine. Nobody wants the itinerary to ignore `TalkRejected`, so there is nothing
independent about it. It was simply left out.

### 1.2 Write side: four more folds, and one of them disagrees

| Decision class | What it folds | Conference live when |
|---|---|---|
| `TalkTracking` | plan + speaking status | planned, not cancelled, not declined |
| `ConfirmConferenceAttendance` | existence | planned, not cancelled, not declined |
| `DeclineConference` | existence | planned, not cancelled, not declined |
| `OpenCfp` | the plan itself | planned, not cancelled, not declined |

These do not use `ConferenceProgress`, and **their "live" differs from the read side's
`dropped()`**: a rejection at an `ACCEPTANCE_REQUIRED` conference drops it from every calendar but
leaves it live for all four commands. `TalkTracking` also re-derives `SpeakingStatus` from events
with its own mapping instead of `ConferenceProgress`'s.

**I don't know whether that is deliberate.** It may be: the dashboard keeps a dropped row, and Ted
might still confirm attendance (a ticket bought after all). It may also be a gap nobody has walked
into yet. It is Open Question 1, and it should be answered before the write side is touched.

### 1.3 What guards it today

- **`ConferenceLifecyclePropagationTest`** (2026-09-13): one scenario per lifecycle event, run
  through all five read models, each arranged so its last event changes the outcome. Removing any
  arm from any projector fails a named case (mutation-verified in all five). It also scans
  `domain` for every `Event` record carrying a `ConferenceId` and fails until a new one has a
  scenario or a stated reason for having none (today `CfpOpened` and `DifferentCityConflictCleared`).
- **Its known blind spot:** `ScheduleGapProjector` only answers `dropped()`, so it can observe 3 of
  the 8 events. A forgotten arm for the other five changes nothing it reports. That is honest today
  and becomes a real gap the day the schedule reads commitment for anything.
- **`CalendarRemovalPropagationTest`** already covered cancel/decline for the two calendars. It
  overlaps the new test there, and it is kept because its question is different: whether the owner
  and public calendars *agree*.

---

## 2. Options for conferences

### A. Keep the switches and rely on the guard (the status quo as of today)

Cost: nothing more. The test catches omission wherever it is observable.

What it does not fix: the same transcription, 45 arms of it, and a new lifecycle event still means
five edits, which the guard only reminds you about. The five `Tracked` records keep drifting in
shape.

### B. One conference fold that reports a *change*, owned once by each projector — **recommended**

A small class in `application`, beside `ConferenceProgress`, that owns the dispatch and the
per-conference state, and answers each event with what happened to the conference in terms a view
can act on:

```java
/**
 * The conference state machine folded once. Not a projector: it builds no view and nothing reads
 * it but the one projector that owns this instance.
 */
public final class ConferenceLifecycle {

    private final Map<ConferenceId, ConferenceProgress> progress = new HashMap<>();

    public ConferenceChange apply(Event event) {
        return switch (event) {
            case ConferencePlanned e -> planned(e);
            case ConferenceCancelled e -> cancelled(e.conferenceId());
            case ConferenceAttendanceConfirmed e -> move(e.conferenceId(), p -> p.confirmed(e.basis()));
            case ConferenceAttendanceDeclined e -> move(e.conferenceId(), ConferenceProgress::declined);
            case TalkSubmitted e -> move(e.conferenceId(), ConferenceProgress::submitted);
            case TalkAccepted e -> move(e.conferenceId(), ConferenceProgress::accepted);
            case TalkRejected e -> move(e.conferenceId(), ConferenceProgress::rejected);
            case TalkWithdrawn e -> move(e.conferenceId(), ConferenceProgress::withdrawn);
            case InvitedToSpeak e -> move(e.conferenceId(), ConferenceProgress::invited);
            default -> new ConferenceChange.Unrelated();
        };
    }
    // planned / cancelled / move: an unknown or cancelled id answers Unrelated, in one place
}

public sealed interface ConferenceChange {
    record Planned(ConferencePlanned planned, ConferenceProgress progress) implements ConferenceChange {}
    record Moved(ConferenceId id, ConferenceProgress progress) implements ConferenceChange {}
    record Dropped(ConferenceId id, ConferenceProgress progress) implements ConferenceChange {}
    record Cancelled(ConferenceId id) implements ConferenceChange {}
    record Unrelated() implements ConferenceChange {}
}
```

Each projector keeps its own instance and its own view. Where it used to have nine arms, it has an
exhaustive switch over four changes (plus `Unrelated`), and the compiler makes it say what `Dropped`
means *for its view*: the dashboard keeps the row, the calendars remove it. Today that difference is
implicit in which projectors call `dropped()` and which call `remove`.

A multi-kind projector (itinerary, schedule, public calendar) routes to it from its `default` arm,
or calls it before its main switch. Pick one pattern and use it in all three.

**Why this does not break R12.** R12 bans a projector depending on *another read model*: sharing
its staleness, its half-folded state mid-batch, and making subscriber order load-bearing. None of
that applies here. Each projector owns a private instance fed the same stream it already handles,
so no state is shared between projectors and there is no ordering to get wrong. It is R12's own
remedy ("share the rule, not the projection") taken one step further, to the part of the rule that
is still being copied. The class name must not end in `Projector` or `Aggregator`, or
`ProjectorsDependOnEventsAloneTest` rejects it, and rightly: it is not one.

**Losses, named:**

- A projector's switch no longer lists the nine events it reacts to. A reader follows one
  indirection to see them.
- Five projectors now break together if `ConferenceLifecycle` is wrong. That is the point, since
  they are supposed to agree, but a bug there is five surfaces wrong at once, including the
  anonymous calendar. `ConferenceLifecycle` gets its own direct test, and the propagation test
  stays as the check that each projector acts on the change.
- The public projector's redaction reasoning currently sits at the arms ("`basis` is read only to
  answer speaking"). It moves to `ConferenceLifecycle` and to the `Moved` branch. The redaction
  tests (`PublicCalendarProjectorTest`, `CalendarRedactionSecurityTest`) must pass *unchanged*: if
  one needs editing, stop and ask.

### C. An abstract `ConferenceStateProjector<V>` base class — rejected

Template method: subclasses implement `onPlanned`, `onMoved`, `onDropped`. It fits
`ConferenceProjector` and `ConferenceCalendarProjector` and nothing else. The itinerary, schedule
and public calendar handle six or seven kinds and cannot extend a conference base class. Two of five
is not a solution, and inheritance is the coupling H2 warns about.

### D. Merge the conference projectors — rejected

One conference read model feeding several views. It breaks H2 outright, and it would put the
owner's conference view and the public one in one place. That is the deny-list shape
`PublicCalendarProjector` replaced: the allow-list only works because the public projector never
holds a private value.

### E. Derived internal events ("ConferenceCommitmentChanged") fed to subscribers — rejected

A pre-processor turns raw events into progress-change notifications that projectors subscribe to.
It makes subscriber order load-bearing, which is exactly R12's objection, and it goes against
`ConferenceProgress`'s stated design: *folds rather than extra events, so they replay and they
reverse*. If it were ever a *stored* event, it would also be a projection result written back into
the log.

### Recommendation

**B, in two steps, read side first.** Step 1 touches the five projectors and nothing on the write
path, and `ConferenceLifecyclePropagationTest` has to pass *without edits* before and after. That
is the evidence the refactor changed no behaviour. Step 2, the write side, waits for Open
Question 1.

---

## 3. The write side (step 2, after Open Question 1)

If Ted answers that a dropped conference should refuse commands: the four decision classes fold
`ConferenceLifecycle` over `eventsForDecision()` and refuse when the conference is `Dropped` or
`Cancelled`. That is a behaviour change, and it needs its own tests per command.

If Ted answers that it is deliberate: the four still fold `ConferenceLifecycle` for the
speaking status (`TalkTracking` stops re-deriving it), and "live" stays planned-and-not-cancelled-or-declined,
written **once** as a named method rather than four identical three-arm switches.

Either way this is R1 as written: decide from the authoritative stream, never from a projection.
`ConferenceLifecycle` is a fold, not a projection, so using it on the write path is allowed.

---

## 4. The general case: what to do when read models handle events the same way

Measured 2026-09-13: arms per event type across `src/main`:

| Events | Handlers |
|---|---|
| `HotelBooked`, `HotelChanged` | 11 each |
| `HotelBookingCancelled`, `TrainBooked`, `ConferencePlanned` | 10 |
| `FlightBooked`, `FlightChanged`, `TrainChanged`, `TrainCancelled`, `ConferenceCancelled`, `ConferenceAttendanceDeclined` | 9 |
| `GatheringPlanned`, `GatheringChanged`, `PrivateEventPlanned`, `PrivateEventCancelled` | 7 |
| `GroundTransferPlanned`, `GroundTransferCancelled`, talk events | 6 |

Most of that duplication is not a problem. The useful question is **what kind of sameness it is**,
because the kinds want opposite treatment:

### 4.1 A duplicated *rule*: extract it (conferences, today the only case)

**Test: would it be a bug if two copies disagreed?** If yes, it is one rule written N times, not N
views, and it belongs in one place: a domain rule (`Place.of`), a fold helper (`ConferenceProgress`),
or a change-reporting fold (option B). State machines are the obvious case. A derived value compared
across surfaces (a place, a zone, "is this day away") is the other.

Candidates to watch: a hotel's `BookingIntent` if it ever gains transitions; an "undo cancel" slice,
which would turn every `*Cancelled` into the first half of a state machine.

### 4.2 A duplicated *lifecycle shape* with different views: keep it, guard it

`HotelBooked → put`, `HotelChanged → put`, `HotelBookingCancelled → remove`, eleven times, each
building a different view. That is H2 working as intended. The views should evolve independently,
and a base class or shared dispatch would couple them to save three lines each.

The risk is the conference one again: an arm left out. The project's answer is a propagation test
per entity (hotel, train, ground transfer, private event, now conferences). Their weakness is that
**they are hand-listed**. A new projector that handles `HotelBooked` and forgets the cancellation is
only caught if someone adds it to `HotelCancellationPropagationTest`.

**Option G1: make the propagation tests self-enumerating.** A source scan (the style of
`ProjectorsDependOnEventsAloneTest`) finds every `EventStreamConsumer` in `application` whose source
matches `case HotelBooked`, and fails if that class is not named in `HotelCancellationPropagationTest`.
That is the same forcing function `CalendarDayMenuTest` uses for `EntryKind`, and the one the new
conference test uses for events. It is cheap, it needs no production change, and the test itself
never needs editing, which is the property CLAUDE.md asks of such guards.
`CalendarRemovalPropagationTest`'s javadoc already records the source-scan alternative in
`Cleanup_Tasks.md`, so this generalizes an idea already filed.

**Option G2: seal `Event` and switch exhaustively** remains rejected (memory: "guard with
lifecycle-propagation scenario tests, NOT by sealing Event"). It would make every projector list
every event, which is the opposite of H2.

### 4.3 A duplicated *derivation*: share it at the right layer

`city() + ", " + country()` is built in 8 places (`CfpDeadlineSource`, `ItineraryRenderer`,
`ConferenceDetailRenderer`, `ConferencesRenderer`, `HotelCalendarProjector`,
`PublicCalendarProjector`, `ConferenceCalendarProjector`, `EventCalendarSubtitle`). It is
presentation, so per "Presentation formatting stays out of the domain" it belongs in a
presentation-layer collaborator, **not** as `Address.cityCountry()`. It is small, but it is the
same "would disagreement be a bug?" test: if `/calendar` and `/itinerary` ever format one venue two
ways, yes.

### 4.4 Identical arms *within* one projector: leave them

`case HotelBooked e -> put(toX(e))` beside `case HotelChanged e -> put(toX(e))`. The event records
are different types with the same fields, and a merged arm would need a common interface on the
events, which makes their shape part of the contract for a projector's convenience (R7). The
existing pattern, both arms calling one builder with the fields spelled out, is already the right
amount of sharing.

### 4.5 A heuristic to write down (if Ted agrees)

Proposed as **H9** in `EventSourcingRulesHeuristics.md`:

> **Share a rule, never a view.** When two read models handle the same events, ask whether it would
> be a bug for them to disagree. If yes, the handling is a rule: extract it to a domain rule or a
> fold that each projector owns privately. If no, the handling is part of each view: keep it
> per-projector (H2) and guard omission with a propagation test that enumerates its own subjects.

---

## 5. Steps, if approved

1. Write `ConferenceLifecycleTest` against the planned `ConferenceLifecycle` API: every event → the
   expected `ConferenceChange`, unknown/cancelled ids → `Unrelated`, a rejection at
   `ACCEPTANCE_REQUIRED` → `Dropped`.
2. Build `ConferenceLifecycle` and `ConferenceChange`.
3. Move one projector at a time, simplest first: `ScheduleGapProjector`, `ConferenceCalendarProjector`,
   `ItineraryProjector`, `ConferenceProjector`, `PublicCalendarProjector` last. After each, run
   `ConferenceLifecyclePropagationTest`, that projector's own test, and for the public projector the
   two redaction tests, **all unedited**.
4. Mutation-verify `ConferenceLifecycle`: drop an arm, swap two transitions, answer `Moved` for a
   drop. Each must fail a named case.
5. Update `ConferenceProgress`'s javadoc ("each projector still writes its own switch" stops being
   true) and the "Redaction" section of CLAUDE.md where it names where the `basis` is read.
6. Separately, and not blocked on this: G1 (self-enumerating propagation guards) and the §4.3
   formatting collaborator are each a `Cleanup_Tasks.md` item.

---

## 6. Open questions for Ted

1. **Should a conference dropped by a rejection refuse commands?** Today every calendar hides it
   and the four write-side decision classes still treat it as live (§1.2). Deliberate or a gap?
2. **B now, or A until a tenth conference event forces it?** The guard makes A safe against
   omission. B removes the transcription. My recommendation is B, because the one bug it would have
   prevented already happened.
3. **Adopt H9** (§4.5) as a written heuristic?
4. **G1**: worth making the hotel/train/ground-transfer/private-event propagation tests
   self-enumerating now, or file it?

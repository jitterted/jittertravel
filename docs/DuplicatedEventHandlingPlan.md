# Duplicated Event Handling — conferences first, then the general case

**Status: plan only, nothing built** (2026-09-13). Written after the pre-push review of `6eb097b`
found the conference state machine being folded five times across the read models, and after one of those
copies (the itinerary) had been three events short for months without anything failing.

The guard that shipped the same day is `ConferenceLifecyclePropagationTest`. This plan is about
whether to go further than a guard, and how.

---

## 1. The problem, measured

### 1.1 Read models: five copies of one dispatch (six since 2026-10-06)

> **Update 2026-10-06.** `TransferEndpointProjector` became the sixth reader when conference venues
> became ground-transfer endpoints (`archived/GroundTransferGatheringEndpointsPlan.md`). It is a
> **drop-only** observer like the schedule, and unlike the schedule it folds **only the two events
> that can drop a conference** (`ConferenceAttendanceDeclined`, `TalkRejected`): the other seven arms
> were written first and PIT reported every one as a surviving mutant, because no test can tell an
> arm that cannot change `dropped()` from its absence. Ted's rule, stated the same day: *do not fold
> events that have no bearing on the decision or the view*; a rule change in `ConferenceState` is
> caught by `ConferenceLifecyclePropagationTest`, which now walks six read models, not by a
> speculative fold. **That reopens the schedule's own nine-arm fold**, whose comment argues the
> opposite ("a partial fold would answer a later question wrongly") and whose six unobservable arms
> §1.3 already flags. Option B dissolves the question for both; under option A the schedule should
> probably drop to the same two arms. Either way, a sixth consumer that wants only "did it drop?" is
> the clearest case yet for `ConferenceChange`.

Each of these writes its own switch over the same nine events and hands each one to
`ConferenceState`:

| Projector | Arms | What "dropped" does there |
|---|---|---|
| `ConferenceProjector` | 9 (+ `CfpOpened`) | keeps the row at `NOT_GOING`, behind `?dropped=show` |
| `ConferenceCalendarProjector` | 9 | removes the entry |
| `PublicCalendarProjector` | 9 | removes the entry (declined via `forget`, rejection via `dropped()`) |
| `ItineraryProjector` | 9 | removes the days (since 2026-09-09; before that, 3 arms) |
| `ScheduleGapProjector` | 9 | removes the occupancy |

**What is already shared** is the *rules*: `ConferenceState` decides that an acceptance commits
attendance, that a rejection drops an `ACCEPTANCE_REQUIRED` conference, that an invitation commits
nothing. Its own javadoc says so, and says the switches were deliberately left per-projector.

**What is not shared** is everything around the rules, written five times:

- which event maps to which transition (`TalkSubmitted` → `submitted`, …);
- that an event for a conference never planned, or already cancelled, is ignored;
- that `ConferencePlanned` starts from `ConferenceState.planned(format)`;
- that `ConferenceCancelled` bypasses the state machine entirely;
- the "hold the progress beside the view, rebuild the view when it moves" record: `Tracked`,
  `TrackedConference` (twice), and a bare `Map<ConferenceId, ConferenceState>` in the public
  projector.

The argument for leaving the switches per-projector was independence (H2). The itinerary shows the
cost: an arm is not a *view* decision you might want to make differently, it is a transcription of
the state machine. Nobody wants the itinerary to ignore `TalkRejected`, so there is nothing
independent about it. It was simply left out.

### 1.2 Command decision contexts: four more folds, and one of them disagrees

| Decision class | What it folds | Conference live when |
|---|---|---|
| `TalkTracking` | plan + speaking status | planned, not cancelled, not declined |
| `ConfirmConferenceAttendance` | existence | planned, not cancelled, not declined |
| `DeclineConference` | existence | planned, not cancelled, not declined |
| `OpenCfp` | the plan itself | planned, not cancelled, not declined |

These do not use `ConferenceState`, and **their "live" differs from the read models'
`dropped()`**: a rejection at an `ACCEPTANCE_REQUIRED` conference drops it from every calendar but
leaves it live for all four commands. `TalkTracking` also re-derives `SpeakingStatus` from events
with its own mapping instead of `ConferenceState`'s.

**I don't know whether that is deliberate.** It may be: the dashboard keeps a dropped row, and Ted
might still confirm attendance (a ticket bought after all). It may also be a gap nobody has walked
into yet. It is Open Question 1, and it should be answered before the decision contexts are touched.

**Update 2026-10-08 — the table above is stale, and the question is answered.** The four folds
were consolidated into one, `LiveConferencePlan`, which `ChangeConferenceDates` also uses (five
callers), so the command decision contexts already state "live" once. **A second decision-context
fold this plan never counted (found 2026-10-09): `ConferenceStanding`**, built for `NotifyFamily`,
walks the same nine events through `ConferenceState` and keeps beside it the basis and *which*
event ended things, for the family email's sentence. So the state machine is folded **eight**
times, not seven; under the ConferenceLifecycle fold it would consume `ConferenceChange` like the
dashboard, and `Dropped` would need to carry its cause (or the reader keeps its two dropping arms). Its javadoc says in as many words that a
conference a rejection dropped *is* still live there — so it was deliberate — but it was deliberate
in isolation: `ConferenceActions` offers **no** move on any `NOT_GOING` row, with a comment claiming
the domain refuses every command against it (true for a decline, false for a rejection drop), so the
confirm `FamilyEmailNotificationsPlan.md` §4.4 calls "the reachable re-entry" was reachable only by
typing the URL. Ted's answer is in §6.

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

### B. The ConferenceLifecycle fold: one conference fold that reports a *change*, owned once by each projector — **recommended**

> Called **the ConferenceLifecycle fold** in discussion from 2026-10-09 (Ted: a letter is "a terrible
> way to refer to things"). The letters stay here only as list markers.

A small class in `application`, beside `ConferenceState`, that owns the dispatch and the
per-conference state, and answers each event with what happened to the conference in terms a view
can act on:

```java
/**
 * The conference state machine folded once. Not a projector: it builds no view and nothing reads
 * it but the one projector that owns this instance.
 */
public final class ConferenceLifecycle {

    private final Map<ConferenceId, ConferenceState> progress = new HashMap<>();

    public ConferenceChange apply(Event event) {
        return switch (event) {
            case ConferencePlanned e -> planned(e);
            case ConferenceCancelled e -> cancelled(e.conferenceId());
            case ConferenceAttendanceConfirmed e -> move(e.conferenceId(), p -> p.confirmed(e.basis()));
            case ConferenceAttendanceDeclined e -> move(e.conferenceId(), ConferenceState::declined);
            case TalkSubmitted e -> move(e.conferenceId(), ConferenceState::submitted);
            case TalkAccepted e -> move(e.conferenceId(), ConferenceState::accepted);
            case TalkRejected e -> move(e.conferenceId(), ConferenceState::rejected);
            case TalkWithdrawn e -> move(e.conferenceId(), ConferenceState::withdrawn);
            case InvitedToSpeak e -> move(e.conferenceId(), ConferenceState::invited);
            default -> new ConferenceChange.Unrelated();
        };
    }
    // planned / cancelled / move: an unknown or cancelled id answers Unrelated, in one place
}

public sealed interface ConferenceChange {
    record Planned(ConferencePlanned planned, ConferenceState progress) implements ConferenceChange {}
    record Moved(ConferenceId id, ConferenceState progress) implements ConferenceChange {}
    record Dropped(ConferenceId id, ConferenceState progress) implements ConferenceChange {}
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
`ConferenceState`'s stated design: *folds rather than extra events, so they replay and they
reverse*. If it were ever a *stored* event, it would also be a projection result written back into
the log.

### Recommendation

**B, in two steps, read models first.** Step 1 touches the five projectors and no command decision
context, and `ConferenceLifecyclePropagationTest` has to pass *without edits* before and after. That
is the evidence the refactor changed no behaviour. Step 2, the command decision contexts, waits for
Open Question 1.

> **Terminology (Ted, 2026-10-09):** this document said "write side" for step 2 until today. That is
> not specific enough. A conference command has two parts: the application service builds the
> command's **decision context** by folding `eventsForDecision()`, and the command's **decider**
> accepts or refuses on what that context says. Everything this plan changes is the first part, so
> it is called the command decision context throughout. "Read side" became "read models" at the
> same time.

---

## 3. The command decision contexts (step 2, after Open Question 1)

**Answered 2026-10-08 (Ted): a rejection drop is final, exactly like a decline.** So step 2 is a
behaviour change: `LiveConferencePlan` (the one fold the five commands share, see §1.2's update)
must forget the conference on a `TalkRejected` *when its format is `ACCEPTANCE_REQUIRED`* — which is
the rule `ConferenceState.rejected()` already holds for the read models. Teaching the fold the rule
by hand makes a second copy of it; folding `ConferenceLifecycle` there instead is Option B's step 2,
and it needs its own refusal test per command (confirm, decline, talk moves, CFP, dates).

Consequences settled with the answer:

- **`ConferenceActions`'s comment becomes true** ("the domain refuses every command against" a
  dropped row), and so does its "going after all means planning it again". Reword it from
  "declined" to "declined or dropped". No UI change.
- **The family plan's three-send row is unreachable** (`FamilyEmailNotificationsPlan.md` §4.4 and
  §8, `NotifyFamilyConferenceTest.acceptRejectThenATicketIsThreeSendsAndConfirmThenReconfirmIsOne`).
  With both exits terminal there is no `GOING → NOT_GOING → GOING` production can produce, so the
  row goes and the test is retired with the decision-context change, in the same change — the row's own
  precedent (it replaced an earlier unreachable example on 2026-09-10). The positive-first rule and
  the "going after all" wording are untouched; only the example dies.
- **Production has nothing to migrate**: the backup of 2026-10-08 holds no `ACCEPTANCE_REQUIRED`
  conference and one `TalkRejected` (J-Fall, a CFP conference, followed by an explicit decline).
  The auto-drop has never fired.
- **Why not "invitation only"** (keep `InvitedToSpeak` accepted after the drop, since a keynote
  after a rejection does not contradict the format the way a ticket does): offered and declined;
  simpler is one terminal rule, and an invitation after a drop is a new plan like any other
  re-entry.

Either way this is R1 as written: decide from the authoritative stream, never from a projection.
`ConferenceLifecycle` is a fold, not a projection, so building a decision context from it is allowed.

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
views, and it belongs in one place: a domain rule (`Place.of`), a fold helper (`ConferenceState`),
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
5. Update `ConferenceState`'s javadoc ("each projector still writes its own switch" stops being
   true) and the "Redaction" section of CLAUDE.md where it names where the `basis` is read.
6. Separately, and not blocked on this: G1 (self-enumerating propagation guards) and the §4.3
   formatting collaborator are each a `Cleanup_Tasks.md` item.

---

## 6. Open questions for Ted

1. **Should a conference dropped by a rejection refuse commands?** Today every calendar hides it
   and the four command decision contexts still treat it as live (§1.2). Deliberate or a gap?
   — **Decided 2026-10-08 (Ted): yes, refuse; the drop is final, like a decline.** Chosen over
   "invitation only" and "ticket or invitation" with the worked example of an accepted-then-pulled
   talk at an `ACCEPTANCE_REQUIRED` conference. Consequences in §3.
2. **B now, or A until a tenth conference event forces it?** The guard makes A safe against
   omission. B removes the transcription. My recommendation is B, because the one bug it would have
   prevented already happened.
3. **Adopt H9** (§4.5) as a written heuristic? — **Still pending (Ted, 2026-10-06):** asked again
   when R8b went into `EventSourcingRulesHeuristics.md` (fold only what bears; propagation tests,
   never sealing), and Ted chose to write R8b alone and decide H9 together with questions 1, 2 and 4.
   R8b and H9 answer different questions — which events *one* projector folds, versus what to extract
   when *several* handle the same events — so neither substitutes for the other.
4. **G1**: worth making the hotel/train/ground-transfer/private-event propagation tests
   self-enumerating now, or file it?

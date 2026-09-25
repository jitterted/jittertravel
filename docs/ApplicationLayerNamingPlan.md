# Application layer naming — one name per responsibility

> **Status: `open` — raised by Ted 2026-09-22, nothing decided, nothing built.** Prompted by
> `6dc16a4`, which renamed every controller's `applicationService` field after its type and so
> produced `cancelHotel.cancelHotel(...)` and `openCfp.openCfp(...)`. Ted: *"is 'cancelhotel' a
> command? a handler? a service? seems unclear and something to look closer at from a design point
> of view."* No earlier note on this existed anywhere in `docs/`, `CLAUDE.md` or
> `EventSourcingRulesHeuristics.md`.

## The problem

The write path has three jobs, and the names do not tell them apart:

| Job | What it does | Names today |
|---|---|---|
| **Command** (domain) | Given a decision context, decides which events happen, or refuses | `CancelHotelCommand`, `ChangeFlightCommand`, … — **consistent, and not in question** |
| **Request → command translation** (application) | Turns a form's request into a command, resolving zones on the way | `BookFlightHandler`, `ChangeTrainHandler`, `HotelHandler`, `PlanConferenceHandler`, … |
| **Application service** (application) | Folds the decision context from events, then hands command + context to `CommandExecutor` | **Two conventions** — see below |

Three separate confusions follow from that.

### 1. Application services follow two conventions

- **By area, a noun:** `FlightBooking.bookFlight`, `HotelBooking.bookHotel`,
  `TrainBooking.bookTrain`, `ConferencePlanning.planConference`, `GatheringPlanning.planGathering`
  (+ `clearConflict`), `GroundTransferPlanning`, `PrivateEventPlanning`, `TalkTracking.record`.
- **By action, a verb:** `CancelHotel`, `CancelTrain`, `CancelGroundTransfer`,
  `CancelPrivateEvent`, `ChangeFlight`, `ChangeHotel`, `ChangeTrain`, `ChangeGathering`,
  `ChangeConferenceDates`, `ChangePrivateEventMatchingLocation`, `ConfirmConferenceAttendance`,
  `DeclineConference`, `OpenCfp`.

The split is historical, not deliberate: the create path of each kind came first and got a noun,
and every later action got its own verb-named class. So one area is often split across both —
booking a flight is `FlightBooking.bookFlight`, changing it is a separate class `ChangeFlight`;
hotels have `HotelBooking`, `ChangeHotel` and `CancelHotel` side by side.

### 2. A verb-named service reads as a command

`CancelHotel` is one suffix away from `CancelHotelCommand`. Nothing in the name says which one
*decides*. A reader has to open the class to learn that `CancelHotel` only gathers the context and
that the rule lives in the command.

### 3. "Handler" names the wrong class

In CQRS/event-sourcing usage a **command handler** loads state, runs the command, and persists what
it produced — which is exactly what the application services here do. The ten `*Handler` classes do
something smaller: `handle(request)` returns a command and touches no state. Anyone arriving with
the usual meaning goes looking in the wrong class.

`HotelHandler` is the outlier inside the outlier: it serves two actions (`bookHotel`,
`changeHotel`), so it is already area-shaped while its siblings are action-shaped.

## Inventory (2026-09-22)

Application services — all take `CommandExecutor`:

| Service | Methods | Other collaborators | Translator it uses |
|---|---|---|---|
| `FlightBooking` | `bookFlight`, `isReadOnly` | `AirportZoneResolver`, `LiveScheduledLegs` | `BookFlightHandler` |
| `ChangeFlight` | `changeFlight`, `isReadOnly` | `FlightDetailsViewProjector`, `AirportZoneResolver`, `LiveScheduledLegs` | `ChangeFlightHandler` |
| `HotelBooking` | `bookHotel` | `LocationZoneResolver` | `HotelHandler` |
| `ChangeHotel` | `changeHotel` | `HotelDetailsViewProjector`, `LocationZoneResolver` | `HotelHandler` |
| `CancelHotel` | `cancelHotel` | — | — |
| `TrainBooking` | `bookTrain` | `LocationZoneResolver`, `LiveScheduledLegs` | `BookTrainHandler` |
| `ChangeTrain` | `changeTrain` | `TrainDetailsViewProjector`, `LocationZoneResolver`, `LiveScheduledLegs` | `ChangeTrainHandler` |
| `CancelTrain` | `cancelTrain` | — | — |
| `GatheringPlanning` | `planGathering`, `clearConflict` | `LocationZoneResolver` | `PlanGatheringHandler` |
| `ChangeGathering` | `changeGathering` | `GatheringDetailsViewProjector`, `LocationZoneResolver` | `ChangeGatheringHandler` |
| `ConferencePlanning` | `planConference`, `isReadOnly` | `LocationZoneResolver`, **`OpenCfp`** | `PlanConferenceHandler` |
| `ChangeConferenceDates` | `changeDates` | — | — |
| `ConfirmConferenceAttendance` | `confirmAttendance` | — | — |
| `DeclineConference` | `declineConference` | — | — |
| `OpenCfp` | `openCfp`, `isReadOnly` | — | — |
| `TalkTracking` | `record`, `isReadOnly` | — | — |
| `GroundTransferPlanning` | `planGroundTransfer` | `GroundTransferEndpointResolver` | `PlanGroundTransferHandler` |
| `CancelGroundTransfer` | `cancelGroundTransfer` | — | — |
| `PrivateEventPlanning` | `planPrivateEvent` | `LocationZoneResolver` | `PlanPrivateEventHandler` |
| `CancelPrivateEvent` | `cancelPrivateEvent` | — | — |
| `ChangePrivateEventMatchingLocation` | `changeMatchingLocation` | — | — |

Out of scope, because they are not per-kind write paths: `BackupService`, `LegacyEventMigration`,
`OneOffTasks`, and `LiveScheduledLegs` (a shared context fold, not a service).

Two things the table shows that matter for the options below:

- **`ConferencePlanning` already composes another service** (`OpenCfp`), because planning a
  conference with a CFP also opens the CFP. Under per-area services that becomes a private call
  inside one class; under per-action services it stays a service-to-service dependency.
- **The four `Change*` services that read a `*DetailsViewProjector` do it to answer "does this
  exist?"** — the projector-based existence check `DecisionContextQueryDesign.md` is set to replace
  (see "Interaction" below). `CancelHotel`'s own Javadoc points at the same inconsistency.

## Options

### Translators (the `*Handler` classes)

Whatever happens to the services, the translators should give up the word "Handler". Candidates:

- **`*CommandFactory`** — `BookFlightCommandFactory.from(request)`. Says what comes out. Long.
- **`*Translator`** / **`*RequestTranslator`** — says what it does, not what comes out.
- **Fold them into the service** as private methods. Costs the seven direct unit tests they have
  today (`BookFlightHandlerTest`, `BookTrainHandlerTest`, `ChangeFlightHandlerTest`,
  `ChangeGatheringHandlerTest`, `HotelHandlerTest`, `PlanGatheringHandlerTest`,
  `PlanGroundTransferHandlerTest`), whose cases would move to the service's test with a spy
  `CommandExecutor`. Zone resolution is fiddly enough that a small, directly tested class is
  earning its keep, so this is the weakest of the three.

`HotelHandler` either splits into two (matching the rest) or the rest merge to per-area (matching
it) — the choice follows whichever service option is picked.

### Services — option A: one per area (noun), actions as methods

`HotelBooking.bookHotel / changeHotel / cancelHotel`, `FlightBooking.bookFlight / changeFlight`,
`ConferencePlanning.planConference / changeDates / openCfp / confirmAttendance / decline`, …

- **For:** four areas already look like this, so it is the smaller move. Ends the
  `cancelHotel.cancelHotel(...)` stutter. Actions on one thing sit together, and a shared
  context fold (existence, "still booked") has one home instead of one copy per action.
  `ConferencePlanning → OpenCfp` becomes a method call.
- **Against:** each class takes the **union** of its actions' collaborators, so a test of
  `cancelHotel` has to construct a `LocationZoneResolver` it never uses. Classes grow — conference
  would carry five or six actions. Controllers are already per-action, so a per-area service is
  injected into several controllers that each call one method.

### Services — option B: one per action, named as the handler it is

`CancelHotelHandler.handle(...)`, `ChangeFlightHandler.handle(...)`, `BookFlightHandler.handle(...)`
— possible only once the translators have released the name.

- **For:** accurate by the common vocabulary. Each class holds exactly what its action needs; a
  controller and its service line up one-to-one.
- **Against:** the larger move — every noun-named service splits (`TalkTracking.record` dispatches
  five talk commands, so it becomes five classes or stays an exception). Shared folds need a
  separate home (as `LiveScheduledLegs` already is). Field names would become
  `cancelHotelHandler`, which is honest but long.

### Services — option C: keep one per action, but make the name not look like a command

`CancelHotelService`, `CancelHotelUseCase`, … Cheapest: only the name changes. It fixes confusion 2
and nothing else, and the `-Service`/`-UseCase` suffix says nothing the package doesn't.

## Recommendation (not agreed)

**Option A, and translators become `*CommandFactory`**, kept as their own tested classes.

The deciding reason is the context folds: several actions on one kind ask the same question of the
stream ("is this booking still live?"), and per-area gives that question one home. The collaborator
union is the real cost; it is smallest exactly where the actions are simplest (every `Cancel*`
needs only `CommandExecutor`).

**Do not do this before `DecisionContextQueryDesign.md`** — see below.

## Interaction with other work

- **`DecisionContextQueryDesign.md`** (`unblocked — ready to build`) replaces
  `eventsForDecision()` and the projector-based existence checks with a typed query port. That
  rewrites the **constructor and the context-building half of every service in the inventory**.
  Renaming or merging first means touching every one of those classes twice, and the query-port
  work would then be reviewed as a diff against freshly moved code. **Build that first, then
  rename** — or fold the rename into it slice by slice, which is worth asking about when it starts.
- **Stored data is unaffected.** Nothing here renames a `*Command`, a `*Request` or an event, so
  `command_log`, `event_log` and backup files never see these class names. Worth re-checking only
  if an option ever proposes renaming commands.
- **`ApplicationServicesUseCommandExecutorTest`** finds services by package, not by name, so it
  survives any option. If one convention is chosen, a similar source-scan test could hold it (e.g.
  "no class in `application` ends in `Handler` unless it takes `CommandExecutor`") — decide whether
  that is worth having rather than adding it by default.

## Size of the change

Measured 2026-09-22: 21 services, 10 translators. Each service is referenced from 3–6 files in
`src/main` (its controller, `EventSourcingConfig`'s `@Bean`, and neighbours) and 1–6 in
`src/test`. All mechanical, all IDE renames/moves, no behaviour change — which is the argument for
doing it one kind at a time, each commit green, rather than as one sweep.

## Open questions for Ted

1. **A, B or C** for the services?
2. **Translators:** `*CommandFactory`, `*Translator`, or fold into the service?
3. **Sequencing:** after `DecisionContextQueryDesign.md`, or folded into its slices?
4. **A guarding test** for the chosen convention — wanted, or is the convention enough?

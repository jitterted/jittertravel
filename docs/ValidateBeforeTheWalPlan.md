# Validate before the WAL: data entry is refused before the command exists

**Status:** shipped 2026-10-22 for the seven creation forms. One decision still open, below
(the five change commands). Paused since 2026-08-22 with nothing written; reopened the day the
bug reached production.

## The bug

Entering a conference on the iPhone, Ted left out the province. The venue check lives in
`PlanConferenceCommand.execute`, and `CommandExecutor.execute` writes the command row **before**
running the command (R4 step 2, write-ahead). So the refusal left a `FAILED_DOMAIN` row under the
conference id. The form re-rendered with the same hidden id (H4: the entity id is the command
id), Ted added the province, and the resubmit was a *different* command wearing the *same* id. It
collided on `command_log`'s primary key, nothing catches that exception, and the page was a 500:

```
org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "command_log_pkey"
  Detail: Key (command_id)=(a5fb99c7-c995-45d7-94df-b2458567bf7f) already exists.
    at dev.ted.jittertravel.infrastructure.PostgresPersister.saveCommand(PostgresPersister.java:45)
    at dev.ted.jittertravel.application.CommandExecutor.execute(CommandExecutor.java:48)
    at dev.ted.jittertravel.application.ConferencePlanning.planConference(ConferencePlanning.java:64)
```

It looked flaky because only half the refusals did it. A zone problem or a bad airport code is
thrown by the handler, before the row is written, and a corrected resubmit is fine; a missing
province or a backwards date range was thrown by the command, after the row, and the corrected
resubmit crashed. Every creation form had the second kind.

H4's "natural idempotency on re-submission" assumed the retry is the *same* command. A corrected
retry is not.

## The two decisions (Ted, 2026-10-22)

**D1. Record significant failures; do not record data-entry mistakes.** Ted's words: *"I want
significant failures recorded (violations of domain constraints/invariants), but ignore data-entry
validation errors like this."* The line is the one he drew in August: **a check that needs nothing
beyond the form's own values is data entry; a check that needs state is a domain refusal.**

- Data entry — a missing province, an unknown country, a station pasted into the city box, an
  arrival before the departure, a check-out on the check-in day, a cancel-by after check-in, a
  transfer dated on neither endpoint's day — is refused **in the command's compact constructor**.
  The handler builds the command before `CommandExecutor.execute` is called, so the refusal throws
  before any row is written. "It was never a valid command, so it is invalid data, not a domain
  rejection, and it should not reach the log at all" (Ted, 2026-08-22).
- A domain refusal — a departure in the past, a conference already over, a trip overlapping a
  booked leg, a change to something that does not exist — stays in `execute(context)`, after the
  row, and is recorded as `FAILED_DOMAIN` exactly as before. **The `*NotInFuture` checks are
  domain refusals**: they need `now`, which is what the decision context carries, and Ted chose to
  keep them recorded rather than thread `now` into every handler.

**D2. After a recorded refusal, the corrected resubmit carries a fresh id.** The re-rendered form
holds a new hidden id. Row A stays in the log as the record of the refusal; the conference is
created as B. H4 is revised to say so: *the entity id is the id of the command that created it; a
refused attempt retires its id.* The alternatives were rejected: superseding the failed row would
erase the refusal D1 asked to keep, and a second row per attempt under one id is a schema change
and a backup-format change.

## What shipped

**Seven creation commands moved their structural checks into their compact constructor**:
`BookFlightCommand`, `BookTrainCommand`, `BookHotelCommand`, `PlanConferenceCommand`,
`PlanGatheringCommand`, `PlanPrivateEventCommand`, `PlanGroundTransferCommand`. Each constructor
says in its Javadoc which half it is. `PlanGroundTransferCommand` has no state-dependent rule at
all (D6: no future-date check), so its `execute` is now a bare event factory; its date-fits-the-
endpoints helper became static and takes the components, because a compact constructor runs before
the record's fields exist. The dates are `Objects.requireNonNull`'d there: a blank date is refused
at the form by `RequiredEntryAdvice` and never reaches a handler, so the old `== null` guards in
`execute` were dead, and the no-null rule is stated once where the value lands.

**One ordering changed, and it is the better order.** Before, a flight, train, hotel, gathering or
private event with a past date *and* a backwards range reported the past date first. Now the range
is refused at construction, before `execute` could look at the clock, so the backwards range is
reported first. The conference already did it that way ("a backwards range is a typo, and the more
useful thing to report"). Location before dates, and dates before overlap, are unchanged.

**Seven creation controllers re-render a refused form under a fresh id**, in the error block after
the service call. Six request records gained a `with<Entity>Id` wither; the flight request is the
one mutable bean and sets its id in place. **`RefusedForm.reRenderUnderFreshId` is where the Spring
detail lives, and it is not optional.** A fresh id on an immutable record means a new object on the
model, and the model's `BindingAwareModelMap` *removes* the binding result the moment its attribute
is replaced by a different object — then `th:field` reads every value through that binding result's
target. Left alone, the re-rendered page showed no errors at all: 12 of the train form's 17 render
tests went red on the first attempt. The helper carries the errors onto a new binding result whose
target is the fresh request and puts it back under the key Spring looks it up by. Reading the id
with `th:value="*{…}"` instead was tried and reverted: on the path where binding itself fails (an
unparsable date) the model attribute is null and the selection expression throws.

The binding-failure early return (a blank or unparsable date) keeps the id. Nothing reached the
service, so nothing was written, and the attempt was never a command.

**Not changed, on purpose:**

- `CommandExecutor` is untouched. Write-ahead stays; `FAILED_DOMAIN` stays; `CommandExecutorTest`
  is as it was.
- `BookFlightItineraryCommand` keeps every leg rule in `execute`, because it collects all of them
  into one `FlightItineraryRefused` and the form reports every leg at once. It never writes a row
  for a refusal anyway: `FlightItineraryBooking` dry-runs `execute` and only calls the executor on a
  clean evaluation. Its controller already replaces a malformed id with a fresh one.
- The `Change*` commands — see the open decision.

## Tests, by tier

- **The rule, and where it fires:** each command's own test constructs the invalid command *inside*
  `assertThatThrownBy`, so a check that drifted back into `execute` fails by name. Each file says
  in a Javadoc which refusals are built-time and which are run-time, and the train and hotel tests
  carry a case proving a data-entry mistake wins over a past date by construction.
- **The fresh id on the model:** a plain unit test per controller, `aRefusedFormIsReRenderedUnderA
  FreshId`, with a stub service that throws. Asserts the re-rendered request parses as a UUID that
  differs from the submitted one, and that what Ted typed is kept.
- **The fresh id on the page:** a `@WebMvcTest` case per form, `aRefusedFormComesBackUnderAFreshId`,
  asserting the rendered hidden input carries a UUID that is not the submitted one. This is the tier
  that caught the binding-result removal, and the one that would catch its return.
- Two handler tests had used a made-up country ("Freedonia") to mean "unresolvable zone". The
  command's own country check now refuses that at construction, so they use Brazil: a real country
  the zone table does not know.

## Open: the five change commands

`ChangeTrainCommand`, `ChangeHotelCommand`, `ChangeGatheringCommand`, `ChangeFlightCommand` and
`ChangeConferenceDatesCommand` still run their structural checks inside `execute`, after their
existence check, so a station pasted into a city box on an edit is still recorded as
`FAILED_DOMAIN`. **They do not 500**: every change controller mints a fresh command id per POST, so
there is no collision. The only cost is a typo in the log, against D1.

Moving them is not free: their tests pin *existence before location* and *existence before dates*
(`existenceIsCheckedBeforeDateValidation`, `aMissingGatheringIsReportedBeforeItsVenue`), and a
constructor check would run first. That only matters for a hand-edited URL, since the edit form is
reached from a row that exists. Ted's call: either accept the reordering and move them, or leave
them and note that D1 holds for creation only.

## Related

- `EventSourcingRulesHeuristics.md` R4 (write-ahead, unchanged) and H4 (revised).
- CLAUDE.md, "A city that is really a station is rejected on the write path, never in the record" —
  updated to say *in the command's constructor*, and why the change commands differ.
- `ActiveReadModelsPlan.md` — the change services' existence checks, which is where any
  reordering of the change commands would be decided.

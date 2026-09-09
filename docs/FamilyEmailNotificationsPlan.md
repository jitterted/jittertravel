# Family Email Notifications — Plan

**Status:** `open` — designed 2026-09-09 (Ted), nothing built.

Push an email to family when Ted books a flight or commits to a conference, so they learn it from a
message rather than by remembering to open `/itinerary`. Built as a **Translator** (Processor): an
asynchronous subscriber watches the event log, and translates a relevant event into a **command**
that records the notification as a new event.

FAMILY is a **single entity** — one email address, no per-person identity, no preferences. That is
deliberate and matches `SecurityConfig`, which has exactly two in-memory accounts (`ted`, `family`)
and no notion of a person.

---

## 1. Decisions settled 2026-09-09 (Ted)

| Decision | Choice |
|---|---|
| Channel | **Email via Brevo**, plain `POST https://api.brevo.com/v3/smtp/email`, no SDK |
| Pattern | **Translator/Processor** — async subscriber → command → event |
| Send order | **Send, then record the event.** The `NotifyFamily` command is write-ahead-logged PENDING, the POST *is* the command's work, `FamilyNotified` is appended on 2xx (§4.3) |
| Boot semantics | **At-most-once. Never send at boot, ever.** No backlog send, no cutoff property |
| Trigger config | **One place in code**, plus a runtime kill switch env var |
| Triggers | **`FlightBooked`**, and **`ConferenceAttendanceConfirmed` + `ConferenceAttendanceDeclined` + `ConferenceCancelled`** — the conference three ship together (§9.1) |
| Content bound | **Anything an OWNER surface shows may go to family** (§5) |
| Body | **`textContent` only**, no HTML part (§9.2) |
| Sender | **`notifications@jittertravel.com`**, with **`replyTo` Ted** so a reply reaches him (§9.3) |

---

## 2. Why the Translator, and what it buys

The naive shape — a subscriber that sends email — is the hazard `EventOrientedBackupRestorePlan.md`
already reverted a working feature to avoid: *"the day someone subscribes a consumer with side
effects — the motivating example is an **email sender** — a rebuild would re-fire those side
effects."*

The Translator dissolves it, because **the send stops being invisible**. Every email leaves a
`FamilyNotified` event in the log, so:

- "did family get told about this flight?" is answerable from `/admin/eventlog`, not from a
  provider dashboard;
- the notification decision is folded from the event stream (R1) rather than from a projection;
- the record rides along in every backup, so it survives a wipe-and-restore like everything else;
- switching later from at-most-once to at-least-once is a change to *when we consult the fold*,
  not a re-architecture.

### What the domain rules force

`DomainCommand.execute(context)` returns `Stream<? extends Event>` and lives in `domain`, which
`DomainIsPureTest` whitelists to `java.*` and `dev.ted.jittertravel.domain.*` only. **The Brevo POST
therefore cannot live inside the command** — which is the right outcome, not an obstacle. It puts
the HTTP call in an infrastructure client and leaves the command doing what every other command
here does: fold a decision, emit a fact.

---

## 3. Replay and restore safety — the load-bearing section

Three mechanisms, and **the first one is the whole fix**:

1. **A reactor cannot be replayed into, and the compiler enforces it.** `register()` does
   `subscribe(p); p.handle(eventStore.findAll())` — every consumer wired that way sees the entire
   history at boot, and Railway boots on every push to `main`. But `ProjectorBootstrapper.register`
   is `<P extends EventStreamConsumer>`, and `EventReactor` (§4.1) is a **separate interface**, so
   a reactor will not compile as an argument to it. `EventStore` has no replay path for reactors at
   all: they are reachable only from `append`. The sending code is therefore unreachable from a
   boot replay by **type**, not by a wiring convention someone could tidy away.

2. **Restore already cannot reach it, and this was verified rather than assumed.**
   `BackupService.restoreJson` pass two calls `persister.restoreCommandsAndEvents(...)` — straight
   to the persister, *not* through `eventStore.append()`. Its javadoc says so: *"projectors only see
   the restored rows on the next boot."* So Ted's wipe-then-import workflow notifies nobody, and
   combined with (1) the next boot notifies nobody either.

3. **The fold is the idempotency guard.** Before sending, the service folds the event stream for a
   `FamilyNotified` naming the same subject. This is what `ConfirmConferenceAttendance.contextFor`
   already does for existence, and it is the only guard needed at this volume (~20 emails a year
   against a few hundred events).

### Deploy dark

Ship with `jittertravel.family-notify.enabled=false`, confirm a real booking produces **no** email
and **no** `FamilyNotified`, then flip the variable. The kill switch is not only for incidents.

---

## 4. Components

```
EventStore.append(...)                     [existing, synchronous, holds transactionLock]
  ├─> notifySynchronousSubscribers(...)    [existing] projectors, unchanged
  └─> dispatchToReactors(...)              [NEW] executor.execute(...) per reactor, then RETURN
        └─> FamilyNotificationTranslator   [infrastructure] EventReactor — on the worker thread
              · filter to trigger types
              └─> NotifyFamily             [application]
                    1. fold eventsForDecision() → already notified? → return, write nothing
                    2. FamilyNotificationMessages.messageFor(event) → subject + body
                    3. commandExecutor.executeExternalAction(id, NotifyFamilyCommand, action)
                         a. saveCommand(...)              → command_log PENDING
                         b. action: BrevoEmailClient.send(...)   ← the only I/O
                         c. 2xx    → appends FamilyNotified      → SUCCEEDED
                            throws → markCommandFailed("FAILED_SEND")
```

### 4.1 Async delivery belongs to `EventStore`, not to the processor

**Decided 2026-09-09 (Ted), reversing an earlier draft of this plan.** That draft gave the
translator its own queue and its own worker thread. Ted's objection, and it is correct: *"I don't
like the precedent this sets for making any async processor be sync+async"* — the second processor
would hand-roll its own queue, capacity policy, thread and shutdown, giving two chances to get
back-pressure and shutdown wrong and two different answers to what happens when it backs up.

One premise in the objection does not survive, and it decides the shape of the fix: **there is no
async handoff without a synchronous enqueue.** `executor.execute(task)` performs a synchronous
`offer` onto the executor's own queue, on the calling thread, inside `append`. Moving the queue
behind a method name does not remove that work — a push design of any shape does a bounded piece of
synchronous work to hand the event across a thread boundary. So the question is not whether to
enqueue; it is **who owns the one queue**. The answer is the store, so it is written once.

(The design that genuinely removes the handoff is a **pull** processor polling the log from a
cursor. It is better on failure semantics — crash-safe by construction, and `FamilyNotified` is
already its cursor — but it needs `@EnableScheduling`, which this codebase has none of, and its
first run finds all of production history, which reopens the at-most-once decision in §1. Recorded
here so the next person does not have to rediscover it.)

#### What changes in `EventStore`

```java
public interface EventReactor {                       // NEW — deliberately NOT EventStreamConsumer
    void react(Stream<StoredEvent> events);
}

public void subscribeAsync(EventReactor reactor) { ... }
```

`append` keeps `notifySynchronousSubscribers(...)` exactly as it is, then hands the same batch to
each reactor via an **injected `Executor`**. Cost measured rather than asserted: `new EventStore(`
has **4 call sites** (1 production, 3 test), so the extra constructor parameter is a trivial change.

**Four things make this the right place for it:**

1. **A reactor is a different type from a projector, and that is load-bearing.** This is the
   `reset()` precedent from `EventOrientedBackupRestorePlan.md` applied directly: putting a
   capability on the shared consumer interface bakes in a hazard invisible at the call site. If
   async delivery were available to any `EventStreamConsumer`, someone eventually subscribes a
   *projector* asynchronously and `/calendar` starts rendering stale after a POST. A separate
   interface separates the two populations by type instead of by comment.

2. **It turns §3's replay guard into a compile error.** `ProjectorBootstrapper.register` is
   `<P extends EventStreamConsumer>`, and an `EventReactor` is not one — so a reactor **cannot** be
   bootstrapped, and there is no `handle(findAll())` path that could reach it. `EventStore` never
   replays into reactors at all. The earlier draft relied on remembering to wire one consumer
   differently from every other, with a comment begging a future reader not to tidy it up. This
   removes the thing that had to be remembered.

3. **The `Executor` is injected, so tests are deterministic.** Pass `Runnable::run` and delivery is
   same-thread and ordered; production passes the real one. Async-ness becomes a wiring decision
   rather than something baked into each processor — which also keeps this feature out of the
   open test-isolation problem instead of adding to it.

4. **Back-pressure, error handling and shutdown are written once.** A bounded queue
   (`ThreadPoolExecutor`, capacity 100, single thread, `AbortPolicy`); on rejection catch
   `RejectedExecutionException`, log, increment `eventstore.reactor.rejected`, drop. Reactor
   exceptions are caught and counted the way subscriber exceptions already are, because after
   `append` returns nobody is listening. One thread means events reach every reactor in log order.
   The `ExecutorService` is a bean, so Spring shuts it down; anything still queued at shutdown is
   lost, which is consistent with at-most-once and stated rather than discovered.

#### Reentrancy: a reactor's own append re-enters dispatch

Worth knowing before writing the code, because it is not obvious and it decides one policy choice.
A reactor appends events (that is the entire Translator pattern), so `FamilyNotified` goes through
`EventStore.append` and is dispatched **back to every reactor**, including the one that caused it.
Three consequences:

- **No loop**, because `FamilyNotified` is not a trigger. The `FamilyNotificationTriggerCompleteness`
  test is what keeps it that way: it forces an explicit not-notified decision for the event, so
  making a notification notifiable becomes a thing someone has to write down rather than a thing
  that quietly recurses.
- **No deadlock.** The worker thread's `append` waits on `transactionLock` if a booking is in
  flight, and it holds nothing while waiting.
- **`CallerRunsPolicy` is banned, and this is the stronger reason.** The earlier one — it runs the
  Brevo POST on the append thread — is true but secondary. The real hazard is that the append thread
  is *inside* `append`, holding `transactionLock`; `synchronized` is reentrant, so a reactor running
  there would append events **inside another append's critical section**, interleaving sequence
  assignment with the batch that is still being written. That is silent corruption, not a slow
  request. `AbortPolicy` and drop.

#### Read-only mode on the worker thread

`CommandExecutor.execute` throws `ReadOnlyModeException`, and the store can flip to read-only *after*
a booking was appended — so the sequence "email accepted by Brevo, then the record fails" is
reachable. Catch it on the worker thread, log, count; never let it escape and kill the thread. The
outcome is failure mode #2 in §6 by another route.

#### `FamilyNotificationTranslator` (infrastructure)

What is left of it is genuinely small: an `EventReactor` that filters the batch to trigger types and
calls `NotifyFamily`. No queue, no thread, no lifecycle. It runs on the worker thread, so it may
block on Brevo freely.

`Clock` is injected (`NoAmbientClockReadsTest`), and the translator captures `Instant.now(clock)`
and `UUID.randomUUID()` and passes them inward. **The translator is the boundary for this write
path** — there is no HTTP request behind it — so the external-inputs rule is satisfied here rather
than violated. Say so in the javadoc; it reads like a violation otherwise.

> **Scope note.** This adds a general async-delivery seam for one consumer, which brushes against
> "no abstraction before the second user". It is not that rule's case: both designs build exactly
> one queue and one thread, and the only question is whether the second processor reuses it or
> copies it. Given equal cost, the reusable location wins. What is *not* built here is anything the
> single consumer does not need — no retry, no dead-letter, no per-reactor tuning.

### 4.2 `BrevoEmailClient` (infrastructure)

Copy `AeroDataBoxClient`'s shape. One difference: its blank-key `return` becomes a
`boolean configured()` the **service** consults *before* opening a command (§4.3), so an
unconfigured notifier writes no `command_log` row rather than an immediately-failed one.

```
POST https://api.brevo.com/v3/smtp/email
api-key: <BREVO_API_KEY>
content-type: application/json

{"sender":  {"name":"JitterTravel","email":"notifications@jittertravel.com"},
 "replyTo": {"email":"<TED_REPLY_EMAIL>"},
 "to":      [{"email":"<FAMILY_NOTIFY_EMAIL>"}],
 "subject": "...",
 "textContent": "..."}
```

**`textContent` only, no `htmlContent`** (§9.2): the body is four to six lines, plain text renders
identically everywhere, Apple Mail auto-links a bare URL so the conference `infoUrl` still opens
with a tap, and there is no email-CSS discipline to take on for content that wants no layout.

**`replyTo` is Ted** (§9.3). The `From` reads as the app, but the likely reply is *"what time do you
land?"* and it should reach him. A `no-reply@` was rejected: the audience is family, not customers.

2xx ⇒ accepted, and the action returns the `FamilyNotified` event. Anything else ⇒ **throw**, which
is what `executeExternalAction` turns into a `FAILED_SEND` command row (§4.3). No retry. The
translator catches at the top of its task, logs, and increments `family.notification.failed`
(Micrometer is already wired) — nothing may escape into the worker and kill the thread.

### 4.3 The command is `NotifyFamily`, and it does the work

**Decided 2026-09-09 (Ted), reversing an earlier draft.** That draft had the service send the email
and *then* execute a `RecordFamilyNotifiedCommand`. Ted: *"that's just a cheat to generate the event
without the triggering command."* Correct, twice over:

- **The command log is a log of intents.** Nobody ever asked to *record a notification*; the
  processor asked to **notify family**. `RecordFamilyNotified` was a fabricated intent invented to
  reach an append.
- **It threw away the failure record, which is the worse half.** With the send outside the command,
  a failed send writes **nothing to `command_log`** — no trace that a notification was attempted.
  But `CommandExecutor` already has exactly this: `saveCommand` writes the row PENDING *before* the
  work, and `markCommandFailed` stamps `FAILED_DOMAIN`/`FAILED_PERSIST` when it doesn't complete.
  There is a `/admin/pending-commands` page, a `pendingCount` on the admin home, and a
  `PendingCommandStartupCheck` that warns at boot. The draft routed around all of it.

Even on its own terms the extra command was redundant: `CommandExecutor.appendEvents(commandId,
commandRecord, events)` already appends events under a command record with no `DomainCommand` at all
(`GatheringPlanning.clearConflict` is the precedent). A no-op `DomainCommand` was never needed.

#### The one hard constraint

**A `DomainCommand` cannot do the work.** `DomainCommand.execute(context)` lives in `domain`, which
`DomainIsPureTest` whitelists to `java.*` and `dev.ted.jittertravel.domain.*` — there is no import
under which it could reach `RestClient`. So "the command does the work" cannot mean a `DomainCommand`
here, and that is a rule worth keeping rather than bending.

#### `CommandExecutor.executeExternalAction`

The honest way to keep the intent in the write-ahead log is a third method beside `execute` and
`appendEvents` — same order as `execute`, with the pure decision swapped for an action allowed to do
I/O, which is why the action type lives outside `domain`:

```java
@FunctionalInterface
public interface ExternalAction {                 // NOT in `domain` — it may do I/O
    Stream<? extends Event> perform();            // throws to fail the command
}

public void executeExternalAction(UUID commandId, Object commandRecord, ExternalAction action) {
    refuseWhenReadOnly(commandRecord);
    persister.saveCommand(commandId, commandRecord);          // write-ahead: PENDING
    List<? extends Event> events;
    try {
        events = action.perform().toList();                   // the Brevo POST
    } catch (RuntimeException failed) {
        persister.markCommandFailed(commandId, "FAILED_SEND", failed.getMessage());
        throw failed;
    }
    appendOrMarkFailed(commandId, events);                    // → SUCCEEDED
}
```

This generalises to any future processor calling an outside system, which is the point of building
the pattern once. `BrevoEmailClient.send` therefore **throws** on a non-2xx rather than returning a
result object — the exception is what fails the command.

> **This does not reopen the "send, then record" decision in §1.** What is written before the send is
> a **command row**, which is the write-ahead every command in this app already gets. No *event* is
> appended before the send, so this is not the outbox that was rejected: an outbox would put a
> `FamilyNotificationRequested` **event** in the log, visible to projectors, in every backup, forever.

#### The caveat this creates, and it changes a documented invariant

`PendingCommandStartupCheck`'s javadoc says: *"Because event appending is atomic, a PENDING command
means no events were durably written — **there is nothing to repair**."* That reasoning holds only
while every command is pure. A PENDING `NotifyFamily` means **the email may well have been sent** —
the crash could have been after Brevo accepted it. So:

- the javadoc must be amended in the same change, not left to mislead;
- a human resolving `/admin/pending-commands` needs to know that abandoning a `NotifyFamily` row is
  not the same as abandoning a `BookFlight` row;
- and the upside: failure mode #1 in §6 stops being *silent*. A dropped notification now leaves a
  PENDING or `FAILED_SEND` row in a surface that already exists and already warns at boot.

#### `NotifyFamily` (application)

Takes `CommandExecutor` and `BrevoEmailClient` — never `EventStore`
(`ApplicationServicesUseCommandExecutorTest`). Two things happen **before** the command exists, and
both deliberately write nothing:

1. **The kill switch and the blank-API-key check.** A disabled or unconfigured notifier must not
   litter `command_log` with rows for mail it never intended to send — and this is what keeps the
   §6 promise that integration tests neither send nor pollute.
2. **The already-notified fold.** A duplicate trigger is a no-op, not a failure; writing a
   `FAILED_DOMAIN` row for "nothing to do" would pollute the pending/failed surfaces this design now
   relies on for signal. Safe outside the command because the reactor executor is single-threaded,
   so there is no concurrent second trigger for one subject.

`NotifyFamilyCommand(NotifiedSubject subject, Instant requestedAt)` is the record persisted to
`command_log` — the intent, no recipient and no prose (§4.4). It lives next to the boundary that
mints it, the processor; compare `ClearDifferentCityConflict`, which lives in `web` because its
boundary is a web request.

### 4.4 The new event

```java
public record FamilyNotified(
        NotifiedSubject subject,   // sealed: FlightNotification | ConferenceNotification
        Instant notifiedAt
) implements Event { }
```

**Two fields, both pure — no `summary`, decided 2026-09-09 (Ted).** A draft carried the subject line
as sent, on the argument that re-wording emails later must not change what a past event says went
out. Ted: *"the subject should be recoverable as-sent because the event is recorded, and a
later-changed booking is irrelevant as we know the exact booking as of when this event was
generated — that is one of the main advantages of event sourcing in the first place."* That is
right, and the cost the draft claimed was a naive-rendering cost rather than an inherent one: folding
the log **to this event's own sequence** reproduces the booking exactly as it stood when the mail
went out. Storing the rendered string was buying something the log already gives.

Consequence to know rather than rediscover: `/admin/eventlog` renders payloads, so a `FamilyNotified`
row shows a typed id and no prose. Making it readable means folding to that sequence, which nothing
does today — worth doing only if the row is ever actually consulted. The presentation-out-of-domain
rule is now satisfied outright rather than argued around.

**Keyed on the entity, not on the triggering event's id.** Two reasons, and the second is a
behaviour improvement rather than a matter of taste:

1. `StoredEvent.eventId` is an **envelope** field. R11's reasoning — a payload must not depend on
   the store's envelope — applies to an id exactly as it does to a timestamp.
2. `ConferenceAttendanceConfirmed` can legitimately occur **twice** for one conference: its service
   javadoc says re-confirming with a different `basis` is a correction, and the last one wins. Keyed
   on `ConferenceId`, the correction sends no second email — correct, since `basis` is excluded from
   the body and the family-visible content is identical. Keyed on the event id it would send a
   duplicate saying the same thing.

`NotifiedSubject` is a **sealed** interface over `FlightId` and `ConferenceId` wrappers, following
the `ScheduledLegId` / `TravelLeg` precedent from `CancelTrainAndOverlappingLegsPlan.md`. Adding a
trigger then means adding a variant, which is a decision the compiler asks for.

**The recipient address is deliberately NOT a field.** It is configuration, and every event is
written verbatim into every backup JSON file — putting a family member's address there makes each
backup a small contact database for no benefit.

The same reasoning applies to `NotifyFamilyCommand` (§4.3), which is kept to
`(NotifiedSubject subject, Instant requestedAt)`. It is not a domain `Event`, so the presentation
rule does not reach it — but a `FAILED_SEND` row is identifiable by subject and timestamp without
storing prose, so there is nothing for the extra field to buy.

Registration checklist for the new event (each has a test that fails until done):

- one `register("FamilyNotified", FamilyNotified.class)` line in `EventTypes` — `EventTypesTest`
- a case in `GoldenEventDeserializationTest`
- `INITIAL_SCHEMA_VERSION`; no upcaster, no migration, **backup format stays v3** (additive new
  type, the `TrainCancelled` precedent)

### 4.5 Trigger configuration

**One place in code, because a trigger and its email body are the same list.** You cannot enable
`HotelBooked` without writing what the hotel email says, so a property file can only ever be looser
than the code, never more useful.

`FamilyNotificationMessages.messageFor(...)` is an exhaustive `switch` over the event payload
returning `Optional<FamilyMessage>`. The forcing function is
`FamilyNotificationTriggerCompletenessTest`, in the style of `CalendarDayMenuTest`: it iterates
**every type registered in `EventTypes`** and requires each to appear in either the notified set or
an explicit not-notified list with a reason. A new event class then cannot be added without someone
writing down whether family hears about it.

Runtime configuration is only:

| Variable | Default | Purpose |
|---|---|---|
| `BREVO_API_KEY` | *(blank)* | secret; blank ⇒ no-op with a warning |
| `FAMILY_NOTIFY_EMAIL` | *(blank)* | the single recipient; blank ⇒ no-op |
| `TED_REPLY_EMAIL` | *(blank)* | `replyTo`; blank ⇒ replies land in the unattended sender mailbox |
| `JITTERTRAVEL_FAMILY_NOTIFY_ENABLED` | `false` | kill switch, no deploy needed |

---

## 5. Content, and the redaction bound

**The bound is: anything an OWNER surface shows may go to family** (Ted, 2026-09-09). FAMILY and
OWNER are one audience for the purposes of this email. A draft used `/itinerary` as the bound, which
would have excluded `AttendanceBasis` automatically; Ted chose the wider rule instead, so the basis
is **in** (§9.4).

**The cost of that choice, recorded because it was chosen with it stated.** This is a *deny-list*
shape — every field on a triggering event is includable by default until someone decides it should
not be — and it is the shape CLAUDE.md says `PublicCalendarProjector` deliberately moved **away**
from. Two things keep it honest here, and both should be treated as load-bearing:

- **The messages are still an allow-list in practice**, because `FamilyNotificationMessages` names
  every field it renders. Nothing is derived from a general "entry" object, so a field it never
  names cannot appear by accident. **Do not** refactor these messages to render from a shared view
  record — that is precisely the move that turns a written-out list back into a deny-list.
- **Anonymous redaction is untouched and still wins.** This bound governs one authenticated
  recipient. Nothing here reaches `PublicCalendarProjector`, `EntryDetails.Publishable` has no slot
  for any of it, and no calendar entry carries it.

### Flight — entirely self-contained

Every field the email needs is on `FlightBooked` itself (`airline`, `flightNumber`,
`departureAirport`, `departureDateTime`, `arrivalAirport`, `arrivalDateTime`), and
`FlightItineraryEntry` carries exactly the same set. **No fold needed.**

```
Subject: Ted booked a flight to London

UA 195
San Francisco (SFO) → London (LHR)
Departs  Mon 12 Oct 2026, 3:20 PM (SFO)
Arrives  Tue 13 Oct 2026, 9:45 AM (LHR)
```

City names come from `StaticAirportCityResolver` (already a bean with four injection sites). Times
come from `ZonedTimestamp.atEntryZone()` — a payload field, never the envelope (R11) — and **the
zone is labelled**. An unlabelled local time in an email is the `project_flight_history_timestamps`
bug in a place with no page around it to give context.

### Conference — needs a fold

`ConferenceAttendanceConfirmed` carries only `conferenceId`, `basis`, `confirmedOn`. **It does not
carry the conference's name, venue or dates**, so the message cannot be built from the triggering
event alone; the service folds the stream for the matching `ConferencePlanned` (`name`, `startDate`,
`endDate`, `venueName`, `venueAddress`, `infoUrl`) — the same fold `ConfirmConferenceAttendance`
already performs for existence, and R1-compatible because it reads the stream and not a projector.

```
Subject: Ted is going to SoCraTes 2026

SoCraTes 2026
Seminarzentrum Rückersbach, Johannesberg, DE
Mon 24 Aug – Thu 27 Aug 2026
His talk was accepted.
https://socrates-conference.de
```

**`AttendanceBasis` is included** (Ted, 2026-09-09). Family are four people who already know his
working life, and CLAUDE.md's rule about the basis is written against **anonymous** viewers — it is
"submission status wearing a different hat" to a stranger, and ordinary news to a sibling. One line
per value, mapped explicitly in `FamilyNotificationMessages` rather than by printing the enum.

### Conference exits — the same fold, the other direction

`ConferenceAttendanceDeclined` and `ConferenceCancelled` ship **in slice 2 alongside the
confirmation** (§9.1), so there is never a released state in which family can be told he is going
but not that he is not. Both fold to the same `ConferencePlanned` for the name.

```
Subject: Ted is no longer going to SoCraTes 2026

SoCraTes 2026
Mon 24 Aug – Thu 27 Aug 2026
```

Two notes. The two exits are **different facts** — Ted withdrew, versus the organizers cancelled the
event — and CLAUDE.md keeps them distinct for good reason, so they get distinct wording rather than
one shared "not going" template. And a decline carries no reason field, so the email says what
happened and not why; that is the honest limit of what the event holds.

---

## 6. Failure modes, accepted knowingly

Named here rather than discovered later, per the standing "name the losses out loud" rule.

1. **A dropped email is dropped forever — but no longer silently.** At-most-once: a Brevo outage, a
   crash, a rejected task, or a shutdown with work queued means that notification never goes out and
   nothing retries it. Chosen deliberately over a boot-time backlog send, whose failure mode —
   emailing the entire flight history on one bad deploy — is far worse and much harder to undo.
   **Since §4.3 the loss leaves a trace**: a `FAILED_SEND` or PENDING row on
   `/admin/pending-commands`, counted on the admin home and warned about at boot by
   `PendingCommandStartupCheck`. A rejected task is the one hole — it never reaches a command row —
   which is why rejection gets its own counter.
2. **A crash between the 2xx and the append** leaves an email sent and the command PENDING. For a
   flight this is invisible downstream (there is only ever one `FlightBooked` per `FlightId`); for a
   conference, a later re-confirmation would send a second, identical email. Note it inverts the
   meaning of a PENDING row — see the caveat in §4.3.
3. **Family is told about bookings and never about changes or cancellations.** With the trigger list
   as narrowed, `FlightChanged`, `ConferenceAttendanceDeclined` and `ConferenceCancelled` send
   nothing. **This is the biggest gap in the feature as specified** — "Ted is going to SoCraTes"
   with no follow-up when he declines is arguably worse than never having emailed, because family
   now hold a stale fact they believe is current. Flagged for a decision; see §9.
4. **Ordering is not guaranteed against the page.** The email is sent from a worker thread after the
   HTTP response has returned, so family can in principle receive it before Ted's browser finishes
   loading the confirmation page. Harmless here, but true.

### Two things that are NOT hazards, checked so nobody re-checks them

- **The existing integration tests cannot send mail, and cannot pollute the event log.** They share
  one in-memory `EventStore` (H6), so every test that books a flight will reach the translator. With
  `BREVO_API_KEY` blank — the default, and the `AeroDataBoxClient` idiom — the client returns
  *skipped*, which means no request **and** no `FamilyNotified` appended, since the event records a
  successful send. Nothing to opt out of and no test fixture to remember.
- **Multi-instance adds no new hazard.** Each replica has its own in-memory `EventStore` and only
  dispatches its own appends, so a booking made on one instance produces exactly one email and the
  other never sees it. That is the *existing* staleness limitation from
  `CommandConsistencyEventStore.md`, not a new one this feature introduces — and notably it fails
  toward one email rather than two.

---

## 7. Slices

**Slice 0 — the async seam, no feature.** `EventReactor`; `EventStore.subscribeAsync` + the injected
`Executor` (4 constructor call sites) + reactor dispatch, error counting and rejection handling; the
`ExecutorService` bean in `EventSourcingConfig`; the `EventStoreTest` cases. Ships green with no
reactor registered and no behaviour change, so the seam is reviewable on its own rather than inside
a feature diff.

**Slice 1 — flights, end to end.** `ExternalAction` + `CommandExecutor.executeExternalAction` +
the `PendingCommandStartupCheck` javadoc amendment; `FamilyNotified` + `NotifiedSubject` +
`NotifyFamilyCommand` + `EventTypes` + golden sample; `BrevoEmailClient`; `NotifyFamily`;
`FamilyNotificationTranslator`; `FamilyNotificationMessages` with the flight arm only; wiring in
`EventSourcingConfig`; the completeness test; the three env vars. Deploy dark, verify, flip.

**Slice 2 — conferences, in and out together.** Adds the `ConferencePlanned` fold, the second
`NotifiedSubject` variant, and **three** arms: `ConferenceAttendanceConfirmed`,
`ConferenceAttendanceDeclined`, `ConferenceCancelled`. Sequenced second because it is the one that
needs the fold, and slice 1 proves the pipe without it. **The three do not split further** (§9.1):
shipping the confirmation alone would put a released version in production that can tell family he
is going and never that he is not.

---

## 8. Tests required

- `EventStoreTest` (extended) — `append` delivers the batch to a registered `EventReactor`;
  **replaying history into projectors delivers nothing to a reactor** (the replay guard, now mostly
  held by the type system but worth pinning anyway); a reactor that throws does not break the
  append or stop the other reactors; a rejected task is counted and dropped rather than thrown.
- `FamilyNotificationTranslatorTest` — with `Runnable::run` as the executor, a trigger event reaches
  `NotifyFamily` and a non-trigger event does not. No queue or thread to test: they moved.
- `CommandExecutorTest` (extended) — `executeExternalAction` writes the command PENDING **before**
  running the action (assert ordering, not just the end state — it is the whole reason the method
  exists); a throwing action leaves a `FAILED_SEND` row and appends no events; a successful one
  appends and flips SUCCEEDED; read-only mode refuses before any row is written.
- `NotifyFamilyTest` — an unconfigured or disabled notifier writes **no command row at all**; the
  already-notified fold writes no command row either; a non-2xx leaves a failed command and no
  event; the `FamilyNotified` event is appended only after a successful send.
- `BrevoEmailClientTest` — `MockRestServiceServer`; assert the `api-key` header and the exact JSON
  body; blank key ⇒ no request at all.
- `FamilyNotificationMessagesTest` — the exact body for each of the four arms, asserted as whole
  lines rather than bare words (the precise-assertions rule); every `AttendanceBasis` value maps to
  its own sentence; a decline and an organizer cancellation produce **different** text; the flight
  body labels both zones. **Also `doesNotContain` for what is deliberately left out** — a decline
  says what happened and not why — because absence is the half that stops being pinned when the
  message is reworded.
- `FamilyNotificationTriggerCompletenessTest` — every `EventTypes`-registered class has an explicit
  notify / do-not-notify decision.
- `GoldenEventDeserializationTest` case; `EventTypesTest` completeness.
- Existing rule tests that must stay green: `DomainIsPureTest`, `NoAmbientClockReadsTest`,
  `ApplicationServicesUseCommandExecutorTest`, `NoFullyQualifiedClassReferencesTest`.
- Mutation-verify each new test (standing practice). The replay guard's mutation is no longer
  "re-wire it through `ProjectorBootstrapper`" — that will not compile, which is the improvement.
  Mutate instead by making `EventStore` deliver `findAll()` to reactors at construction; the
  `EventStoreTest` case above must go red.

---

## 9. Questions resolved, and what remains open

**All five closed 2026-09-09 (Ted), plus one they created.**

1. **Conference exits notify, and ship in slice 2 — not a later slice.**
   `ConferenceAttendanceDeclined` and `ConferenceCancelled` go out with the confirmation, so no
   released version can tell family he is going and never that he is not. → §5, §7.
2. **`textContent` only**, no HTML part. → §4.2.
3. **`notifications@jittertravel.com`, with `replyTo` Ted.** The `From` reads as the app; the reply
   reaches him. `no-reply@` rejected — the audience is family, not customers. → §4.2, §10.
4. **`AttendanceBasis` is included.** CLAUDE.md's rule about it is written against anonymous
   viewers; family already know his working life. → §5.
5. **`summary` is dropped** from `FamilyNotified`; both remaining fields are pure. The subject as
   sent is recoverable by folding the log to that event's own sequence. → §4.4.
6. **New, created by (4): the content bound is now "anything an OWNER surface shows."** `/itinerary`
   no longer bounds it. The deny-list risk that creates, and the two things holding it honest, are
   in §5 — the important one being **do not refactor the messages to render from a shared view
   record**.

### Still open

- **`FlightChanged`** notifies nobody, deliberately. There is no `FlightCancelled` event in the
  codebase at all, and `FlightChanged` is a full snapshot, so "what changed" means diffing against
  prior state rather than reading the event. Left for a later slice with its eyes open: a flight
  moved by six hours is exactly the kind of thing family would want, and today they get silence.
- **`/admin/eventlog` shows a `FamilyNotified` as a typed id with no prose** (§4.4). Fixable by
  folding to that sequence; worth doing only if the row is ever actually consulted.

---

## 10. Pre-Push tasks (belong in `Pre-Push-Tasks.md` with the commit)

- [ ] Brevo account created; `notifications@jittertravel.com` added as a sending identity.
- [ ] **SPF and DKIM DNS records** published for `jittertravel.com` and verified in Brevo.
      *Skipped ⇒ mail is accepted by the API and lands in spam, which looks exactly like the feature
      not working.* **Before the push.**
- [ ] `BREVO_API_KEY` set on the **app** Railway service (secret; variables are scoped per service).
- [ ] `FAMILY_NOTIFY_EMAIL` set on the app service — the single family address or group alias.
- [ ] `TED_REPLY_EMAIL` set on the app service — where a family reply lands (§9.3). *Skipped ⇒
      replies go to the unattended `notifications@` mailbox and are never seen.* **After the
      rollout**, but before telling family the emails are live.
- [ ] `JITTERTRAVEL_FAMILY_NOTIFY_ENABLED` left `false` for the first rollout; flipped to `true`
      only after a real booking is confirmed to have produced no email and no `FamilyNotified`.
      *Skipped ⇒ the first booking after the rollout emails family from an unverified sender.*
      **After the rollout.**

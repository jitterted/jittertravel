# Family Email Notifications — Plan

**Status:** `open` — designed 2026-09-09 (Ted), **revised 2026-09-10 after a review against the
code**, nothing built. The revision changed three things materially — the conference trigger set
(§4.5), the idempotency key (§4.4), and what `/admin/pending-commands` actually shows (§4.3) — and
opened five questions in §9 that need Ted before slice 2 is buildable. Everything the review checked
and found sound is marked *(verified 2026-09-10)* where it matters.

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
| Triggers | **`FlightBooked`**, and for conferences **every event that moves the attendance commitment** — `ConferenceAttendanceConfirmed`, `ConferenceAttendanceDeclined`, `ConferenceCancelled`, **`TalkAccepted`, `TalkRejected`**. Revised 2026-09-10: the original three miss the auto-commit and auto-drop paths entirely (§4.5) |
| Notify decision | **The notified *fact* changed**, not "has this entity ever been notified" — revised 2026-09-10, because the original key made the exit email unreachable (§4.4) |
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

3. **The fold is the idempotency guard.** Before sending, the service folds the event stream for the
   **most recent `FamilyNotified` naming the same subject** and compares the fact it recorded with
   the fact that would be sent now. Same fact ⇒ nothing to say ⇒ return, write nothing. This is
   what `ConfirmConferenceAttendance.contextFor` already does for existence, and it is the only
   guard needed at this volume (~20 emails a year against a few hundred events).

   **It was a bare existence check until 2026-09-10, and that was a bug, not a simplification.**
   "Has this subject ever been notified?" makes the *second* notification about a subject
   unreachable — so a conference confirmed and then declined would send the "going" mail and
   silently swallow the "no longer going" mail, which is precisely the failure §9.1 exists to
   prevent. The reasoning is in §4.4; the shape of the fold is in §4.5.

### Deploy dark

Ship with `jittertravel.family-notify.enabled=false` (env `FAMILY_NOTIFY_ENABLED` — see §4.5 for why
the name matters), confirm a real booking produces **no** email and **no** `FamilyNotified`, then
flip the variable. The kill switch is not only for incidents.

**Then send one real email to Ted before family ever see one.** Point `FAMILY_NOTIFY_EMAIL` at Ted's
own address, make a real booking, confirm the mail arrives and reads correctly, *then* repoint it at
the family address. A mistyped recipient sends OWNER-bounded content — travel detail,
`AttendanceBasis`, a conference's speaking history — to a stranger, and the only symptom is a reply
that never comes. §5 sets a wide content bound; this is the step that makes the wide bound safe.

---

## 4. Components

```
EventStore.append(...)                     [existing, synchronous, holds transactionLock]
  ├─> notifySynchronousSubscribers(...)    [existing] projectors, unchanged
  └─> dispatchToReactors(...)              [NEW] executor.execute(...) per reactor, then RETURN
        └─> FamilyNotificationTranslator   [infrastructure] EventReactor — on the worker thread
              · filter to trigger types
              └─> NotifyFamily             [application]
                    0. enabled? configured? → return, write nothing
                    1. fold eventsForDecision() → the fact to tell family now (§4.5),
                       and the fact last told → unchanged? → return, write nothing
                    2. FamilyNotificationMessages.messageFor(fact) → subject + body
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
    void react(List<StoredEvent> events);             // List, NOT Stream — see below
}

public void subscribeAsync(EventReactor reactor) { ... }
```

**`List`, not `Stream`, and the difference is a real bug avoided** (2026-09-10). A reactor's batch is
handed across a thread boundary and consumed *later*, on the worker. A `Stream` is
single-consumption, so the obvious implementation — build `storedEvents.stream()` at dispatch and
capture it in the task — gives the second reactor an already-consumed stream, and gives the first
one a stream created on a thread that has since released `transactionLock`. `List<StoredEvent>` is
already what `append` has in hand (`storedEvents` is a `toList()`), it is immutable, and it removes
the trap rather than documenting it. `EventStreamConsumer` keeps its `Stream` signature: it is
consumed synchronously, in the same stack frame, which is the case a stream suits.

**One structural caveat on the type guard.** `EventStreamConsumer.handle(Stream<StoredEvent>)` and
`EventReactor.react(List<StoredEvent>)` are separate interfaces, not a hierarchy, so nothing stops a
class implementing **both** and being registered both ways — bootstrapped *and* subscribed async.
That is not reachable by accident (it takes two deliberate registrations), and the differing
parameter types make it visible in the class declaration, but the guard is "a reactor cannot be
replayed into", not "a class cannot be both".

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

   **`Runnable::run` is only safe where the reactor under test does not itself append**, and that
   restriction is not optional: it is byte-for-byte the `CallerRunsPolicy` interleaving banned two
   sections below — the reactor runs on the append thread, inside `append`, holding
   `transactionLock`, and its own `append` re-enters the critical section. It is fine for
   `EventStoreTest` (a recording reactor) and for `FamilyNotificationTranslatorTest` (`NotifyFamily`
   is a test double). It must **not** be wired into an integration test that has a real
   `EventStore` behind a real `NotifyFamily`. Say so in the javadoc of `subscribeAsync`, next to the
   `Executor` parameter, because the two paragraphs that explain it are far apart.

4. **Back-pressure, error handling and shutdown are written once.** A bounded queue
   (`ThreadPoolExecutor`, capacity 100, single thread, `AbortPolicy`); on rejection catch
   `RejectedExecutionException`, log, increment `eventstore.reactor.rejected`, drop. Reactor
   exceptions are caught and counted the way subscriber exceptions already are, because after
   `append` returns nobody is listening. One thread means events reach every reactor in log order.

   **Shutdown must be declared `shutdownNow`, and the default is wrong** (corrected 2026-09-10). An
   earlier draft said "anything still queued at shutdown is lost, which is consistent with
   at-most-once". It is not what happens: Spring infers `shutdown()` as the destroy method of an
   `ExecutorService` bean, and `shutdown()` **drains** the queue — already-submitted tasks run. So
   queued Brevo POSTs would fire while the datasource is closing, and the `FamilyNotified` append
   behind each one would then fail: sent-but-unrecorded, which is the worst of the two outcomes and
   the one §4.3 built the command row to make visible. Declare
   `@Bean(destroyMethod = "shutdownNow")` so the drop is the real behaviour and the sentence above
   is true. Pin it with a test that a queued task is not run after shutdown.

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

#### Why not virtual threads instead of a pool

Asked 2026-09-10, and worth writing down because the obvious modern answer to "bounded queue,
single thread, rejection policy" is "Java 26 has virtual threads, delete all of that". It is the
wrong answer **here**, for a reason that is about correctness rather than performance.

**Start with the fact:** virtual threads are not enabled in this app. `spring.threads.virtual.enabled`
appears nowhere in `application.properties` or `application-prod-preview.properties`, and it is
opt-in. Nor would setting it reach this bean: that property swaps Tomcat's request threads and
Spring-managed task executors, not a `ThreadPoolExecutor` constructed by hand in
`EventSourcingConfig`.

**A pool does two jobs, and virtual threads only retire one of them.** Job one is reusing an
expensive OS thread — virtual threads genuinely kill that, and nothing here depends on it. Job two
is **bounding how much work runs at once**, which is what the capacity-100 queue and `AbortPolicy`
are. `Executors.newVirtualThreadPerTaskExecutor()` has no queue and no ceiling: every submitted task
starts immediately, and it has no rejection policy because nothing is ever rejected. §6.1's "a
rejected task is dropped and counted" would become "every trigger POSTs to Brevo at once". That is a
different failure mode, not the absence of one.

**The blocker, though, is that one thread is load-bearing for correctness.** Two things in this
design read "single-threaded reactor executor" as a premise:

- **The fact-changed fold sits outside the command** (§4.5, and the `NotifyFamily` section below).
  It is safe there *only* because there is no concurrent second trigger for one subject. Under
  thread-per-task, two triggers for the same flight both fold, both conclude family has not been
  told, and both send.
- **Events reach every reactor in log order**, which is the last sentence of point 4 above.
  Concurrent dispatch loses it.

Neither is a performance property that can be traded for cheaper blocking. And cheaper blocking is
all that is on offer: the worker blocks on one Brevo POST at a time, on one thread, in a
single-user app. The virtual-thread win scales with thousands of concurrent blocking tasks.

**Two things that would change, neither of them a reason to switch.** A virtual-thread-per-task
executor never runs a task on the caller, so the `CallerRunsPolicy` hazard above evaporates — but
`Runnable::run` in tests is exactly as dangerous as it is today, because that is caller-runs by
construction. And `transactionLock` is a `synchronized` block, which on JDK 21–23 pinned a virtual
thread's carrier while it waited; JEP 491 removed that in JDK 24, so on Java 26 it is a non-issue
either way.

**Conclusion: bounded queue, single thread, `AbortPolicy`, `shutdownNow`.** Turning virtual threads
on app-wide is a separate question about the web tier and would leave this bean untouched.

#### Read-only mode on the worker thread

`refuseWhenReadOnly` is the **first** line of `executeExternalAction`, before `saveCommand` and
before the action — so in read-only mode the outcome is **no email, no command row, and no trace at
all**. That is a genuinely silent drop, and it is the one hole left in §6.1's promise that a lost
notification is visible somewhere. Catch `ReadOnlyModeException` on the worker thread, log it, and
increment `family.notification.suppressed.readonly`; never let it escape and kill the thread. The
counter is the whole record of the loss.

**Corrected 2026-09-10.** An earlier draft described the read-only case as *"email accepted by
Brevo, then the record fails"* and pointed at failure mode #2. That sequence is not reachable this
way: read-only refuses before the send, so nothing is sent. Failure mode #2 is reached only by a
crash or a database failure between the 2xx and the append — the `appendOrMarkFailed` path, not
this one.

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

- **the invariant is stated in three places, not one**, and all three must be amended in the same
  change (checked 2026-09-10):
  - `PendingCommandStartupCheck`'s javadoc, quoted above;
  - its boot log message — *"they did not complete and no events were written"*;
  - `admin-pending-commands.html:114` — *"These commands were persisted but never completed (no
    events were written)"*. This is the one a human actually reads while deciding, so it is the one
    that matters most and the one an "amend the javadoc" task would miss.
- a human resolving `/admin/pending-commands` needs to know that abandoning a `NotifyFamily` row is
  not the same as abandoning a `BookFlight` row;
- and the upside: failure mode #1 in §6 stops being *silent* — **once §4.3a below is built.**

#### 4.3a `/admin/pending-commands` does not show a `FAILED_SEND` row today

**Found 2026-09-10, and it invalidates the payoff this whole section is justified by.**
`PostgresPersister.findPendingCommands()` and `countPendingCommands()` both filter
`WHERE status = 'PENDING'` — nothing else. `PendingCommandStartupCheck` calls the second. So a
`FAILED_SEND` row lands **only** under `/admin/commandlog?status=FAILED`, which nothing counts and
nothing warns about, and which nobody visits unless they already suspect something.

Building `executeExternalAction` to reach a surface it does not in fact reach is the whole cost with
none of the benefit, so **slice 1 widens the surface** rather than correcting the claim downwards:

- `countPendingCommands()` and `findPendingCommands()` take `status IN ('PENDING')` **or** a
  `status LIKE 'FAILED%'` — the exact predicate is §9's open question Q3, because widening the count
  also changes the `pendingCount` badge on the admin home and the boot warning for **every**
  command, not just this one. That is a change to an existing surface, so it is Ted's call, not a
  side effect of a notification feature.
- `TimelineCommand.statusLabel()` gains a `FAILED_SEND` arm. Its `switch` ends in `default -> status`
  so an unmapped value renders as the raw enum string rather than failing — visible, ugly, and
  exactly the kind of thing that ships green. `failed()` already returns true for it, since it
  prefix-matches `FAILED`.
- the page's own wording (`admin-pending-commands.html`) has to say what it now lists.

**Until Q3 is answered, treat §6.1's "the loss leaves a trace" as aspirational.** It is true of the
row in `command_log`; it is not yet true of any surface that volunteers it.

#### `NotifyFamily` (application)

Takes `CommandExecutor`, `BrevoEmailClient` and a **plain `boolean enabled`** — never `EventStore`
(`ApplicationServicesUseCommandExecutorTest`). Two things happen **before** the command exists, and
both deliberately write nothing:

1. **The kill switch and the blank-API-key check.** A disabled or unconfigured notifier must not
   litter `command_log` with rows for mail it never intended to send — and this is what keeps the
   §6 promise that integration tests neither send nor pollute.
2. **The fact-changed fold** (§4.5). A trigger that would tell family something they have already
   been told is a no-op, not a failure; writing a `FAILED_DOMAIN` row for "nothing to say" would
   pollute the pending/failed surfaces this design now relies on for signal. Safe outside the
   command because the reactor executor is single-threaded, so there is no concurrent second trigger
   for one subject.

**The kill switch is constructor-injected as a `boolean`, never read with `@Value`** (2026-09-10).
`src/main/java/dev/ted/jittertravel/application` has **zero** `org.springframework` imports across
136 files — that line is currently held perfectly, and a `@Value` on `NotifyFamily` would be the
first break in it. `EventSourcingConfig` reads the property and passes the value in, the way it
already passes `Clock` and the persister. `BrevoEmailClient.configured()` needs no such treatment:
it is an infrastructure `@Component` and already holds its own `@Value` fields, exactly like
`AeroDataBoxClient`.

`NotifyFamilyCommand(NotifiedSubject subject, Instant requestedAt)` is the record persisted to
`command_log` — the intent, no recipient and no prose (§4.4). It lives next to the boundary that
mints it, the processor; compare `ClearDifferentCityConflict`, which lives in `web` because its
boundary is a web request.

### 4.4 The new event

```java
public record FamilyNotified(
        NotifiedSubject subject,   // sealed: FlightNotification | ConferenceNotification
        NotifiedFact fact,         // enum — what family were told (added 2026-09-10)
        Instant notifiedAt
) implements Event { }

public enum NotifiedFact {
    FLIGHT_BOOKED,
    CONFERENCE_GOING,
    CONFERENCE_NOT_GOING
}
```

**Three fields, all pure — no `summary`, decided 2026-09-09 (Ted).** A draft carried the subject line
as sent, on the argument that re-wording emails later must not change what a past event says went
out. Ted: *"the subject should be recoverable as-sent because the event is recorded, and a
later-changed booking is irrelevant as we know the exact booking as of when this event was
generated — that is one of the main advantages of event sourcing in the first place."* That is
right, and the cost the draft claimed was a naive-rendering cost rather than an inherent one: folding
the log **to this event's own sequence** reproduces the booking exactly as it stood when the mail
went out. Storing the rendered string was buying something the log already gives.

Consequence to know rather than rediscover: `/admin/eventlog` renders payloads, so a `FamilyNotified`
row shows a typed id and an enum, and no prose. `fact` makes that row roughly readable —
`CONFERENCE_NOT_GOING` beside a conference id says what went out — which is a side benefit of the
change below rather than its reason. The presentation-out-of-domain rule is satisfied outright:
`NotifiedFact` is a domain value, not a display string, and the sentence family actually read is
still built in `FamilyNotificationMessages` and still not stored.

**Keyed on the entity, not on the triggering event's id.** Two reasons, and the second is a
behaviour improvement rather than a matter of taste:

1. `StoredEvent.eventId` is an **envelope** field. R11's reasoning — a payload must not depend on
   the store's envelope — applies to an id exactly as it does to a timestamp.
2. `ConferenceAttendanceConfirmed` can legitimately occur **twice** for one conference: its service
   javadoc says re-confirming with a different `basis` is a correction, and the last one wins. Keyed
   on `ConferenceId`, the correction sends no second email — correct, since the family-visible
   content is identical. Keyed on the event id it would send a duplicate saying the same thing.

**But the entity alone is not the whole key — `fact` is the other half (added 2026-09-10).** The
event was originally two fields and the guard was *"is there a `FamilyNotified` for this subject?"*.
That is wrong in a way slice 2 ships broken: a conference is confirmed (email sent, event written),
then declined — and the fold finds the confirmation's event, decides family have already been told
about this conference, and **sends nothing**. The exit email, which §9.1 refuses to let slip to a
later slice precisely so family never hold a stale "he is going", is unreachable from the first day
it exists.

So the question the fold asks is *"has what family believe changed?"*: take the **latest**
`FamilyNotified` for this subject, compare its `fact` with the fact the trigger produces now, and
send only on a difference. Every case falls out of that one rule —

| Sequence | Behaviour | Right? |
|---|---|---|
| confirm | `CONFERENCE_GOING`, nothing prior ⇒ send | ✅ |
| confirm, re-confirm with corrected `basis` | fact unchanged ⇒ silent | ✅ the 2026-09-09 decision, preserved |
| confirm, decline | `GOING` → `NOT_GOING` ⇒ send | ✅ the case that was broken |
| confirm, decline, confirm again | three sends | ✅ each is news |
| decline a conference family were never told about | see the rule below ⇒ silent | ✅ |
| book flight (only ever one `FlightBooked` per `FlightId`) | one send, ever | ✅ |

**The one rule that does not fall out of "did it change", and it is load-bearing: an exit is sent
only where a `CONFERENCE_GOING` was sent first.** With no prior notification the "change" from
nothing to `CONFERENCE_NOT_GOING` is real, and mailing it would tell family Ted is not going to a
conference they never knew he was considering — which is the submission pipeline leaking through the
back door, and against a wider audience than §5's bound was argued for. So: no prior
`FamilyNotified` for the subject ⇒ send only a **positive** fact. Write it as its own named
predicate, not as a condition inside the comparison, because it is a different rule with a different
reason.

**Why store `fact` rather than recompute it.** The alternative that needs no new field is to fold
the log **to the sequence of the last `FamilyNotified`** and recompute what was true then — which is
exactly the argument §9.5 used to drop `summary`, and it is a good argument there. It is weaker
here: `summary` was a *rendered string* the log could reproduce, whereas `fact` is the decision the
notifier made, and a decision that is recomputed can silently change when the folding rules change —
a later edit to `ConferenceProgress` would retroactively alter what the log says family were told.
`fact` is also what makes the comparison a one-line read instead of a sequence-aware fold that
nothing in the codebase does today. Recorded rather than assumed: this is the one place the plan
stores a derived value, and Q5 in §9 is the invitation to overrule it.

`NotifiedSubject` is a **sealed** interface over `FlightId` and `ConferenceId` wrappers, following
the `ScheduledLegId` / `TravelLeg` precedent from `CancelTrainAndOverlappingLegsPlan.md`. Adding a
trigger then means adding a variant, which is a decision the compiler asks for.

**The recipient address is deliberately NOT a field.** It is configuration, and every event is
written verbatim into every backup JSON file — putting a family member's address there makes each
backup a small contact database for no benefit.

The same reasoning applies to `NotifyFamilyCommand` (§4.3), which is kept to
`(NotifiedSubject subject, NotifiedFact fact, Instant requestedAt)`. It is not a domain `Event`, so
the presentation rule does not reach it — but a `FAILED_SEND` row is identifiable by subject, fact
and timestamp without storing prose, so there is nothing for a rendered-string field to buy. `fact`
earns its place on the command for a different reason than on the event: it is what makes a failed
row say *which* notification was lost, which is the whole point of §4.3a.

Registration checklist for the new event (each has a test that fails until done):

- one `register("FamilyNotified", FamilyNotified.class)` line in `EventTypes` — `EventTypesTest`
- a case in `GoldenEventDeserializationTest`
- `INITIAL_SCHEMA_VERSION`; no upcaster, no migration, **backup format stays v3** (additive new
  type, the `TrainCancelled` precedent)

### 4.5 Trigger configuration

**One place in code, because a trigger and its email body are the same list.** You cannot enable
`HotelBooked` without writing what the hotel email says, so a property file can only ever be looser
than the code, never more useful.

`FamilyNotificationMessages.messageFor(...)` is a `switch` over `NotifiedFact` (compiler-exhaustive,
because it is an enum) returning a `FamilyMessage`. **The `switch` over the triggering event is a
separate thing and is *not* compiler-exhaustive** — `Event` is deliberately not sealed (CLAUDE.md:
guard exhaustiveness with scenario tests, not by sealing `Event`), so that one ends in a
`default -> Optional.empty()` and the forcing function is a test, not the compiler. An earlier draft
called it "an exhaustive switch", which invites exactly the sealing the repo has decided against.

The forcing function is `FamilyNotificationTriggerCompletenessTest`, in the style of
`CalendarDayMenuTest`: it iterates **every type registered in `EventTypes`** and requires each to
appear in either the notified set or an explicit not-notified list with a reason. A new event class
then cannot be added without someone writing down whether family hears about it.

**`FamilyNotificationMessages` lives in `infrastructure`**, beside the translator and the client, and
`NotifyFamily` (application) calls it — the same application→infrastructure direction
`CommandExecutor` already takes to reach `EventStore`. Decided rather than left to an import: an
email body is presentation, and CLAUDE.md keeps formatting out of `domain` and prefers it in a
presentation-layer collaborator. `infrastructure` is where this app's non-web presentation already
lives (the iCal feed), and it keeps `application` free of display strings. The alternative — build
the message in the translator and pass a `FamilyMessage` into `NotifyFamily` — is cleaner on paper
and was rejected because the conference message needs the event-stream fold, which arrives through
`CommandExecutor.eventsForDecision()` and belongs to the service, not the reactor.

#### The conference trigger set is the commitment's transitions, not three hand-picked events

**Revised 2026-09-10, and this is the second material change from the 2026-09-09 design.** The
original list — `ConferenceAttendanceConfirmed`, `ConferenceAttendanceDeclined`,
`ConferenceCancelled` — misses both of the paths the code actually takes most often:

- **`TalkAccepted` is an auto-commit.** `ConferenceProgress.accepted()` says so in its own javadoc:
  *"An acceptance makes Ted GOING on its own, **with no confirmation event anywhere** — submitting
  the talk was already the opt-in."* On `/conferences` the `Accepted` link emits `TalkAccepted`, not
  a confirmation. So on the CFP path — the ordinary way Ted gets into a conference — the original
  trigger list fires **nothing**, and §5's flagship example (*"Ted is going to SoCraTes 2026 / His
  talk was accepted."*) is the case that would almost never have been sent.
- **`TalkRejected` is an auto-drop** where `format == ACCEPTANCE_REQUIRED`:
  `ConferenceProgress.rejected()` moves the commitment to `NOT_GOING`, the conference leaves both
  calendars, and no decline or cancellation event is ever written. So §9.1's guarantee — no released
  version can tell family he is going and never that he is not — does not hold for that format.

So the trigger set is defined by its effect, not by a list: **every event that can move
`AttendanceCommitment`.** Today that is `ConferenceAttendanceConfirmed`,
`ConferenceAttendanceDeclined`, `ConferenceCancelled`, `TalkAccepted`, `TalkRejected`. On any of
them, `NotifyFamily` folds that conference's own events and asks what family should now believe:

```
fold the conference's events →
    ConferencePlanned            → name, dates, venue, infoUrl   (the message's content)
    ConferenceProgress           → commitment, speakingStatus, speaking()
    ConferenceCancelled          → gone

gone or commitment == NOT_GOING  → CONFERENCE_NOT_GOING
commitment == GOING              → CONFERENCE_GOING
commitment == WATCHING           → nothing to say, ever
```

**`ConferenceCancelled` is folded separately from `ConferenceProgress`, and this is a real shape of
the code rather than an oversight** (checked 2026-09-10): `ConferenceCalendarProjector` handles it
with `entries.remove(conferenceId)`, a hard removal, while every other conference event is a
`ConferenceProgress` transition. The notifier's fold has to carry the same two-part state.

**`TalkSubmitted`, `TalkWithdrawn` and `InvitedToSpeak` are not triggers**, because they move only
the speaking axis and leave the commitment where it was — which under the fact-comparison rule
(§4.4) would produce no email even if they were. `TalkWithdrawn` on a conference Ted is still
attending is the interesting one and is **Q1** in §9: family were told he is speaking, he no longer
is, and nothing corrects it.

Runtime configuration is only:

| Property (`application.properties`) | Env var | Default | Purpose |
|---|---|---|---|
| `jittertravel.family-notify.api-key` | `BREVO_API_KEY` | *(blank)* | secret; blank ⇒ no-op with a warning |
| `jittertravel.family-notify.recipient` | `FAMILY_NOTIFY_EMAIL` | *(blank)* | the single recipient; blank ⇒ no-op |
| `jittertravel.family-notify.reply-to` | `TED_REPLY_EMAIL` | *(blank)* | `replyTo`; blank ⇒ replies land in the unattended sender mailbox |
| `jittertravel.family-notify.enabled` | `FAMILY_NOTIFY_ENABLED` | `false` | kill switch, no deploy needed |

**Both columns are required, and the property line is what makes the env var work** (2026-09-10).
This repo's idiom is an explicit placeholder in `application.properties` —
`jittertravel.aerodatabox.api-key=${AERODATABOX_API_KEY:}` at line 44 — which binds the two by
writing the binding down. Do not rely on relaxed binding to connect them: an earlier draft paired
`jittertravel.family-notify.enabled` with `JITTERTRAVEL_FAMILY_NOTIFY_ENABLED`, and those two do
**not** bind to each other (relaxed binding strips the hyphen, so that env var names
`jittertravel.family.notify.enabled`). The feature would have shipped permanently dark with nothing
to show for it. Four lines in `application.properties`, in the same commit as the four variables.

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

No conference trigger carries what the email needs. `ConferenceAttendanceConfirmed` carries only
`conferenceId`, `basis`, `confirmedOn`; `TalkAccepted` carries less. **None carries the conference's
name, venue or dates**, so the message cannot be built from the triggering event alone; the service
folds the stream for the matching `ConferencePlanned` (`name`, `startDate`, `endDate`, `venueName`,
`venueAddress`, `infoUrl`) — the same fold `ConfirmConferenceAttendance` already performs for
existence, and R1-compatible because it reads the stream and not a projector. It folds
`ConferenceProgress` in the same pass (§4.5), so the commitment and the speaking line come from one
traversal.

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

**The speaking line has two sources, and only one is an `AttendanceBasis`** (2026-09-10). On the
auto-commit path there is no `ConferenceAttendanceConfirmed` and therefore **no basis at all** — the
line has to come from `ConferenceProgress.speakingStatus()`/`speaking()` instead. So the mapping is
over the folded progress, not over the basis enum: an `ACCEPTED` speaking status gives *"His talk
was accepted."*, a confirmation with `SPEAKING_INVITED` gives the invited wording,
`TICKET_PURCHASED` with no speaking gives no line at all. `AttendanceBasis` remains *includable*
under §5's bound and is what distinguishes an invitation taken up from a ticket bought; it is simply
not the only input. `FamilyNotificationMessagesTest` covers both entry paths, since one of them was
missing from the design entirely until the review.

### Conference exits — the same fold, the other direction

`ConferenceAttendanceDeclined`, `ConferenceCancelled` and `TalkRejected` ship **in slice 2 alongside
the commitment triggers** (§9.1), so there is never a released state in which family can be told he
is going but not that he is not. All fold to the same `ConferencePlanned` for the name, and all
produce `CONFERENCE_NOT_GOING` — sent only where a `CONFERENCE_GOING` went out first (§4.4).

```
Subject: Ted is no longer going to SoCraTes 2026

SoCraTes 2026
Mon 24 Aug – Thu 27 Aug 2026
```

Three notes. The exits are **different facts** — Ted withdrew, the organizers cancelled the event,
the talk was rejected — and CLAUDE.md keeps the first two distinct for good reason, so they get
distinct wording rather than one shared "not going" template. A decline *does* carry a `reason`
(free text, may be blank) and `ConferenceCancelled` carries one too; whether either reaches family
is **Q2** in §9, and the default until answered is no — the email says what happened and not why.

**The rejection wording is its own question and is deliberately not settled here.** `NotifiedFact`
records `CONFERENCE_NOT_GOING` for all three, so the *decision* is uniform, but the sentence is
chosen from the triggering event, and "his talk was rejected" tells family a submission outcome that
CLAUDE.md guards carefully — against anonymous viewers, admittedly, and §5's bound is wider. Q4.

---

## 6. Failure modes, accepted knowingly

Named here rather than discovered later, per the standing "name the losses out loud" rule.

1. **A dropped email is dropped forever — but no longer silently.** At-most-once: a Brevo outage, a
   crash, a rejected task, or a shutdown with work queued means that notification never goes out and
   nothing retries it. Chosen deliberately over a boot-time backlog send, whose failure mode —
   emailing the entire flight history on one bad deploy — is far worse and much harder to undo.
   **Since §4.3 the loss leaves a row in `command_log`** — `FAILED_SEND`, or PENDING on a crash.
   Whether any *surface* volunteers that row depends on §4.3a, which is unbuilt and gated on Q3:
   today `/admin/pending-commands`, the admin home's `pendingCount` and
   `PendingCommandStartupCheck` all filter `status = 'PENDING'` exactly, so a `FAILED_SEND` shows up
   only under `/admin/commandlog?status=FAILED`. **Do not describe this failure mode as "visible"
   until §4.3a ships.** Two holes stay open regardless, which is why each gets its own counter: a
   **rejected task** never reaches a command row at all, and a send suppressed by **read-only mode**
   is refused before the row is written (§4.1).
2. **A crash between the 2xx and the append** leaves an email sent and the command PENDING. For a
   flight this is invisible downstream (there is only ever one `FlightBooked` per `FlightId`); for a
   conference the lost `FamilyNotified` means the fact-comparison in §4.4 has no record of what
   family were told, so the **next** commitment change re-sends from a stale baseline — a
   re-confirmation would send a second, identical "going" email. Note it inverts the meaning of a
   PENDING row — see the caveat in §4.3.
3. **Family is told about commitments and never about changes.** `FlightChanged` sends nothing (no
   `FlightCancelled` event exists in the codebase at all), and neither does a `TalkWithdrawn` that
   leaves Ted attending but no longer speaking. **This was the biggest gap in the feature as
   specified on 2026-09-09 and it is now half-closed**: the conference exits are in the trigger set
   (§4.5) and the fact-comparison (§4.4) makes them reachable, so "Ted is going to SoCraTes" with no
   follow-up when he drops out is fixed. What remains is *changes to a fact already sent* rather
   than *exits from it* — Q1 and the `FlightChanged` item in §9.
4. **Ordering is not guaranteed against the page.** The email is sent from a worker thread after the
   HTTP response has returned, so family can in principle receive it before Ted's browser finishes
   loading the confirmation page. Harmless here, but true.

### Two things that are NOT hazards, checked so nobody re-checks them

- **The existing integration tests cannot send mail, and cannot pollute the event log.** They share
  one in-memory `EventStore` (H6), so every test that books a flight will reach the translator. Two
  independent gates stop it there, both at their defaults: `jittertravel.family-notify.enabled` is
  `false`, and `BREVO_API_KEY` is blank — the `AeroDataBoxClient` idiom. Either alone means no
  request **and** no `FamilyNotified` appended, since the event records a successful send. Nothing
  to opt out of and no test fixture to remember.
  *One thing to expect rather than debug (2026-09-10): the integration tests get the **real**
  executor bean, so worker threads do start and each trigger event does reach `NotifyFamily` — it
  returns at step 0 having written nothing. Harmless, but it means a stack trace from that thread in
  a test log is this feature, and `Runnable::run` is not the fix (§4.1(3)).*
- **Multi-instance adds no new hazard.** Each replica has its own in-memory `EventStore` and only
  dispatches its own appends, so a booking made on one instance produces exactly one email and the
  other never sees it. That is the *existing* staleness limitation from
  `CommandConsistencyEventStore.md`, not a new one this feature introduces — and notably it fails
  toward one email rather than two.

---

## 7. Slices

**Slice 0 — the async seam, no feature.** `EventReactor` (taking `List<StoredEvent>`);
`EventStore.subscribeAsync` + the injected `Executor` (4 constructor call sites) + reactor dispatch,
error counting and rejection handling; the `ExecutorService` bean in `EventSourcingConfig` with
`destroyMethod = "shutdownNow"`; the `EventStoreTest` cases. Ships green with no reactor registered
and no behaviour change, so the seam is reviewable on its own rather than inside a feature diff.

**Slice 1 — flights, end to end.** `ExternalAction` + `CommandExecutor.executeExternalAction`; the
**three** amendments to the PENDING invariant (§4.3 — javadoc, boot log, the page's own wording);
`FamilyNotified` + `NotifiedSubject` + `NotifiedFact` + `NotifyFamilyCommand` + `EventTypes` +
golden sample; `BrevoEmailClient`; `NotifyFamily` with the fact-comparison fold;
`FamilyNotificationTranslator`; `FamilyNotificationMessages` with the flight arm only; wiring in
`EventSourcingConfig` (including the kill switch passed in as a `boolean`); the completeness test;
the four properties **and** the four env vars; the `DEPLOYMENT.md` rows. Deploy dark, verify with
`FAMILY_NOTIFY_EMAIL` pointed at Ted, then repoint and flip.

**Slice 1a — §4.3a, the failure surface.** Widening `findPendingCommands`/`countPendingCommands`,
the `FAILED_SEND` arm on `TimelineCommand.statusLabel()`, the page wording. Separate because it
changes an existing admin surface for *every* command, not just this feature, and it is gated on Q3.
Ship it with slice 1 if Q3 is answered by then; ship slice 1 without it if not, and correct §6.1's
wording in that commit rather than leaving the claim standing.

**Slice 2 — conferences, every commitment transition together.** Adds the `ConferencePlanned` +
`ConferenceProgress` + cancelled fold, the second `NotifiedSubject` variant, and **five** trigger
arms: `ConferenceAttendanceConfirmed`, `ConferenceAttendanceDeclined`, `ConferenceCancelled`,
`TalkAccepted`, `TalkRejected`. Sequenced second because it is the one that needs the fold, and
slice 1 proves the pipe without it. **The five do not split further** (§9.1): shipping the
commitments alone would put a released version in production that can tell family he is going and
never that he is not — and shipping the original three alone would ship a version that mostly never
tells them he is going at all (§4.5).

---

## 8. Tests required

- `EventStoreTest` (extended) — `append` delivers the batch to a registered `EventReactor`;
  **replaying history into projectors delivers nothing to a reactor** (the replay guard, now mostly
  held by the type system but worth pinning anyway); a reactor that throws does not break the
  append or stop the other reactors; a rejected task is counted and dropped rather than thrown;
  **two reactors each receive the full batch** (the `List`-not-`Stream` case from §4.1 — with a
  `Stream` the second gets an `IllegalStateException`, so this is the test that would have caught
  it); **a task still queued when the executor is shut down does not run** (the `shutdownNow`
  decision, which is otherwise invisible until production).
- `FamilyNotificationTranslatorTest` — with `Runnable::run` as the executor, a trigger event reaches
  `NotifyFamily` and a non-trigger event does not. No queue or thread to test: they moved.
  `NotifyFamily` is a test double here, which is what makes `Runnable::run` legitimate — see §4.1(3)
  for the case where it is not.
- `CommandExecutorTest` (extended) — `executeExternalAction` writes the command PENDING **before**
  running the action (assert ordering, not just the end state — it is the whole reason the method
  exists); a throwing action leaves a `FAILED_SEND` row and appends no events; a successful one
  appends and flips SUCCEEDED; read-only mode refuses before any row is written.
- `NotifyFamilyTest` — an unconfigured or disabled notifier writes **no command row at all**; the
  fact-comparison fold writes no command row when the fact is unchanged; a non-2xx leaves a failed
  command and no event; the `FamilyNotified` event is appended only after a successful send; a
  `ReadOnlyModeException` is caught, counted and not rethrown into the worker.
- **`NotifyFamilyTest`, the fact-comparison cases — one per row of §4.4's table**, because the table
  is the specification and every row is a bug that shipped in an earlier draft or nearly did:
  confirm ⇒ send; re-confirm with a corrected `basis` ⇒ silent; **confirm then decline ⇒ send** (the
  case the entity-only key made unreachable); confirm, decline, confirm ⇒ three sends; **decline a
  conference with no prior `FamilyNotified` ⇒ silent** (the positive-first rule, which is a
  different rule from the comparison and needs its own test); `TalkAccepted` with no confirmation
  anywhere ⇒ send `CONFERENCE_GOING` (the auto-commit path, absent from the design until
  2026-09-10); `TalkRejected` on an `ACCEPTANCE_REQUIRED` conference already notified ⇒ send
  `CONFERENCE_NOT_GOING`; the same on a `CALL_FOR_PAPERS` conference ⇒ silent, because the
  commitment does not move.
- `BrevoEmailClientTest` — `MockRestServiceServer`; assert the `api-key` header and the exact JSON
  body; blank key ⇒ no request at all.
- `FamilyNotificationMessagesTest` — the exact body for each arm, asserted as whole lines rather
  than bare words (the precise-assertions rule); the speaking line is produced on **both** entry
  paths (a confirmation carrying a basis, and an auto-commit carrying none — §5); every
  `AttendanceBasis` value maps to its own sentence; a decline, an organizer cancellation and a
  rejection produce **different** text; the flight body labels both zones. **Also `doesNotContain`
  for what is deliberately left out** — a decline says what happened and not why, and no message
  contains a `reason` field's text until Q2 says otherwise — because absence is the half that stops
  being pinned when the message is reworded.
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
   *The 2026-09-10 review found this decision was not implementable as designed — twice. The
   entity-only idempotency key made the exit unreachable (§4.4), and `TalkRejected` drops a
   conference with no exit event at all (§4.5). Both are now fixed; the decision itself stands and
   is what caught them.*
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

### Opened by the 2026-09-10 review — these need Ted

Ordered by what blocks what. **Q3 blocks slice 1a; Q1, Q2 and Q4 block slice 2; Q5 is a chance to
overrule a call already folded in.** Slice 0 and slice 1 are buildable as written.

**Q1 — does family hear when Ted stops speaking but still attends?** `TalkWithdrawn` leaves the
commitment where it was, so under the fact-comparison rule it sends nothing. But the "going" email
family received said *"His talk was accepted."*, and after a withdrawal that sentence is false while
the trip is still on. Three ways out: (a) leave it — the trip is the news, the talk is detail, and
family will hear about it in conversation; (b) add a `CONFERENCE_GOING_NOT_SPEAKING` fact, making
`TalkWithdrawn` a trigger and the speaking status part of what is compared; (c) drop the speaking
line from the email entirely, so nothing can go stale. **My read: (a).** The feature is
"family learn about the trip", and (b) doubles the fact vocabulary to correct one clause. But (c) is
the honest answer if you would rather the emails never say anything they might later contradict.

**Q2 — does the `reason` on a decline or an organizer cancellation reach family?** Both
`ConferenceAttendanceDeclined` and `ConferenceCancelled` carry free text (may be blank). §5's bound
permits it, and *"the organizers cancelled — venue flooded"* is exactly the sentence a person would
send. Against: it is free text Ted wrote for himself in an admin form, so it is the one field in
these emails not written with an audience in mind. Default until answered: **not included**, which
is what `FamilyNotificationMessagesTest` asserts.

**Q3 — should `/admin/pending-commands` list failed commands, or only pending ones?** §4.3a. The
narrow fix is to widen `findPendingCommands` to `status = 'PENDING' OR status LIKE 'FAILED%'` and
rename the page's heading accordingly. The consequence is not local: `countPendingCommands` feeds
the `pendingCount` badge on the admin home and `PendingCommandStartupCheck`'s boot warning, so
widening it makes **every** past `FAILED_DOMAIN` row — a bad form submission from a year ago —
resurface as something demanding attention. Options: (a) widen both, and add an "abandon" for
failed rows so the list can be emptied; (b) widen only the *list*, leave the count and the boot
warning on PENDING; (c) leave it alone and give `FamilyNotified` failures their own line on the
admin home instead. **My read: (b)** — it is the smallest change that makes the row findable, and
it does not turn a boot warning into noise. But (a) is the one that actually makes the surface
mean "things needing a human", which is what it is for.

**Q4 — what does the email say when a rejection drops a conference?** `TalkRejected` on an
`ACCEPTANCE_REQUIRED` conference removes it from every calendar. Family were told he is going, so
they must be told he is not — but the natural sentence, *"his talk was rejected"*, is a submission
outcome, which CLAUDE.md guards more carefully than anything else about a conference. §5's bound
(anything an OWNER surface shows) permits it and the audience is four people who know his working
life, so this is not a redaction violation — it is a question about what Ted wants said. Options:
(a) say it plainly; (b) use the neutral *"Ted is no longer going to X"* used for the other two exits,
which says the true and useful thing without the outcome; (c) do not send at all for this path.
**My read: (b)** — it is the one exit wording that is already written, it needs no new decision
about what family should know, and (c) reintroduces the stale-fact problem §9.1 refuses.

**Q5 — store `NotifiedFact` on the event, or recompute it by folding to that event's sequence?**
§4.4 folds in the stored field. The alternative is purer and is the same argument that dropped
`summary` on 2026-09-09: fold the log to the sequence of the last `FamilyNotified` and recompute
what was true then, storing nothing derived. It costs a sequence-aware fold that nothing in the
codebase does today, and it makes the "what did we tell family" answer change if the folding rules
ever change. Stated because the `summary` decision is recent and the reasoning here deliberately
does not follow it.

### Still open from 2026-09-09

- **`FlightChanged`** notifies nobody, deliberately. There is no `FlightCancelled` event in the
  codebase at all, and `FlightChanged` is a full snapshot, so "what changed" means diffing against
  prior state rather than reading the event. Left for a later slice with its eyes open: a flight
  moved by six hours is exactly the kind of thing family would want, and today they get silence.
  Note that §4.4's fact-comparison does **not** help here — a changed flight is the same fact
  (`FLIGHT_BOOKED`) with different content, so it needs either a content comparison or a second
  fact, and that is the design work the later slice owes.
- ~~**`/admin/eventlog` shows a `FamilyNotified` as a typed id with no prose**~~ — largely closed by
  `NotifiedFact` (§4.4): the row now reads as a subject plus what was said about it. The exact
  sentence is still not recoverable without folding to that sequence, which remains unbuilt and
  unneeded.

---

## 10. Pre-Push tasks (belong in `Pre-Push-Tasks.md` with the commit)

- [ ] Brevo account created; `notifications@jittertravel.com` added as a sending identity.
- [ ] **SPF and DKIM DNS records** published for `jittertravel.com` and verified in Brevo.
      *Skipped ⇒ mail is accepted by the API and lands in spam, which looks exactly like the feature
      not working.* **Before the push.**
- [ ] `BREVO_API_KEY` set on the **app** Railway service (secret; variables are scoped per service).
- [ ] `FAMILY_NOTIFY_EMAIL` set on the app service — **Ted's own address for the first rollout**,
      repointed at the family address or group alias only after one real notification has been
      received and read (§3, "Deploy dark"). *Skipped ⇒ the first email family ever get is also the
      first anyone has seen, and a typo sends travel detail to a stranger.*
- [ ] `TED_REPLY_EMAIL` set on the app service — where a family reply lands (§9.3). *Skipped ⇒
      replies go to the unattended `notifications@` mailbox and are never seen.* **After the
      rollout**, but before telling family the emails are live.
- [ ] `FAMILY_NOTIFY_ENABLED` left `false` for the first rollout; flipped to `true` only after a
      real booking is confirmed to have produced no email and no `FamilyNotified`.
      *Skipped ⇒ the first booking after the rollout emails family from an unverified sender.*
      **After the rollout.** (Named `FAMILY_NOTIFY_ENABLED`, not
      `JITTERTRAVEL_FAMILY_NOTIFY_ENABLED` — see §4.5 for why the longer name does not bind.)

### And in `DEPLOYMENT.md`, in the same commit

**Not optional and not a duplicate of the list above.** CLAUDE.md: *"a new variable goes in both,
and they say different things"* — a `Pre-Push-Tasks.md` box stops being true the moment it is
ticked, while `DEPLOYMENT.md`'s table is the standing description of a configured instance. Skipping
the second is exactly how `REMEMBER_ME_KEY`, `CALENDAR_FEED_TOKEN` and `JITTERTRAVEL_BASE_URL` were
all missing from that table on 2026-09-09.

- [ ] Four rows in the variables table (currently at `DEPLOYMENT.md:168`), each saying what happens
      when it is **absent**: `BREVO_API_KEY` (optional, secret — unset ⇒ no mail, warning logged);
      `FAMILY_NOTIFY_EMAIL` (optional — unset ⇒ no mail); `TED_REPLY_EMAIL` (optional — unset ⇒
      replies reach nobody); `FAMILY_NOTIFY_ENABLED` (optional, default `false` — unset ⇒ feature
      off).
- [ ] `BREVO_API_KEY` added to the **secrets** note below that table (`DEPLOYMENT.md:177`), beside
      `AERODATABOX_API_KEY`.
- [ ] Note that all four have **empty defaults**, so the app boots happily with the feature silently
      off — the `CALENDAR_FEED_TOKEN` failure shape, not the `TED_PASSWORD` one. That is the right
      choice here (a notifier that cannot send is not a reason to refuse a deploy), but it is the
      shape that hides a misconfiguration, which is why §3's verify-then-flip step exists.

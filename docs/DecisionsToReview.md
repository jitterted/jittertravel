# Decisions To Review

A running register of decisions made **during implementation** that Ted has not yet examined.
Each one is a place where the code now commits to something a reasonable person could disagree
with — not a list of everything that was done.

Work through them one at a time. When a decision is settled, set its **Status** to `Accepted` or
`Changed`, note the outcome in a sentence, and move it to the "Settled" section at the bottom. Add
new entries here as later slices raise them.

Status values: `Needs review` · `Accepted` · `Changed`

---

## From the Cancel Hotel slice (2026-08-07)

Roughly highest-stakes first.

---

### D1. `CommandExecutor.eventsForDecision()` — a read method on the write-path class

**Status:** `Needs review` — discussion in progress, see `DecisionContextQueryDesign.md`
**Where:** `application/CommandExecutor.java`
**Owned by:** `DecisionContextQueryDesign.md` (2026-08-07). Direction agreed — a tagged/typed query
port replaces this method — but the slice is paused behind the export/import rethink.

I added a method that exposes the whole event stream to application services:

```java
public Stream<StoredEvent> eventsForDecision() {
    return eventStore.findAll();
}
```

**Why.** You chose folding from the event stream (R1) over reading a projector. But
`ApplicationServicesUseCommandExecutorTest` forbids an `EventStore` constructor parameter anywhere
in `application`, so a service cannot reach the stream on its own. `CommandExecutor` is the one
class exempted from that rule, so the read arrives through it.

**What to push on.** `CommandExecutor` was purely a write-path collaborator; it now has a read
method. The name and Javadoc try to keep it honest ("not for building views"), but nothing
*enforces* that — any service could use it to build a read model and quietly re-create the
staleness the projectors already handle properly.

**Alternatives not taken:**
- A separate `EventStreamReader` interface implemented by `EventStore`, injected into services that
  fold. Passes the architecture test (it checks for the `EventStore` type specifically) and keeps
  reading and writing as distinct collaborators. Costs one more interface and one more bean.
- Widen the architecture test to allow `EventStore` in services that only read from it — hard to
  express, and weakens a rule that exists for a good reason.

**If you reject it:** `CancelHotel` changes shape, and so does anything later that folds.

---

### D2. The fold reads the entire event log on every cancellation

**Status:** `Needs review` — discussion in progress, see `DecisionContextQueryDesign.md`
**Where:** `application/CancelHotel.java`
**Owned by:** `DecisionContextQueryDesign.md` (2026-08-07). The "second concrete caller" question
below is answered there — converting the three projector-reading services alongside Cancel gives
four callers at once.

`eventsForDecision()` streams *all* events and the fold filters for one `HotelBookingId`. There is
no tagged or filtered query — `TaggedEventStoreQueryingDesign.md` describes one and is explicitly
deferred until a second caller demands it.

**Why.** It matches how everything else in the codebase reads (`eventStore.findAll()` everywhere),
and at your data volume the cost is nothing.

**What to push on.** This is arguably that "second concrete caller" the tagged-query design was
waiting for. Cancelling is rare, so I did not treat it as one — but you may disagree, and it is
cheaper to decide now than after three more folds exist.

---

### D4. `LocationAuditProjector` deliberately does *not* drop a cancelled booking

**Status:** `Needs review`
**Where:** `application/LocationAuditProjector.java`

Every other hotel projector removes the booking on `HotelBookingCancelled`. This one does not, and
carries a comment plus a test saying so.

**Why.** The `HotelBooked` row stays in `event_log` forever and the read-time upcaster resolves its
location on *every* replay. If the audit stopped reporting that location, an unresolvable one could
hide there and break startup — which is the exact failure the audit exists to prevent.

**What to push on.** I am confident in the reasoning, but it is the one place where "cancelled means
gone" is not true, and it is the kind of asymmetry that surprises people later. Worth you agreeing
it is right, since the test now locks it in.

---

### D6. `BookedHotelView` carries a pre-resolved `cancelDeadlinePassed` flag

**Status:** `Needs review`
**Where:** `application/BookedHotelView.java`, `application/BookedHotelsProjector.java`

The record gained a tenth component plus a `withDeadlineEvaluatedAt(Instant)` copy method, set in
`views(TimeView, Instant)`. (It is up to twelve components and two copy methods as of 2026-08-13,
when `cancelled` / `cancellationReason` arrived with the tombstone row — see S5, which makes the
"clunky" concern below worse rather than better.)

**Why.** The renderer needs "has the deadline passed?" but cannot take an `Instant`:
`TimeFilterToggleConventionTest` discovers every static `render(List, TimeView)` and invokes it
reflectively, substituting `List.of()` for any parameter type it does not recognise. Adding a third
parameter fails that test. `now` already arrives at `views(...)` from the controller boundary, so
the flag is resolved there.

**What to push on.** A ten-component record with a hand-written copy method is clunky, and the flag
is display-only state living on a view record. The alternative is loosening the convention test to
tolerate extra parameters — which weakens a guard you deliberately made strict.

---

### D8. Smaller calls, listed for completeness

**Status:** `Needs review`

- **Separate `CancelHotelController`** rather than another method on `ChangeHotelController` —
  follows one-slice-per-controller, but the cancel form lives on the change page, so the two are
  coupled in the UI.
- **`/booked-hotels/*/cancel` added explicitly** to `SecurityConfig` rather than widening to
  `/booked-hotels/*/**`. Explicit means every future per-item action needs its own line (and its own
  `AuthorizationMatrixTest` row) — deliberate, but it is a recurring small tax.
- ~~**Confirmation is an inline `onclick="return confirm(...)"`** on the cancel button.~~ Overtaken
  by events: Cancel Hotel moved to its own confirmation page (`cancel-hotel.html`), so there is no
  inline JS left to test. The stray-click concern now rests on that page plus S5's Undo backlog
  item.
- **`reason` is a free-text input with no length limit or validation**, normalised `null` → `""`.
- **`HotelBookingCancelled` carries only the id and the reason** — no `cancelBy`, no check-in, no
  timestamp of its own beyond the event's. Per the plan; means the event alone cannot tell you
  whether the cancellation was inside the free window.

---

## Settled

### S7 (was D7). `CancelHotelRequest.commandId()` returns a fresh random UUID

**Status:** `Accepted` — the concern it raised is now moot (2026-08-11).
**Where:** `web/CancelHotelRequest.java`

Follows `ChangeHotelRequest` and `ClearDifferentCityConflict`.

**Original concern (obsolete):** it was framed around command-replay *import* resumability —
re-importing a backup would re-apply this command under a new id rather than skip it. That model no
longer exists: backup/restore is event-oriented (`EventOrientedBackupRestorePlan.md`, 2026-08-11),
restoring stored **events** verbatim and never re-executing commands, so a request's `commandId()`
is minted once at the boundary and never replayed from a backup. There is nothing left to review
here — a fresh random command id at the controller boundary is the correct, uniform pattern.

---

### S3 (was D3). `CancelHotelContext.checkIn` is nullable, meaning "no check-in gate"

**Status:** `Changed` — settled 2026-08-13
**Where:** `domain/CancelHotelContext.java`, `domain/CancelHotelCommand.java`

**Outcome: moot — the gate itself was removed, so there is no nullable `checkIn` left to shape.**
Ted's reasoning: the gate protected against a harmless action. Cancelling happens *with the hotel*,
in the real world; entering it in JitterTravel is a separate manual step that routinely lags behind,
so "check-in has passed" was never evidence that the cancellation was wrong — only that the
data entry was late. Refusing it blocked recording something that had already happened. (The
eventual fix is piping booking emails in automatically; until then the lag is expected.)

`CancelHotelContext` is now `bookingExists` alone — no `checkIn`, no `now` — which retires both the
null-means-two-things problem and the proposed `CheckInGate` sealed type in one move. Deleted:
`CannotCancelAfterCheckIn`, the controller's catch branch and the `Clock` it injected, and the
template's error banner. The no-gate property is now structural rather than tested: there is
nothing time-shaped in the context for the command to consult.

The safety argument for removing it depends on a mistaken cancel being cheap to reverse, which is
why S5's Undo item exists.

---

### S5 (was D5). Hard removal — no tombstone, no "Cancelled" row, no undo

**Status:** `Changed` — settled 2026-08-13
**Where:** `application/BookedHotelsProjector.java`, `web/BookedHotelsRenderer.java`

**Outcome: reversed for `/booked-hotels`, unchanged everywhere else, and undo is backlogged.**
The undo point was indeed the crux — it is what made removing the check-in gate (S3) safe to
reason about, and it turned out not to hold.

- `BookedHotelsProjector` now keeps a **tombstone** instead of removing: a greyed-out row, hotel
  name as plain text (no maps link), a "Canceled" badge replacing Tentative/Final, the cancellation
  reason as a tooltip, and no actions. It is the only hotel read model that does — calendar,
  itinerary, schedule problems, both tentative-hotel projectors and the hotel details view still
  drop the booking, so `/booked-hotels` is now the one surface where a cancellation is visible.
  Tombstones show under **FUTURE**, not only ALL, so a cancellation you just made appears where you
  are already looking.
- The row is deliberately action-free because **Undo Cancel** is the action that belongs there.
  That is now a tracked backlog item (`Future_Feature_Slices.md`, indexed in `Backlog.md`) rather
  than an accepted gap: re-entry by hand still mints a new `HotelBookingId` and loses the
  original's history.
- Phase 3's `replacesHotelBookingId` consequence is **partly resolved**: the replaced booking is
  now renderable on `/booked-hotels`, though still nowhere else.
- D4 (`LocationAuditProjector` keeping cancelled locations) is no longer the *only* asymmetry, so
  its "surprising exception" framing is weaker than when it was written — the rule is now
  "cancellation removes, except where a surface exists to show it".

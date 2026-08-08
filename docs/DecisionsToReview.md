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

### D3. `CancelHotelContext.checkIn` is nullable, meaning "no check-in gate"

**Status:** `Needs review` (you chose the approach; the *shape* is what needs a look)
**Where:** `domain/CancelHotelContext.java`, `domain/CancelHotelCommand.java`

You picked "make the gate's checkIn optional" over carrying a redundant `checkIn` on the request.
The result is a domain command with a null-means-something branch:

```java
if (context.checkIn() != null && !context.now().isBefore(context.checkIn().utc())) {
    throw new CannotCancelAfterCheckIn(...);
}
```

**What to push on.** `null` here means two different things depending on the caller: "the booking
does not exist" (live path, where `bookingExists` is also false) and "do not apply this gate"
(import). The Javadoc explains it, but a reader hitting the `if` first has to go look.

A more explicit shape would be a small sealed type — `CheckInGate.At(ZonedTimestamp)` vs
`CheckInGate.None` — which names the import case instead of encoding it as absence. More
ceremony for one branch; your call whether it earns it.

**Related:** you asked to revisit export/import more broadly before another command needs folded
context. That is tracked in `Backlog.md` and is the bigger version of this question.

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

### D5. Hard removal — no tombstone, no "Cancelled" row, no undo

**Status:** `Needs review` (recorded as decided 2026-08-07 in the plan; confirming the consequences)
**Where:** all seven view projectors

A cancelled booking vanishes from every view, including `/booked-hotels?filter=all`.

**What to push on, concretely:**
- There is no way to see what you cancelled without reading `/admin/eventlog`.
- There is no undo. Cancelling by mistake means re-entering the booking by hand, with a new id.
- Phase 3's `replacesHotelBookingId` will point at a booking that is not renderable anywhere, so
  the link is data-only — the plan already notes this, but it is a real consequence of D5.

A "Cancelled" row under the ALL filter would cost one branch per projector and a `status` on the
view. You decided against it; I want to be sure the undo point was part of that.

---

### D6. `BookedHotelView` carries a pre-resolved `cancelDeadlinePassed` flag

**Status:** `Needs review`
**Where:** `application/BookedHotelView.java`, `application/BookedHotelsProjector.java`

The record gained a tenth component plus a `withDeadlineEvaluatedAt(Instant)` copy method, set in
`views(TimeView, Instant)`.

**Why.** The renderer needs "has the deadline passed?" but cannot take an `Instant`:
`TimeFilterToggleConventionTest` discovers every static `render(List, TimeView)` and invokes it
reflectively, substituting `List.of()` for any parameter type it does not recognise. Adding a third
parameter fails that test. `now` already arrives at `views(...)` from the controller boundary, so
the flag is resolved there.

**What to push on.** A ten-component record with a hand-written copy method is clunky, and the flag
is display-only state living on a view record. The alternative is loosening the convention test to
tolerate extra parameters — which weakens a guard you deliberately made strict.

---

### D7. `CancelHotelRequest.commandId()` returns a fresh random UUID

**Status:** `Needs review`
**Where:** `web/CancelHotelRequest.java`

Follows `ChangeHotelRequest` and `ClearDifferentCityConflict`.

**What to push on.** Import resumability works by skipping commands whose id is already in
`command_log`. A random id means re-importing the same backup re-applies this command under a new
id rather than being skipped. That is already true of change and clear-conflict commands, and per
your wipe-then-import workflow it does not bite you — but it does mean "resumable import" is not
uniformly true, and the plan had suggested a *stored* id here specifically to avoid that.

---

### D8. Smaller calls, listed for completeness

**Status:** `Needs review`

- **Separate `CancelHotelController`** rather than another method on `ChangeHotelController` —
  follows one-slice-per-controller, but the cancel form lives on the change page, so the two are
  coupled in the UI.
- **`/booked-hotels/*/cancel` added explicitly** to `SecurityConfig` rather than widening to
  `/booked-hotels/*/**`. Explicit means every future per-item action needs its own line (and its own
  `AuthorizationMatrixTest` row) — deliberate, but it is a recurring small tax.
- **Confirmation is an inline `onclick="return confirm(...)"`** on the cancel button. Not covered by
  the `js` tier, and it is the only thing standing between a stray click and an unrecoverable
  cancellation (see D5).
- **`reason` is a free-text input with no length limit or validation**, normalised `null` → `""`.
- **`HotelBookingCancelled` carries only the id and the reason** — no `cancelBy`, no check-in, no
  timestamp of its own beyond the event's. Per the plan; means the event alone cannot tell you
  whether the cancellation was inside the free window.

---

## Settled

*(nothing yet)*

# Per-role read models — should FAMILY have its own projectors?

> **Status: `exploration`, not started. Deadline to explore: Tuesday 2026-09-08** (set by Ted,
> 2026-09-04 — "i'd like to explore the multiple projectors soon"). Nothing is designed here yet;
> this doc exists so the session that does it starts from the analysis already done rather than
> redoing it.

## The question, in Ted's words

> *"does this point to more finer-grained and separate read model projectors? per-role?"*

Asked while deciding how the owner's conference name reaches a detail page that FAMILY may not open
(`ConferenceDetailAndChangePlan.md`, Q5). The immediate case is small; the question underneath it is
not.

## What is true today

**FAMILY is served the OWNER's read model, and the renderer declines to draw the owner-only parts.**
`CalendarController` picks `PublicCalendarProjector` for a stranger and `CalendarAggregator` for
"everyone else" — owner and family alike — and `CalendarViewBuilder` then gates on `isOwner`.

**Five `EntryDetails` records already carry an owner-only path that family receives and never sees:**

| Record | Field |
|---|---|
| `EntryDetails.Gathering` | `editPath` |
| `EntryDetails.Flight` | `editPath` |
| `EntryDetails.Train` | `editPath` |
| `EntryDetails.Lodging` | `editPath` |
| `EntryDetails.GroundTransfer` | `cancelPath` |

A conference `detailPath` would be the sixth. **So this is not a compromise the conference work
introduces — it is the established shape**, and the question is about the whole calendar.

Only two places gate content on `isOwner` in `CalendarViewBuilder`: the day menu (line 284) and
`ownerActions` (line 378). Every owner/family difference today is **an action being absent**.

## The case for splitting

**The repo already believes in per-role read models at the boundary that matters.**
`PublicCalendarProjector` is exactly that, and CLAUDE.md is emphatic about why it beat
`CalendarEntryRedactor`: an allow-list built straight from events, where "a field it never reads
cannot leak", against a deny-list that stripped fields from a read model that already held them.

**Family is currently served by the deny-list pattern one tier up** — the renderer strips. That is
the arrangement the anonymous work rejected, sitting one role over.

## The case against, for now

**The argument that justified the public split was specifically about anonymous.** CLAUDE.md:
"anonymous viewers are a first-class threat model", "`/calendar` is the one page anonymous visitors
can see". Family is Ted's family. The concern is "do not show them owner admin surfaces", not "stop
a stranger on the internet learning where Ted sleeps". A path sitting in a record that nothing
renders is untidy; it is not a disclosure.

**R12 makes the cost higher than it looks.** A read model is built from events alone, never from
another read model — so a FAMILY projector may **not** filter the owner's, it has to fold the events
again. That is the five calendar projectors plus `CalendarAggregator` plus the itinerary's,
duplicated or parameterized, to remove a class of tidiness problem.

## The trigger to watch for

**Today every owner/family difference is an action being absent, which a renderer expresses
perfectly well by omission.** The day a difference becomes **a field with a different value** for
family — a different label, a coarser time, a redacted city — omission stops being expressive, and
the split earns itself. That is the signal, not the count of stripped fields.

## A cheaper middle step, if it bites before then

Collapse the scattered owner-only paths into **one component per details record** — one thing to
omit, one place to test — which also makes a later projector split mechanical rather than
archaeological. Six existing users, so it is not premature abstraction. Named here so the choice is
not all-or-nothing; not proposed.

## Questions the exploration should answer

- Does FAMILY differ from OWNER anywhere by **value** today, or only by **absent actions**? (The
  itinerary and `/schedule-problems` have not been audited for this — only the calendar has.)
- If a FAMILY projector existed, what would `CalendarAggregator` look like — three aggregators, or
  one parameterized by audience? R12 constrains the answer.
- Does the same question apply to `/itinerary`, which is `hasAnyRole("FAMILY","OWNER")` and builds
  its own entries?
- Is the "one owner-actions component per record" middle step worth doing **regardless**, as it
  makes the boundary explicit and testable in one place?

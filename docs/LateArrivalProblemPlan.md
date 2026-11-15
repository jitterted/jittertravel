# Late Arrival — a schedule problem for arriving after an event has begun

Status: `open` — filed 2026-11-14, nothing designed or built. Split out of the fix that made
`ScheduleTimeline` sort by clock time (Ted, 2026-11-14).

## Where it came from

`/schedule-problems` reported two false Missing Travel rows on 2026-11-20: a palace tour in Potsdam
12:10–16:00, a 16:00 transfer to Berlin, a Motel One check-in at 18:00. The timeline ordered each
day by role — every stay **and event** ended in a morning slot and began in an evening one — so the
tour's 12:10 start sorted after the transfer and the walk invented Berlin → Potsdam → Berlin.

The fix sorts legs, events and checkouts by clock time and keeps only **check-ins** in the evening
slot (a check-in is the hotel's official time, not Ted's arrival; four production stays had one
earlier than the flight that got him there). Clock order exposed a case the old ordering hid:

- DDD Europe opens at 09:00; the train out of Gembloux leaves 10:00 and lands in Antwerp 11:30.
- Train lands at 09:36 for a conference starting 09:00.

The rule shipped with the fix: **a booked leg into the event's city that lands after the event has
begun but before it ends counts as getting there** (`ScheduleTimeline.reachedLate`). Nothing is
reported — the same silence the old ordering gave. A leg landing *after* the event ends is still
Missing Travel.

## What this plan is for

Ted asked whether the real problem — *"I'll be late: the travel is there, but I arrive after it
begins, though before it ends"* — should be reported instead of silenced. This is that slice.

## Open questions (none decided)

1. **Is lateness always a problem?** Often it is chosen: skipping the opening morning of a three-day
   conference, arriving at the dinner after the starter. A problem that cannot be dismissed stays
   amber until the event starts, and teaching Ted to read past amber costs the problems that
   matter. So it probably needs a "that's fine" action — a new event per occurrence, like
   `DifferentCityConflictCleared`, and with it a write path.
2. **Threshold?** Ten minutes late to a dinner versus missing a whole conference day.
3. **Fix links** — the leg's change page? None?
4. **Wording** for the list row and the band (`ProblemBand`), via an HTML mockup before building.

## What it touches (measured 2026-11-14)

`ScheduleProblem` is sealed; seven production classes switch over it: `ScheduleProblem` (the record
and `relevantUntil`), `ScheduleTimeline` (detection — `reachedLate` already finds exactly these
cases), `ScheduleGapProjector.firstDate`, `ProblemKey`, `ProblemBand`, `ProblemFix`,
`ScheduleProblemsRenderer`. Roughly the size of the overlapping-travel problem, plus the dismiss
path if question 1 says so. Problem colouring rule applies: it wears the same amber as every other
problem.

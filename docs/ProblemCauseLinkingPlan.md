# Plan: Cause-linking for schedule problems

> **Status: `open`** — written up 2026-09-18 (Ted), nothing built. Split out of
> `PrivateEventMatchingLocationPlan.md` D5, which is its second and sharper motivating case.
>
> **Read the two costs in "What it costs" before starting.** This touches the timeline's core walk,
> which also feeds `awayDays()`, `locationByNight` and the missing-hotel sweep — it is a bigger
> change than any one fix link it enables.

## The gap, stated

A `ScheduleProblem` mostly cannot say **which entry caused it**, so `/schedule-problems` cannot
offer a fix that acts on that entry.

Today the tree works around this in three different ways, which is the tell that it is one problem
and not three:

- **`ProblemFix.java:79-87`** — `SchedulingConflict` gets an empty fix list, with a comment saying
  its two sides are names, cities and times with no ids, and either may be a gathering or a private
  event, so a link would need a kind+id reference the record does not carry.
- **`ScheduleProblemsRenderer.java:33`** — a greyed, non-interactive **Fix** with the `title`
  *"Editing a gathering from here arrives with cause-linking"*, the honest presentation limit that
  `archived/ProblemCalendarPlan.md` F6 specified.
- **`ProblemKey.java:68-70`** — the same note again, at the point a key is derived.

All three named **"slice 4"** until 2026-09-18, and that slice had already shipped (clash markers,
`archived/ProblemCalendarPlan.md`, 2026-08-20) without cause-linking — so the references pointed
nowhere. They name this document now; see Open question 4 for what that repointing did and did not
touch.

## Why it is worth doing now — the second user

Until 2026-09-18 the only motivating case was `SchedulingConflict`, whose fix is *"go and edit one of
these two things"* — vague enough that "greyed, with a reason" was a defensible end state.

`PrivateEventMatchingLocationPlan.md` supplies a second and much sharper one. A conference in Lone
Tree, CO and a dinner in Centennial, CO, four miles apart, raise a `MissingTravel` whose right fix is
one click: **set the dinner's matching location to Lone Tree.** The page that does it exists and
works (`/planned-private-events/{id}/matching-location`), the problem report is exactly where Ted is
standing when he wants it, and it cannot be offered — because `MissingTravel` does not know which
evening raised it.

Two users with two different fixes is the bar CLAUDE.md's "no abstraction before the second user"
rule sets, and this clears it.

## Where the id is dropped

Twice, and both are deliberate simplifications that have outlived their reason.

1. **`ScheduleGapProjector:159`** holds `Map<PrivateEventId, Occupancy>` — it has the id as the map
   key and drops it building the value.
2. **`ScheduleTimeline.Occupancy` (`:581`)** is `(name, city, startsAt, endsAt, kind)`. `Kind` says
   *what sort of thing* but never *which one*. `Point` (`:485`) is
   `(day, rank, role, city, moment, conferenceName)` — it carries a **display string** for
   conferences and no id for anything.

So by the time `gapLeaving` (`:415`) builds a `MissingTravel`, the causing entry is two records gone.

## The shape to build — mirror `TravelLeg`

The tree already solved this once, for the other half of the timeline.
`CancelTrainAndOverlappingLegsPlan.md` slice 2 gave `Movement` a sealed `TravelLeg` identity so
`OverlappingTravel` could link to cancel pages. Its javadoc states the property that matters:

> *"Sealed with typed ids rather than an `(enum kind, String id)` pair, so a switch over it is
> exhaustive: the day Cancel Flight ships, `ProblemFix` stops compiling until someone writes down a
> flight's fix link."*

**`Occupancy` wants the same thing.** A sealed `OccupancyRef` over `ConferenceId` / `GatheringId` /
`PrivateEventId`, carrying `label()`, `identity()` and `detailsPath()` exactly as `TravelLeg` does,
threaded `Occupancy` → `Point` → `MissingTravel` → `ProblemFix`. `Occupancy.Kind` then becomes
redundant and should go, rather than sitting beside a sealed type that says the same thing better.

### Only the arrival side needs it

This is what makes the change smaller than it first looks, and it was verified against the real
detector while building the private-event feature.

`gapLeaving(String fromCity, ZonedTimestamp lastMoment, Point arrival)` takes the origin as a **bare
string** — by then the origin's `Point` is gone — and the destination as a `Point`. Pulling the
origin thread too would mean reworking the walk's state, not just widening a record.

It does not need pulling, for two reasons found by `PrivateEventMatchingLocationPropagationTest`:

- **The dinner raises exactly one gap, not two.** An occupancy contributes a rank-2 `REQUIRE` at its
  start and a rank-0 `LEAVE` at its end (`ScheduleTimeline:461-464`). A `LEAVE` asserts nothing about
  where Ted must be, so nothing asks for Lone Tree again after the dinner and no return journey is
  reported. *(The first draft of the private-event plan assumed a symmetric pair and was wrong; the
  test corrected it.)*
- **Even where two gaps do appear, one fix collapses both.** Re-matching the dinner removes the
  cause, so both rows go at once — the fix does not have to be offered on each.

So: `Point` carries the `OccupancyRef` of whatever raised it, `MissingTravel` carries the arrival's,
and the origin side stays a string.

### `ProblemFix` then switches on it

```java
case ScheduleProblem.MissingTravel gap -> travelFixes(gap);   // + a rematch fix when the
                                                              // arrival is a private event
```

A private event's arrival adds **"Same place as where I'm staying"** pointing at
`/planned-private-events/{id}/matching-location`.

**A gathering's arrival gets the same fix, and the page already exists**:
`ChangeGatheringController` maps GET/POST `/planned-gatherings/{gatheringId}`, and
`change-gathering.html:188` carries the `locationForMatching` input with the same hint the plan form
uses. So a gathering needs **no new page at all** — only the `OccupancyRef` that says which one.
(An earlier draft of this document said gatherings had "no way to change it either", which was
simply wrong; the Change Gathering flow has been there all along.)

A conference's arrival gets nothing: `plan-conference.html` does not even expose the field
(`Cleanup_Tasks.md:131`), and there is no conference edit at all.

## What it costs

Both of these are reasons to do it as its own change, not to fold it into a feature.

1. **Blast radius.** `Occupancy` and `Point` are the timeline's working types. The same structures
   feed `awayDays()`, `locationByNight`, `atHomeOn` and the missing-hotel sweep, so every one of
   those is in the change's path even though none of them wants an id. The `TravelLeg` precedent is
   encouraging here — it went in cleanly — but `Movement` is the *smaller* half of the walk.
2. **It crosses the dropdown threshold.** `ProblemFix.travelFixes` returns three today
   (`Book flight` / `Book train` / `Ground transfer`), which is exactly CLAUDE.md's cap for rendering
   actions as links. A fourth makes `/schedule-problems` render a **menu** — and only on the rows
   whose arrival is a private event **or a gathering** (both have a page to point at; a conference
   does not), so the report's rows stop looking alike. Note this cost got *bigger* once the
   gathering page turned out to exist: more rows cross the threshold, not fewer. That wants deciding
   before the code, not after: the options are to accept the inconsistency, to move a fix out of the
   cell the way `ConferencesRenderer` moved the CFP deadline, or to let a rematch fix **replace** the
   ground-transfer one where it applies (a four-mile taxi being exactly what Ted does not want to
   record).

## Open questions

1. **Does `Occupancy.Kind` go, or stay beside `OccupancyRef`?** It should go — two ways to ask the
   same question drift — but `conferenceName()` reads it and the missing-hotel row depends on that.
2. **Does `SchedulingConflict` get real links in the same change?** It is the original motivating
   case and carries *two* causes, either of which may be a gathering or a private event. Doing it
   too is what retires `ScheduleProblemsRenderer:26`'s greyed Fix; leaving it means the greyed span
   survives alongside working links elsewhere, which is worse than today.
3. ~~**Gatherings have the same four-mile problem and no page to fix it on.**~~ **Withdrawn
   2026-09-18 — the premise was false.** A gathering carries `locationForMatching`
   (`plan-gathering.html:186`) *and has been editable all along*, at
   `/planned-gatherings/{gatheringId}` (`ChangeGatheringController`, `change-gathering.html:188`).
   There is no companion change owed: a gathering's arrival gets the same fix link as a private
   event's, pointing at a page that already works. What the question was really noticing is that
   **a conference** has no such page — `plan-conference.html` does not expose the field at all — and
   that is tracked in `Cleanup_Tasks.md:131`, not here.
4. ~~**The three stale "slice 4" comments** should point here instead.~~ **Done 2026-09-18.**
   `ProblemFix`, `ProblemKey` and `ScheduleProblemsRenderer` now name this document; no `slice 4`
   reference survives in `src/main`. Two notes from doing it:
   - `ScheduleProblemsRenderer.NO_FIX_REASON` is a rendered `title` pinned by
     `ScheduleProblemsRendererTest`, so its **wording was left alone** — only the javadoc above it
     was repointed. It reads honestly only while `SchedulingConflict` is the *only* unlinkable
     problem; building this plan should **delete** the constant rather than reword it.
   - `ProblemFix` gained a note that `MissingTravel` has the same gap for a *different* reason (it
     knows two cities, not which entry raised them), since that is the case a reader arriving from
     `/schedule-problems` will actually be looking at.
   - The references in `archived/ProblemCalendarPlan.md` (:126, :377, :381) and
     `archived/ProblemContextOnFixPagesPlan.md` (:108) were **deliberately not touched** — archived
     plans record what was decided at the time, and rewriting them would erase that slice 4 shipped
     without cause-linking, which is the fact this document exists to carry forward.

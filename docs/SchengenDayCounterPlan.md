# Schengen Day Counter Plan — 90/180 tracking on the calendar and in the planning forms

> **Status: OPEN (planned 2026-08-12, amended 2026-08-12 and 2026-08-23). Nothing built.** Design
> agreed with Ted; implementation not started. See `docs/Backlog.md` for the current status of
> everything else.
>
> **Amendment 3 (2026-08-23): this plan gains `datesConfirmed`, and its one dependency is now met.**
> `datesConfirmed` on `ConferencePlanned` moved here from `archived/ConferenceSubmissionTrackingPlan.md`,
> where it had been deferred twice and was sitting in a slice whose other contents were this plan's
> anyway. It is a field on that plan's event, but this is the only plan that reads it, and it is in
> no code yet — so it simply lands with the ceiling, at step 4. That plan's slice 5 was re-cut to the
> one thing it actually owns (its gap projector filtering by attendance).
> Separately, the **commitment dependency is satisfied**: slices 1–4 of that plan shipped between
> 2026-08-18 and 2026-08-22, so `AttendanceCommitment` is live and nothing here is blocked on it.
>
> **Amendment 1 (2026-08-12):** the counter produces **two** numbers, a confirmed floor and a
> worst-case ceiling, and gains explicit per-gap *assumed stays*. Driven by
> `archived/ConferenceSubmissionTrackingPlan.md`, which splits conferences into committed and speculative.
>
> **Amendment 2 (2026-08-12), verified against the 2026-08-11 backup:** presence is an **evidence
> hierarchy**, not a single union — external border crossings first, dated sources as fallback, Ted's
> assumptions last. Legs outrank hotels by decision. Airport membership moves to step 1; no
> historical backfill is needed. Every claim in the new material was measured against the real event
> log rather than reasoned about, and the worked numbers below come from that data.

## Problem

The Schengen area allows a non-resident 90 days of presence in any rolling 180-day window. Ted
travels there often enough to get close to the limit, and today the app cannot answer either
question that matters:

1. **Where am I right now?** Nothing in the app models "which country was I in on a given day".
   `ScheduleGapProjector` derives something adjacent (nights in a city, for hotel gaps) but never
   exposes it, and works in cities rather than countries.
2. **Would this booking push me over?** Conferences are usually booked *first*, before any flight
   exists. So the check has to work from a conference's own dates and venue country alone, and it
   has to fire **before submit** — a warning after the fact is a warning about something already
   committed.

There is also a plain memory problem: Schengen membership is not obvious (Croatia and Bulgaria
are in; Ireland and Cyprus are not), so the form should determine it rather than expecting Ted to.

## What the app already knows

| Source | Country available? | Where |
|---|---|---|
| Conference | yes | `ConferencePlanned.venueAddress().country()` |
| Gathering | yes | `GatheringPlanned.location().country()` |
| Hotel | yes | `HotelBooked.address().country()` |
| Train leg | yes | `TrainStationAddress.country()` — enough to detect external crossings today |
| **Flight leg** | **no** | `StaticAirportCityResolver` maps airport → **city** only |

The flight gap is the only missing datum, and it is a **prerequisite**, not a finishing touch: tier 1
is the authoritative source for the floor, and without airport membership there are no
flight-derived crossings at all. It was originally sequenced last on the grounds that hotels carry
the count — which is empirically true (see the evidence hierarchy below) but is exactly the reliance
Ted declined.

**What is actually needed is narrower than a country map:** tier 1 only asks *is this airport inside
the Schengen area* — a boolean over a static set, failing loudly on an unknown code rather than
guessing. The entire 2026-08-11 backup uses seven airports (SFO, JFK, YYZ, LHR, FRA, MUC, HAM), of
which three are inside. Full airport→country resolution remains useful for other features but is not
on this critical path.

## Design

### Evidence hierarchy: border crossings first, dated sources as fallback

**Superseded design (2026-08-12).** This section originally read "presence is a union of dated
country intervals, **not** a chain of legs", on the grounds that a conference booked before any
flight would otherwise contribute nothing. That reasoning is right for the *forward* half of the
feature and wrong for the backward half, and the two need different spines.

The legal question is about **passport control**, not about where Ted slept. So the floor is derived
from external border crossings where they exist, and the union is the fallback where they don't:

| Tier | Source | Used for |
|---|---|---|
| 1 — authoritative | **External Schengen border crossings** → entry/exit envelopes. Every day inside an envelope counts, whatever else is or isn't on the calendar. | The floor, for any trip whose travel is booked. |
| 2 — fallback | Dated country intervals (conference, gathering, hotel), unioned — the table below. | Days no envelope covers: future trips before travel exists. |
| 3 — assumption | Assumed stays, explicitly recorded by Ted. | Open envelopes and gaps between tier-2 intervals. |

**"External" is the operative word, and it excludes most legs.** Intra-Schengen travel crosses no
border and is irrelevant to the count: Cologne→Gembloux, Frankfurt→Leipzig and MUC→HAM change
nothing. A crossing is a leg with exactly one endpoint inside the area.

**Crossings come from trains as well as flights.** Verified against the 2026-08-11 backup: the
June trip's exit is the **Brussels→London train on Jun 17**. Pairing flights only would merge
Jun 7 → Jun 30 into a single 24-day envelope and over-count the SoCraTes UK excursion by 4 days.
Train events already carry country on both endpoints, so this tier works for trains today.

**Envelope pairing is entry → *next* exit**, never first-to-last, or two trips merge. An entry with
no matching exit is an **open envelope** — see assumed stays.

**Empirical validation (2026-08-11 backup).** Both tiers were computed over the real event log and
they agree exactly where both have data:

```
external crossings          envelopes                     June cross-check
  06-07 ENTRY SFO→FRA         06-07..06-17   11 days        envelope June days:     20
  06-17 EXIT  train Bru→Lon   06-22..06-30    9 days        source-union June days: 20
  06-22 ENTRY LHR→FRA         08-25..09-14   21 days
  06-30 EXIT  MUC→SFO
  08-25 ENTRY SFO→MUC       fallback needed for: Agile Testing Days
  09-14 EXIT  FRA→SFO         2026-11-16..19 (no travel booked yet)
```

Floor 45 days, peak 45 on 2026-11-19, 20 used in today's window. Booking the Sept 14 FRA→SFO exit
moved **9 days out of "assumed" and into "confirmed"** — the union alone had scored 36.

**Hotels are demoted to fallback and corroboration.** Empirically they carry the past on their own
(the only source adding unique past days, and their June union matches the envelopes exactly), but
they evidence where Ted slept, not where he legally was: a hotel cannot establish a boundary, it
over-counts if cancelled outside the app or no-showed, and it under-counts a night with friends.
**Decision (Ted, 2026-08-12): prefer legs; never let hotels outrank them.** Dropping hotels from the
count entirely is *not* viable — the pre-submit warning exists precisely for trips with no travel
booked, which is tier 2's whole job.

This gives a useful audit for free: **a Schengen hotel night outside every envelope is either a
missing leg or a data error.** On current data that audit is clean apart from November, where no
travel is booked yet — which is the expected, not the anomalous, case.

Tier 2, the fallback union — **every source contributes dates independently** and the results are
unioned:

| Source | Dates contributed |
|---|---|
| Conference | start local date … end local date, inclusive |
| Gathering | start local date … end local date, inclusive |
| Hotel | check-in date … check-out date, inclusive (you are there that morning) |
| Any leg touching a Schengen endpoint | its own travel day(s) |

That last row is a floor under a broken envelope, not a spine: an intra-Schengen train is proof of
presence on the day it runs even when the entry leg is missing from the log. It should never be the
*reason* a day counts on a well-formed trip — if it is, the disagreement audit has something to say.

Unioning a `Set<LocalDate>` makes three things fall out for free:

- overlapping sources collapse (a conference plus its hotel plus its legs is one set of days);
- the day of entry and the day of exit each count as a full day, which is the rule;
- **a conference with no flights still counts**, which is the requirement — and this is the reason
  tier 2 cannot be deleted in favour of tier 1 alone.

Local dates throughout, per decision 7 elsewhere in this codebase: the day you are in a country is
the local day at that location.

### Two numbers: a confirmed floor and a worst-case ceiling

**Decision (Ted, 2026-08-12).** A single number cannot answer the question, because a lot of what
Ted knows about the next six months is not yet committed. So the counter runs the *same* union twice
over different input sets:

| Number | Input |
|---|---|
| **Floor** (confirmed) | Every **closed envelope** (tier 1), plus tier-2 days for **committed** conferences no envelope covers. The number that must stay legal. |
| **Ceiling** (worst case) | The floor, plus tier-2 days for **live speculative** conferences, plus every recorded **assumed stay** (below). |

**The dividing line is the envelope, not the calendar.** A trip in the future with both crossings
booked is as definitive as one in the past — Aug 25 → Sep 14 is already floor, not ceiling. A trip in
the past with a missing leg would fall to tier 2. Date has nothing to do with it.

**Consequence worth knowing: conference commitment only moves the Schengen number for conferences no
envelope covers.** Once flights bracket a conference, its days are already inside the envelope and
the `WATCHING` / `GOING` label changes nothing here (it still matters on the calendar). On the
2026-08-11 data that is exactly one conference — Agile Testing Days, November. So the dependency on
`archived/ConferenceSubmissionTrackingPlan.md` is real but narrow.

"Live speculative" means `WATCHING` — including submitted-and-waiting and rejected-but-undecided.
It excludes declined, withdrawn-and-not-going, and organizer-cancelled conferences: counting dead
entries inflates the ceiling until it means nothing. See
`archived/ConferenceSubmissionTrackingPlan.md` for where those states come from.

Because presence is a union rather than a sum, two speculative conferences in the same week do not
double-count. The ceiling is genuinely "the most days you could possibly burn", not an unreachable
total.

### Assumed stays — bridging is data, not a heuristic

**Tier 1 removes most of the need for this.** A closed envelope already counts its interior days, so
the gap between two conferences inside one envelope is proven presence, not an assumption. Assumed
stays are needed in exactly two places:

- **Open envelopes** — an entry with no exit booked. Ted is in the area and the app cannot know for
  how long.
- **Gaps between tier-2 intervals** — two conferences with no travel booked either side.

Contiguous-ish Schengen commitments without flights between them are the largest hidden risk, and
they are a risk **independent of speculation**. Ted's example: Portugal Nov 4–5 (committed),
Netherlands Nov 11–12 (speculative), Germany Nov 16–19 (committed). Counted as written that is 6
committed days. If he simply stays in the region rather than flying home twice, it is Nov 4–19 —
**16 days** — and the two committed conferences alone, with the Netherlands entry deleted, still
bracket a 10-day gap he would plausibly sit through. Two conferences a fortnight apart (Oct 28–29
Portugal, Nov 14–19 Germany) he would *not* bridge; he would fly home for the two empty weeks.

**Decision (Ted, 2026-08-12): no automatic bridge threshold.** An earlier proposal inferred bridging
by merging any two intervals whose gap fell under a tolerance (~10 days, bracketed by the examples
above). Rejected in favour of asking: the app lists the gaps and Ted marks the ones he would sit
through. A guessed tolerance is a number quietly deciding something material, which is the same
objection as the no-padding decision below.

This needs no new interval algebra, because **an assumed stay is just another dated country
interval** fed into the same union:

| Event | Notes |
|---|---|
| `SchengenStayAssumed(assumptionId, startDate, endDate, country, note)` | `country` may be `""` — Schengen-wide is enough for 90/180, and the country only affects display. Per the no-null-Strings rule, the compact constructor normalizes. |
| `SchengenStayAssumptionWithdrawn(assumptionId, reason)` | Ted changed his mind, or booked a flight home and the gap is moot. |

**Assumed stays land in the ceiling only, never the floor** — they are an assumption, not a booking.
No extra flag is needed for the case where Ted acts on one: booking the hotel for those nights makes
the floor rise on its own, from the hotel.

**Candidate gaps are suggested, not stored.** The counter surface lists candidates with a one-click
"assume I stay" — that click is what emits the event. Four rules keep the list short and honest, two
of them forced by checking the design against real data:

- **An external crossing out of the area breaks the gap.** With an exit booked on Nov 6 and a
  re-entry on Nov 15, Ted is demonstrably not staying; that gap must not be offered or counted,
  whatever was assumed earlier. Validated on the 2026-08-11 backup: the Jun 18–21 gap is correctly
  *not* a candidate, because the Brussels→London train proves the exit. This is the one place a train
  is load-bearing despite trains adding zero unique days to the union.
- **Clamp the gap end to the next known presence *anywhere*, not the next Schengen presence.** As
  originally specified, the gap after Sep 13 on real data ran to Nov 15 — 63 days, obvious nonsense.
  The useful bound was Denver on Sep 23, because that is when Ted demonstrably left. So
  `end = min(next Schengen interval start, next known non-Schengen commitment) − 1`.
- **An open envelope needs its own candidate type.** An entry with no exit is not a gap *between* two
  intervals, so the gap-listing above never offers it, and it is the most dangerous case there is —
  presence of unknown length. Real instance: `SFO→MUC` arriving Aug 25 sat open until the Sept 14
  exit was booked. Offer it as "you entered on X with no exit booked — assume you stay until?"
- **A same-day envelope is a transit candidate, offered in the opposite direction.** Since stopovers
  are always entered as separate flights, a Lisbon layover en route to Morocco produces a one-day
  envelope. This is the only candidate where the default is *counted* and Ted's click **removes** a
  day rather than adding one. See the layover note under known limitations.
- Gaps beyond roughly a month are not worth offering.

**Expect the ceiling to fall as travel is booked**, while the floor rises. That is the opposite
direction from the no-padding consequence noted below, and it is not a contradiction: booking
converts uncertainty into fact from both ends, so the two numbers converge on the truth.

### No padding — accurate information only

**Decision (Ted, 2026-08-12): do not pad.** An earlier proposal was to inflate a conference's
range by ±1 day in the preview, on the theory that you arrive early and leave late, and that
under-counting is the dangerous direction for a warning. Rejected: arrival and departure padding
are never symmetric, and a number that is quietly wrong is worse than a number that is
conservative-but-honest.

**Consequence to expect, not to fix:** the count *rises* as flights and hotels are added around a
conference. That is correct — the conference contributes its own days, the travel contributes the
travel days, and each is counted when it is actually known.

**This does not conflict with assumed stays.** No padding means the app never *infers* extra days:
not ±1 around a conference, and not a bridge across a gap because the gap looked small. An assumed
stay is Ted asserting days, explicitly and revocably, and it is confined to the ceiling. The rule is
about who decides, not about how many days get counted.

### `application/SchengenArea`

Plain Java, no Spring. A normalized country-name set, matching the `trim().toLowerCase(Locale.ROOT)`
style `LocationZoneResolver` already uses, since stored country strings are free text.

Members (29): Austria, Belgium, Bulgaria, Croatia, Czechia, Denmark, Estonia, Finland, France,
Germany, Greece, Hungary, Iceland, Italy, Latvia, Liechtenstein, Lithuania, Luxembourg, Malta,
Netherlands, Norway, Poland, Portugal, Romania, Slovakia, Slovenia, Spain, Sweden, Switzerland.

Explicitly **not** members: United Kingdom, Ireland, Cyprus. Alias the same spellings the rest of
the codebase already accepts (`czech republic`/`czechia`, `united kingdom`/`uk`, and so on).

### `application/SchengenDayCounter`

Plain Java. No Spring, no event knowledge, no clock — `today` comes in from the boundary, per the
external-inputs rule.

```java
public class SchengenDayCounter {
    public SchengenUsage usage(Set<LocalDate> schengenDates, LocalDate today);
}

public record SchengenUsage(int daysUsed, int daysRemaining, LocalDate windowStart,
                            int peakDays, LocalDate peakDate) {}
```

- **Window** for a reference date `D` is `[D-179, D]` — 180 days inclusive.
- **`daysUsed`** — how many dates in the set fall in today's window.
- **`peakDays` / `peakDate`** — the maximum of that same count over every `D` from `today` through
  the last date in the set, and the date where it occurs. One sliding-window pass, O(n).

`peakDays` is the number that protects against an overstay: `daysUsed` alone cannot see a trip
already on the calendar three months out.

**There is no separate "what-if" mode, and no separate floor/ceiling mode.** The conference preview
unions the hypothetical dates into the set and calls the same method; the floor and the ceiling are
the same method called twice on different sets, wrapped for the callers that want both:

```java
public record SchengenRange(SchengenUsage floor, SchengenUsage ceiling) {}
```

One code path means the number on the form, the floor on the calendar, and the ceiling on the
calendar cannot disagree with each other.

### `application/SchengenPresenceProjector`

An `EventStreamConsumer` producing **two** sets, both filtered through `SchengenArea`:
`Set<LocalDate> confirmedDates()` and `Set<LocalDate> possibleDates()`, where the latter is a
superset. Keeping both in one projector means a conference moving from `WATCHING` to `GOING` shifts
its days between the sets in a single event handler, with no chance of the two drifting apart.

It also derives the tier-1 envelopes, which is the one piece of genuinely ordered work here: fold
flight and train events into a chronological list of **external crossings**, pair entry → next exit,
and expose the closed envelopes plus any open one. Everything else in this projector is set union and
order-independent; envelope pairing is not, so it re-derives from the sorted crossing list rather
than being maintained incrementally as events arrive out of order.

Handles the same lifecycle events the other projectors do, so a cancelled or changed item moves the
number: `ConferencePlanned` / `ConferenceCancelled`, `GatheringPlanned` /
`GatheringChanged`, `HotelBooked` / `HotelChanged` / `HotelBookingCancelled`, `TrainBooked` /
`TrainChanged`, and `FlightBooked` / `FlightChanged`.

Plus, from this amendment: `ConferenceAttendanceCommitted` and `ConferenceAttendanceDeclined` (which
set determines a conference's days), and `SchengenStayAssumed` /
`SchengenStayAssumptionWithdrawn` (ceiling only). A conference whose dates are **provisional**
(`datesConfirmed == false`) still counts toward the ceiling, but the surface must mark the
contribution as guessed — a ceiling built from last year's dates should not read as fact.

### `datesConfirmed` on `ConferencePlanned` — this plan's field to add (moved here 2026-08-23)

A conference slot held before the CFP opens usually carries **last year's dates**, and this plan is
the only thing that reads them as a number rather than showing them. So `ConferencePlanned` needs a
`datesConfirmed` flag (or the inverse, `datesProvisional`) marking a guess wherever it is counted.

**It is a field on another plan's event, but it is work for this one.** It was deferred out of
`archived/ConferenceSubmissionTrackingPlan.md`'s slice 1 (Ted, 2026-08-18) because shipping a form control
months ahead of the behaviour that reads it is a papercut with no payoff, and it sat in that plan's
slice 5 until 2026-08-23 — which left a field waiting on a consumer in a *different* plan and made
that plan look unfinishable. It is in no code at all today, so nothing has to be migrated: it simply
lands with the ceiling, here.

**What adding it costs**, carried over from that plan: a second schema bump on `ConferencePlanned`
(**v3→v4**) with its own upcaster increment (absent→provisional), exactly like the `format`
increment that shaped the version-ladder — see `EventPayloadUpcasterDesign.md`. Absent means
*provisional*, not confirmed: an existing row was entered before anyone was asked the question, and
a guess wrongly counted as fact is the failure this flag exists to prevent. Plus the form control on
`/plan-conference` and the "guessed" marking on whichever surface shows the ceiling.

**Sequencing:** with **step 4** (`SchengenPresenceProjector`), which is where conference dates first
become part of a number. Earlier than that it is again a control with nothing reading it.

## The pre-submit warning

### Endpoint

Follows the `AddressParseController` precedent exactly: a `@RestController` GET under `/api/`,
deliberately a GET so it stays outside Spring Security's CSRF scope and the form need not thread a
token through `fetch` (`AddressParseController.java:19-22`).

```
GET /api/schengen-preview?country=Netherlands&start=2026-11-02T09:00&end=2026-11-05T17:00

→ { "inSchengen": true,
    "confirmed": { "daysUsed": 71, "remaining": 19, "peakDate": "2026-11-14" },
    "worstCase": { "daysUsed": 87, "remaining": 3,  "peakDate": "2026-11-19" } }
→ { "inSchengen": false }
```

`remaining` is `90 − peakDays`, computed **including the range in the query** — not
`90 − daysUsed`. Today's count does not know about the trip about to be committed, which is the
entire point of the check.

The conference being planned is speculative at the moment of planning, so its own dates land in
`worstCase` and not in `confirmed`. The panel shows both.

`inSchengen: false` covers both "the country is not a member" and "the country field is blank",
so the client needs no special-casing for a half-filled form.

**Security:** add `/api/schengen-preview` to the OWNER block at `SecurityConfig.java:43-50` **and**
a row to the `policy()` matrix in `AuthorizationMatrixTest`, in the same change. The endpoint
discloses presence and forward travel planning; it is owner-only. `/api/**` already receives a real
403 rather than a 302 redirect (`SecurityConfig.java:79-86`), so the `fetch` will see auth failures
honestly.

### Form behaviour

Both triggers call the same endpoint with all three params; the response decides visibility and
content. Country blur is simply the moment `inSchengen` typically flips.

- **`country` field blur** → fires the call. This answers "is Croatia Schengen?" without Ted having
  to know.
- **`endDate` field blur** → fires the same call with the new range and repaints the number.

**Reserved space.** The panel is always present in the DOM with a fixed `min-height`, empty until
populated, so revealing it never shifts the fields under the cursor. It sits immediately above the
submit button: it reads inputs both above it (the dates) and below it (the country), so it must not
sit above a field it depends on.

**States:**

| Condition | Panel |
|---|---|
| `inSchengen: false`, or country blank | empty (space still held) |
| both numbers < 85 | quiet: `Schengen: 19 confirmed / 3 worst-case days remaining (peak 19 Nov)` |
| **either** number ≥ 85 | **warning** styling, same line |
| either number > 90 | warning styling, plus `— 6 days over` on the number that is over |

**Warning threshold: both numbers at 85, same severity (Ted, 2026-08-12).** An earlier proposal
warned hard on the floor and gave the ceiling a softer advisory band. Rejected in favour of one rule.

**Known consequence of that choice:** the ceiling is ≥ the floor by construction, so "either at 85"
reduces to a single comparison on the ceiling, and in practice the warning always fires on worst
case. That is the earliest possible alarm, and it will sometimes fire over trips Ted never takes.
Recorded as a deliberate choice rather than derived behaviour, so it can be revisited without
re-deciding the whole design.

**Non-blocking.** The submit button is never disabled. These are *tentative* conferences, and Ted
may know something the app does not; a warning is the right amount of pressure.

### Scope of the form work

First pass: `plan-conference.html` (field `venueCountry`) and `plan-gathering.html` (field
`country`). Both are things booked before flights exist. `book-hotel.html` also uses `country` and
picks the panel up nearly free.

The `change-*` forms need an `&excluding=<id>` parameter, so that editing an event does not count
its old dates *and* its new dates. Out of scope for the first pass, but the endpoint signature
should leave room for it.

## The calendar strip

A strip above the grid in `CalendarRenderer`, showing the range:

> **Schengen: 34 / 90 days used** · window from 2026-02-14 · peak **71–87** on 2026-11-03
> · *3 assumed stays, 2 speculative conferences* ▾

The peak is a range: confirmed floor to worst-case ceiling. Expanding the strip breaks the ceiling
down by contribution (`71 committed + 10 assumed + 6 speculative`) and lists the candidate gaps with
their assume/withdraw toggles — a wrong assumption has to be visible, not buried inside a number.
Gaps whose contributing conference dates are provisional are marked as such.

**OWNER-only.** `/calendar` is the one page anonymous visitors can see. The strip reveals
country-level presence *and* forward planning, so it is gated on `isOwner`, which
`CalendarController` already derives (`CalendarController.java:42`). Pass the usage inward as a
nullable value and render nothing when absent — renderers must never re-derive viewer identity
(CLAUDE.md redaction rule 4).

## Build order

**Dependency: satisfied since 2026-08-19.** The floor/ceiling split needs a way to tell a committed
conference from a speculative one, which arrived with step 2 of `archived/ConferenceSubmissionTrackingPlan.md`
(the commitment events); slices 1–4 of that plan have all shipped, so `AttendanceCommitment` is live
and nothing here waits on it any more. The concern that motivated the dependency — building the
counter against an all-tentative model, giving every conference a floor of zero — no longer applies.
**Read the commitment through `ConferenceProgress`**, which is where that plan put the shared rules
precisely so that readers cannot disagree; do not fold the commitment events again here.

**And this plan now owns `datesConfirmed`** (moved 2026-08-23) — see the section above. It is a field
on `ConferencePlanned`, but this is the only plan that reads it.

1. `SchengenArea` — country membership, **and** airport membership (the boolean described above).
   Both halves are needed from the start now that tier 1 leads.
2. `SchengenDayCounter` + tests. Pure and dependency-free, so the boundary semantics get nailed
   before anything depends on them. `SchengenRange` lands here.
3. **Envelope derivation** — external crossings from flights and trains, entry → next exit pairing,
   open-envelope detection. This is the floor's spine, so it comes before the fallback union.
4. `SchengenPresenceProjector` — tier-2 fallback for days no envelope covers: conferences (both sets,
   keyed on attendance), gatherings, hotels. **`datesConfirmed` lands here** (v3→v4 on
   `ConferencePlanned` + upcaster + form control + the "guessed" marking) — this is the step where
   conference dates first become part of a number, and moving it any earlier repeats the mistake that
   kept it unbuilt: a form control with nothing reading it.
5. `/api/schengen-preview` + `SecurityConfig` matcher + `AuthorizationMatrixTest` row.
6. Form panel and JS on both templates.
7. Calendar strip, owner-only, showing the range.
8. **Assumed stays**: the two events, candidate detection (both gap and open-envelope kinds), the
   strip's expandable list, and its assume/withdraw POSTs. Owner-only routes — `SecurityConfig`
   matchers **and** `AuthorizationMatrixTest` rows in the same change, `.with(csrf())` on the POST
   tests.
9. **Leg/lodging disagreement audit** — Schengen hotel nights falling outside every envelope, as a
   missing-leg report. Cheap once steps 3 and 4 exist, and the thing that keeps tier 1 honest.

**No historical backfill is needed** — see "Historical data" below. `StaticAirportCountryResolver`
(full airport→country, beyond the membership boolean) is no longer on this plan's critical path.

## Testing

Per the project's usual tiers:

- **`SchengenDayCounterTest`** — day 180 exactly ageing out of the window; a single-day trip;
  back-to-back trips; a peak that falls in the future rather than today; the 84/85 warning
  threshold boundary. Plus: the ceiling is never below the floor, for any input.
- **`SchengenPresenceProjectorTest`** — lifecycle-propagation scenarios, per the preferred guard
  against a projector silently missing an event: `ConferenceCancelled`, `HotelBookingCancelled`
  and `GatheringChanged` must each move the number. Plus, for the two sets:
  committing a speculative conference must move its days from the ceiling into the floor **without
  changing the ceiling**; declining one must remove them from both; a withdrawn assumed stay must
  leave the floor untouched.
- **Ted's own worked example, as an explicit test** — PT Nov 4–5 committed, NL Nov 11–12 speculative,
  DE Nov 16–19 committed, no travel booked: floor 6, ceiling 8 with no assumptions, ceiling 16 with
  the two gaps assumed. Then the same case with the NL entry deleted: floor 6, and 16 with the single
  10-day gap assumed. It is the case the design was derived from, so it should fail loudly if the
  interval math regresses.
- **`SchengenEnvelopeTest`, from the real June 2026 trip** — the best fixture available, because it
  exercises every tier-1 rule at once: entry by flight (Jun 7 SFO→FRA), **exit by train**
  (Jun 17 Brussels→London), re-entry by flight (Jun 22 LHR→FRA), exit by flight (Jun 30 MUC→SFO),
  with five intra-Schengen legs that must be ignored. Expected: two envelopes, 11 + 9 days, and the
  Jun 18–21 UK gap offered as no candidate at all. Flights-only pairing yields one 24-day envelope,
  so this test is what stops that regression.
- **Open-envelope case, also real** — `SFO→MUC` arriving Aug 25 with no exit: one open envelope, an
  open-ended candidate rather than a gap candidate. Then add the Sep 14 `FRA→SFO` exit and assert the
  envelope closes at 21 days and the two September gap candidates disappear, because a closed
  envelope proves its own interior.
- **Cross-tier agreement** — over the June trip, tier 1 and the tier-2 union must produce identical
  day sets (both 20 on real data). A divergence means one of the two is wrong, and the test says so
  without needing to know which.
- **`SchengenPreviewControllerTest`** — `@WebMvcTest` slice with `@WithMockUser`: URL mapping,
  param type conversion, and a 403 for an anonymous caller.
- **`AuthorizationMatrixTest`** — a `policy()` row for `/api/schengen-preview`.
- **`CalendarRedactionSecurityTest`** — an anonymous body that `doesNotContain("Schengen")` **and**
  does not contain the day number. Assert on absence of the private value, per redaction rule 5.
- **`JsBehaviorTest`** (`@Tag("js")`, `./mvnw test -Pjs-tests`) — the blur triggers, a non-Schengen
  country leaving the panel empty, the warning class appearing at 85, and no layout shift when the
  panel populates. The `fetch` is stubbed with Playwright's `page.route()`; still no server, no
  Spring context, no DB.

Every new or changed test to be proven by mutating production code so it fails for the right
reason, then reverted.

## Historical data — nothing to backfill

**Question (Ted, 2026-08-12):** past conferences in Schengen countries were attended but carry no
commitment event, so they would derive as speculative and land in the ceiling rather than the floor.
Does that need a migration?

**Answered from the data, not from principle.** Measured over the 2026-08-11 backup, restricted to
days on or before 2026-08-12 — unique Schengen days each source contributes that no other source
covers:

| Source | Unique past days | Days covered |
|---|---|---|
| Conference | **0** | 5 |
| Gathering | **0** | 3 |
| Train | **0** | 7 |
| Hotel | 6 | 20 |

Past conferences contribute nothing that hotels and legs do not already establish, so **there is
nothing to backfill and no admin migration screen to build.** A reviewable one-time checklist was
designed and then dropped on this evidence; if a trip with no leg *and* no lodging data ever appears,
the disagreement audit (build step 9) will surface it, which is the cheaper way to find out.

Two supporting facts: the earliest event in the log is June 2026, so nothing predates the current
180-day window at all; and the only two `ConferenceCancelled` events are the BeJUG and Leipzig
migrations to gatherings, whose days are still counted through the gathering events.

**Still needed by hand, and not inferable:** conferences already accepted but *future* at ship time.
Absence of a decline says nothing there. On current data that is a handful of entries and a few clicks
in the dashboard view.

## Known limitations, to state rather than fix

- **Layovers are always visible, and now over-count rather than under-count.** This limitation
  originally read "a three-hour Schengen connection will not count unless entered as a separate leg."
  **That case does not arise (Ted, 2026-08-12): stopovers are always entered as separate flights**, so
  SFO→Morocco via Lisbon is entered as `SFO→LIS` and `LIS→CMN`, never as one leg. Deliberately, so a
  layover is not hidden.

  The consequence for tier 1 is the opposite of what was recorded: `SFO→LIS` opens an envelope and
  `LIS→CMN` closes it, so a same-day transit counts **one Schengen day**, and one that crosses
  midnight counts two. A round trip through Lisbon therefore contributes ~2 days per trip. Whether
  that is right depends on something the app cannot know — airside international transit is not an
  entry, but clearing immigration is.

  **Handle it as a review candidate, not an inference**, consistent with the per-gap decision: an
  envelope whose entry and exit are the *same day* (or under ~24 hours) is a **transit candidate**,
  surfaced with "airside transit, or did you clear immigration?" Counting it is the default, since
  over-counting is the safe direction for a legal limit; Ted's answer is what removes it. Nothing here
  is inferred from flight duration or airport.
- **Hotels are trusted for tier 2 but not audited against reality.** A booking cancelled outside the
  app, or a no-show, still contributes days where no envelope covers it. This is the residue of the
  "prefer legs" decision: legs outrank hotels wherever both exist, and where only hotels exist their
  word is taken.
- **Days with no data are gaps, not zeros.** The projector knows only what is in the event stream.
  The ceiling narrows this considerably — speculative conferences and assumed stays *are* unbooked
  intent, and they now count — but intent that was never entered still contributes nothing, and the
  floor in particular will understate until travel is booked. This follows from the no-padding
  decision.
- **Assumed stays go stale silently.** Nothing expires an assumption whose bracketing conference has
  moved, been declined, or passed. The gap list is the mitigation: it is visible on the strip, so a
  stale assumption is one glance away rather than hidden in a number. Auto-withdrawing an assumption
  when its neighbours change is deliberately not attempted — it would guess at intent, which is the
  thing the per-gap decision exists to avoid.
- **Unknown airports** resolve to an unknown country, never a guess — consistent with
  `LocationZoneResolver`'s strictness. Under-counting is the dangerous direction here, so unknown
  days should be surfaced rather than silently dropped.

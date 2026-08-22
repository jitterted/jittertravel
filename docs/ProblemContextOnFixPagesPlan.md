# Problem Context on Fix Pages — carry the "why" from `/schedule-problems` to the form

**Status: `done` — designed and built 2026-08-21 (Ted + discussion). All three slices shipped in one pass.**

> **As built.** `ProblemRef` (the content-derived key, exhaustive over the sealed `ScheduleProblem`)
> + `FixOrigin` (where the link was clicked: problem list, problem calendar, or itinerary — with the
> Back label and href for each) + `ProblemContextLookup` (key → banner, against `problems(now)` and
> `context()`) + `ProblemContextAdvice` (`@ControllerAdvice`, D6 option A) +
> `templates/fragments/problem-context.html`, included by all six fix targets, styled in `site.css`.
> `ProblemFix.forProblem` now takes the origin and appends `?problem=&from=` to **every** href in one
> place, so a new fix cannot ship without them. Its three callers name their own origin; there is no
> defaulting overload. Tests: `ProblemRefTest`, `ProblemContextLookupTest`,
> `ProblemContextFragmentConventionTest`, `ProblemContextBannerWebIntegrationTest` (two controllers
> through the real Spring + Thymeleaf path, including the encoded href `ProblemFix` actually
> renders), plus updated href expectations in the four renderer/controller tests that assert whole
> anchors. Seven mutations verified — key separator, context cap, context padding, advice returning
> nothing, a template forgetting the fragment, fixes losing the reference, the itinerary Back link,
> and the fragment losing its null guard — each failing the right test and reverted. Suite green:
> **1331 unit + 48 js**.
>
> **Two things came out simpler than planned.** D4's `ProblemWording` was not needed: `ProblemBand`
> and `ContextBand` already carry the report's wording as plain strings, so the banner reuses them
> and there is no second copy of the phrasing to drift. And D7 turned out to have **three** origins,
> not two — `ItineraryRenderer` renders fix links too, and a link clicked there now comes back to
> `/itinerary`.

## Problem

Every fix link on `/schedule-problems` (both views — the list card and the calendar band, which
share `ProblemFix.forProblem`) navigates away to a form that has **forgotten why you came**.

Ted, 2026-08-21:

> By the time I get to the "book hotel" page from the problems page, I've forgotten what the
> surrounding context is, so I have to open up another tab to see.

The prefill is not the gap. `/book-hotel?city=…&checkIn=…&checkOut=…` arrives with the right city
and the right nights already in the inputs. What is missing is **the sentence that made those the
right values**: *no bed in Denver for the 14th to the 18th, because dev2next runs the 14th to the
18th, and the flight lands at 11:30 on the 14th.* That sentence exists — the report renders it, and
`ScheduleGapProjector` already publishes both halves of it (`problems(now)` and `context()`) — it
just does not travel with the link.

Opening a second tab is the workaround, and it is the whole cost: two tabs, two page loads, and a
manual re-read of a report he was looking at four seconds ago.

This plan covers **all** fix targets, not the hotel one. A second plan (ground-transfer endpoint
prefill) is separate and comes after; see "Not in this plan".

## What ships

Above the form, on every page a fix link can reach, a banner that says:

- **the problem, in the report's own words** — "No hotel in Denver, checking in Sep 14, checking
  out Sep 18"
- **the causes around it**, from `ScheduleContext` — "dev2next, Denver, Sep 14–18", "Flight SFO →
  DEN, Sep 14"
- **a way back** — "← Back to schedule problems", returning to the surface he left (the problem
  list, the problem calendar, or the itinerary)

No banner when the page is reached any other way (the index nav card, the calendar day-menu): the
page renders exactly as it does today.

## Decisions

### D1 — The link carries a *reference*, never the words

The href gets one new parameter, `problem=<key>`. The target page looks the key up against
`ScheduleGapProjector.problems(now)` and renders from what it finds.

The alternative — putting the sentence in the query string — is rejected. A URL can be edited, and a
banner that prints whatever the URL says is a page that will confidently state something false about
the schedule. Worse, it goes stale: fix the flight in another tab, come back, and the banner still
describes a problem that no longer exists. A lookup cannot lie, because the report is its only
source.

### D2 — No match renders nothing

Unknown key, edited key, or a problem solved since the link was drawn: the banner is absent and the
form is untouched. This is the whole error path. There is no "problem not found" message — the page
is still perfectly usable, and an error about a decoration is noise.

This also makes the key format free to change later: an old bookmarked URL degrades to today's
behaviour.

### D3 — The key is derived from the problem's content, exhaustively

`ScheduleProblem` has no id, and giving it one would mean persisting identity for something that is
*derived* — it is recomputed from events on every batch. So the key is a function of the record's
own fields, per variant, in an exhaustive switch over the sealed interface — the same shape as
`ProblemFix.forProblem` and `ProblemBand.from`, so **a new problem type cannot be added without
deciding how it is referenced.**

Encoding, as built — one URL-encoded token, parts separated by a vertical bar, kind first:

| Variant | Key |
|---|---|
| `MissingHotel` | `hotel` \| city \| checkIn \| checkOut |
| `MissingTravel` | `travel` \| fromCity \| arrivedAt UTC \| toCity \| nextDepartureAt UTC |
| `DuplicateHotel` | `dup` \| firstNight \| lastNight \| the stays' booking ids, comma-separated |
| `DifferentCityConflict` | `city` \| gatheringId \| conferenceId \| date |
| `SchedulingConflict` | `clash` \| first name \| first startsAt UTC \| second name \| second startsAt UTC |

The duplicate's stays are named because the nights alone collide: two cities double-booked over the
same run are two problems, and one fix link must not explain the other. The missing hotel's
conference is *not* in the key — it is context, not identity.

`SchedulingConflict` has no fix link today (it carries no ids — the cause-linking gap), so its key
is unreachable for now. It is defined anyway, because the switch is exhaustive and because the
banner is what a future fix link for it will want.

Matching is equality on the key, computed the same way on both ends. No parsing back into a problem:
decode nothing, compare strings, take the problem object from the projector.

### D4 — The banner reuses the problem calendar's wording (revised during implementation)

The plan called for extracting a `ProblemWording` collaborator, on the grounds that
`ScheduleProblemsRenderer` writes its detail lines as j2html `<time>` elements the banner cannot use.

**Not needed.** `ProblemBand.from(problem)` already returns exactly what the banner wants, as plain
strings: `title` ("No hotel — Denver"), `detail` ("4 nights — dev2next"), `firstDay`/`lastDay` for
the clipping in D5, and `marker().cssModifier()` for the kind edge. `ContextBand.from(context)` does
the same for the cause lines ("dev2next, Denver · Sep 14–18"). Both are exhaustive switches, so a
new variant still cannot slip past.

So the banner reads those two, and **no new phrasing exists anywhere**. That is better than the
planned extraction, not a shortcut around it: the banner has to say what the band Ted just clicked
said, and now it literally cannot say anything else. The band's `fixes` are ignored — the banner
explains, it does not re-offer the fix you are already on.

### D5 — Context rows are clipped to the problem's own days

`ScheduleGapProjector.context()` is unfiltered by design. The banner clips it, exactly as
`ProblemCalendarRenderer` clips to the days it draws: keep a `ScheduleContext` whose
`[firstDay, lastDay]` overlaps the problem's own span, widened by one day at each end so the flight
that lands the morning the gap opens still shows.

Cap the list. The banner is a reminder, not a second report; the Back link is there for the rest.
Built at four rows — about a conference, its flights either side, and one stay.

### D6 — How the banner reaches the model — **decided: option A** (Ted, 2026-08-21)

The fragment needs a model attribute, and only a controller or a `@ControllerAdvice` can put one
there. Six controllers are reachable from a fix link: `BookHotelController`, `BookFlightController`,
`BookTrainController`, `PlanGroundTransferController`, `CancelHotelController`,
`ClearConflictController`.

**Option A (chosen) — one `@ControllerAdvice`** that reads `?problem=` and adds the banner attribute
for every request. One new class, zero controller changes, no constructor gains a dependency it does not
otherwise use. Cost: it is implicit (nothing in `BookHotelController` says the banner exists), it is
a Spring mechanism this codebase has none of today (no `@ControllerAdvice`, no `HandlerInterceptor`
anywhere in `src/main/java`), and it runs on every controller, not just these six.

**Option B — inject `ProblemContextLookup` into each of the six controllers.** Explicit and greppable.
Cost: one new dependency in six classes that do not otherwise use it — which is exactly the shape Ted
rejected on 2026-08-18 for the state-aware nav link ("weigh coupling against UX gain").

**Ted chose A.** The banner is genuinely cross-cutting — its trigger is a query parameter, not
anything about booking a hotel — and the coupling rule points away from B.

The convention test (below) is what stops option A from being invisible: it fails when a fix target
does not render the fragment.

**One thing option A forced, worth knowing.** A `@WebMvcTest` slice instantiates every
`@ControllerAdvice` it scans, and this codebase has forty-odd slices for controllers with nothing to
do with the schedule. Requiring the beans outright would make each of them mint a
`ScheduleGapProjector` and a `Clock` to render an unrelated form. So `ProblemContextAdvice` takes
both as `ObjectProvider` and resolves them once in its constructor: absent either, there is no
banner — the same outcome as a key that matches nothing. That is the one Spring-ism the choice
bought, and it is confined to that constructor.

### D7 — Back link returns to the surface he left (three of them, not two)

`ProblemFix` hrefs carry `from=`, and `FixOrigin` maps it to the Back label and href. Landing in the
list after clicking a calendar band is a small thing that reads as the app losing your place.

The plan said two values. There are **three**: `ItineraryRenderer` renders the "Book hotel" fix on a
bedless day too, so `from=itinerary` comes back to `/itinerary` — coming back to a report Ted was
not reading is worse than the problem being solved. Absent or unrecognized falls back to the problem
calendar, the same default `ProblemView.fromParam` takes.

Every caller names its own origin; there is deliberately **no** single-argument overload, so a new
surface offering fixes has to decide where its links come back to.

## Security

**No new route, so no `SecurityConfig` matcher and no `AuthorizationMatrixTest` row** — the same
reasoning `ProblemFix` already records: the matrix is keyed by path, a query parameter cannot escape
a path matcher, and every target is already OWNER-only (verified 2026-08-21 in `SecurityConfig`:
`/book-flight`, `/book-hotel`, `/book-train`, `/plan-ground-transfer`, `/clear-conflict`,
`/booked-hotels/*/cancel` — all `hasRole("OWNER")`).

That is a **standing condition, not a one-time check.** The banner prints OWNER-only data — hotel
cities, gathering names, exact arrival times, the whole content of a report `SecurityConfig` gates
at OWNER. If a fix target is ever opened to FAMILY or anonymous, the banner must not render on it.
Write that down next to the fragment.

Nothing here touches `CalendarEntryRedactor`, `/calendar`, or any public surface.

## Slices (all three shipped 2026-08-21)

**S1 — end to end on one page.** `ProblemRef` (D3) + `ProblemContextLookup` + `ProblemContextAdvice`
+ `fragments/problem-context.html` + the key on the hotel fix, rendered on `/book-hotel`. Proves the
whole path with one target. (No `ProblemWording` — see D4.)

**S2 — the rest of the targets.** Key on every `ProblemFix` href; fragment into `book-flight`,
`book-train`, `plan-ground-transfer`, `cancel-hotel`, `clear-conflict`. Add the convention test.

**S3 — Back to the surface he left** (D7), including the itinerary's own fix links.

In the event all three landed in one pass: the reference and the origin are appended in the same
place in `ProblemFix`, so splitting them across commits would have meant writing that method twice.

## Tests

- `ProblemRefTest` — the key for each variant, and that two different problems of the same kind get
  different keys.
- `ProblemContextLookupTest` — matches; returns empty for an unknown key; returns empty when the
  problem has been solved; clips context rows to the problem's span ±1 day (D5); caps the row count.
- `ProblemContextBannerWebIntegrationTest` — a `@WebMvcTest` over **two** fix controllers (the
  fragment has to work on more than the page it was written against): GET with `?problem=` renders
  the banner, an unmatched key and a plain GET render none, and the real `ProblemFix` href resolves
  through its own percent-encoding. Assert **whole elements** — e.g.
  `contains("<div class=\"problem-context problem-context--bed\">")` and the exact sentence — never a
  bare word, and for the absence case assert on the wrapper element, not on a word that appears
  elsewhere on the form. A per-controller slice for the other four is not worth its weight; the
  convention test covers that they include the fragment, and the fragment is one file.
- `ProblemContextFragmentConventionTest` — every template reachable from a `ProblemFix` href
  includes `~{fragments/problem-context :: problemContext}`. Modelled directly on
  `AddressPasteFragmentConventionTest`, which exists because a shared fragment that must be included
  everywhere is exactly the thing that gets forgotten on the seventh page.
- Mutation-verify every one of them: change the production string or drop the fragment, watch the
  right test go red, revert.

Run `./mvnw test` and `./mvnw test -Pjs-tests` before pushing. No JS ships in this plan, so the js
tier should be unaffected — which is itself worth confirming, not assuming. **Both ran green:
1331 unit, 48 js.**

Two test notes from the build. Four existing tests assert *whole* fix anchors and had to learn the
new suffix — that is the assertion style working, not friction. And `MockMvcTester.uri(String)`
re-encodes a hand-written `%7C` into `%257C`, which reads exactly like the banner being broken; the
request-level tests pass the key as a parameter value, and one separate test feeds the real
`ProblemFix` href in through `URI.create` so the encoded round trip is still covered.

## Follow-up, same day: making the banner matchable (2026-08-21)

Ted used it on `/plan-ground-transfer` and found the banner and the form talking past each other:
the banner said `Hotel, Denver · Sep 13–16` while the endpoint select offered `Reichshof — Hamburg`.
Matching them meant matching *cities* — which fails outright when two stays share one.

Two halves, both shipped:

- **The banner names the hotel.** `ScheduleContext.Stay` gained `hotelName` (a pass-through:
  `ScheduleTimeline.Stay` already held it), so `ContextBand` labels it like every other kind —
  `Reichshof, Denver · Sep 13–16`. `ContextBand` is shared, so **the grey context bands on
  `/schedule-problems?view=calendar` name the hotel too** — Ted okayed that side effect rather than
  giving the banner a second formatting site.
- **The form opens on the right endpoints.** `?problem=` reaches
  `PlanGroundTransferController` too, which resolves the gap through the same `ProblemRef` the
  banner uses and asks the choices for an unambiguous candidate at each end — **exactly one or
  none**, per **D17**. This was the half Ted actually meant by "prefill"; the date/time filling
  below was a misread of the request, kept because it still helps a hand-picked endpoint.
- **The options name their dates.** Covered by **D16** in `docs/archived/GroundTransferPlan.md`: the
  hotel lists split by direction like the flight legs, each option reading
  `Reichshof — Hamburg · check out Fri Sep 18, 11:00 AM`, and prefilling that moment. The prefill
  half is **on trial** at Ted's request — a stay is a range, so choosing an ongoing stay moves the
  date field backwards to check-in day. The label says which moment it fills in, so a wrong one is
  visible; removing it later leaves the dates in place.

## Not in this plan

**Ground-transfer endpoint prefill.** `/plan-ground-transfer` receives only `?date=`, because the
gap knows *cities* while the form takes *endpoint tokens* (`airport:DEN`, `hotel:<bookingId>`) and
one city maps to zero, one, or many of them — recorded as D13 in
`docs/archived/GroundTransferPlan.md`. Preselecting the wrong one is not a wasted click: it writes a
`GroundTransferPlanned` event that **removes the very gap it was entered to close**. The safe
version — preselect only on an unambiguous single match, group the candidates otherwise, say so when
there are none — is a separate plan, and it helps one problem kind where this one helps all of them.
That is why this goes first (Ted, 2026-08-21).

**Fixing in place.** A flow that never leaves `/schedule-problems` would beat a banner outright.
It is also a much larger change, and the banner is the cheap 90%.

# Conference detail page + Change Conference

> **Status: `planned` 2026-09-04, nothing built.** Written after Ted asked where the details for a
> planned conference are and whether the gap was tracked. It was not: `Cleanup_Tasks.md` (Deferred)
> tracks *"No way to change a conference"*, which is the **edit** half; nothing named the **view**
> half at all.

## The problem, in three findings

1. **There is no `/conferences/{conferenceId}`.** Conferences have only action sub-routes —
   `/cfp`, `/confirm`, `/decline`, `/talk`. Flights, hotels, trains and gatherings all answer at
   `/{collection}/{id}`.
2. **The venue is carried and never shown.** `ConferenceView` holds `venueName` and `venueAddress`;
   `ConferencesRenderer` renders neither. The table is Name / Going? / Dates / City / Actions.
   `PlannedGatheringsRenderer` and `PlannedPrivateEventsRenderer` both show their venue name — the
   conference dashboard is the odd one out, and no projector change is needed to fix it. Today the
   only place the venue is visible is `/itinerary`.
3. **A conference is the only planned kind you cannot correct.** No `ChangeConferenceController` to
   match `ChangeGathering`, so name, dates, venue, `format` and `infoUrl` are fixed at plan time.

**And the CFP is legible only in pieces.** The deadline is a sub-line under the name; the submission
URL hides behind the `Submitted` action; `SpeakingStatus`, `ConferenceFormat` and the basis live in
`ConferenceProgress` and never reach a view *by design* (they are the private half). There is
nowhere that answers "what is going on with this conference" in one look.

---

## What the codebase already decides for us

Read these before proposing anything; three of them close options that look open.

- **`/{collection}/{id}` *is* the change form, everywhere.** `ChangeHotelController`,
  `ChangeFlightController`, `ChangeTrainController` and `ChangeGatheringController` each own the
  `GET` **and** `POST` at `/{collection}/{id}`. **There is no read-only detail page anywhere in this
  app.** So "a detail page" is a new *kind* of surface here, not a missing instance of an existing
  one — which is D1.
- **`/conferences/*` is already `hasRole("OWNER")`** in `SecurityConfig` (single `*` matches one
  segment). A new `/conferences/{id}` route needs **no new matcher** — but it still needs its
  `AuthorizationMatrixTest` row, and that test is the thing that proves the gate, not the config.
- **The Actions column is budgeted for exactly three links.** `ConferencesRenderer` is
  `table-layout: fixed` with a 240px Actions column "budgeted for three of them", and the state
  machine already spends all three (`Submitted · Ticket Bought · Decline`). **A "Details" link
  cannot be appended there** without either a fourth link — which CLAUDE.md's dropdown rule turns
  into a menu — or re-budgeting the column. This kills the obvious answer, so the entry point has
  to come from somewhere else. See Options below.
- **`EntryDetails.Conference` has no `editPath`**, while `Gathering`, `Flight`, `Lodging` and
  `Train` all do; `CalendarViewBuilder.ownerActions` has an explicit `Conference _ -> List.of()`
  arm saying so. Adding one is safe **only** on the owner record: CLAUDE.md's redaction rule 1 says
  an `EntryDetails.Publishable` has no slot for an edit path, and that is what makes the allow-list
  a compiler check. `PublicConference` is a separate record, so this is legal — but it must be said
  out loud and pinned by both redaction tiers, because it is exactly the shape of change that
  erodes the rule.
- **The j2html/Thymeleaf split argues for two pages, not one.** Read-only views are j2html; POST
  forms are Thymeleaf, which keeps renderers clear of CSRF. A combined view+form page has to be
  Thymeleaf throughout, which drags the whole CFP/talk presentation into a template.

---

## Decisions

### D1. Two pages: a j2html detail view at `/conferences/{id}`, a Thymeleaf form at `/conferences/{id}/change`

The house pattern is one page, and this deliberately departs from it. Four reasons, in order of
weight:

1. **The CFP state is the reason the detail page was asked for, and it is read-only.** Deadline,
   submission URL, where the talk stands, the format, the commitment and its basis — none of it is
   editable on this page (each has its own action route already). A form page whose largest section
   is not part of the form is a form page in name only.
2. **The split matches the j2html/Thymeleaf rule** rather than fighting it.
3. **It is the direction the repo is already moving.** `Cleanup_Tasks.md` (Open) carries "split
   every page that combines an Edit and a Cancel form"; Cancel Hotel was split out in `f5971ef`.
   Merging view + edit here would be adding a combined page while another item removes them.
4. **Change Conference is rare; looking one up is not.** Conferences are corrected when a venue
   moves; they are *consulted* whenever Ted wonders what he committed to.

**The cost, stated plainly:** conferences become the only kind where `/{id}` is not the edit form,
so someone reading the routes sees an inconsistency. Accepted, because the alternative is a
Thymeleaf page carrying six read-only panels. **If Ted prefers consistency over this, the fallback
is a single Thymeleaf `/conferences/{id}` with the CFP panel above the form** — say so and it
collapses to one slice.

### D2. The detail page is a *reference* surface, and that decides its contents

CLAUDE.md splits **recording** surfaces (report an act already done; want identification and
consequences only) from **decision-support** surfaces (carry whatever makes the choice answerable).
A detail page is neither: it is the place Ted goes to *re-read what he already recorded*. So the
rule to apply is its parent — **show what identifies the conference and what state it is in; put
nothing here that exists to help him decide, because the deciding happens on the action pages.**

Concretely it carries:

| Section | Fields | Source |
|---|---|---|
| Identity | name (linked to `infoUrl`), venue name, full street address, city, country | `ConferencePlanned` |
| When | start/end as `ZonedTimeTag` in the venue zone, day count | `ConferencePlanned` |
| Attendance | commitment chip (`WATCHING`/`GOING`/`NOT_GOING`), and the **basis** when confirmed | `ConferenceProgress` |
| Format | `CALL_FOR_PAPERS` / `ACCEPTANCE_REQUIRED` / `OPEN_SPACE`, in words | `ConferencePlanned.format` |
| CFP | see D3 | `CfpOpened` |
| Talk | `SpeakingStatus` in words, and what it means for attendance | `ConferenceProgress` |
| Actions | the same state-machine links `/conferences` offers, plus **Change** | `ConferencesRenderer.actions` |

**The basis is publishable here and nowhere else.** `AttendanceBasis` is on CLAUDE.md's private
list because it re-states the submission outcome. This page is OWNER-only behind
`hasRole("OWNER")`, so it may show it — but it must reach the page through a **new OWNER-only view
record**, never by adding a field to anything a calendar projector touches. Do not widen
`ConferenceView`'s contract to serve this page if that view is reachable from a calendar path;
check before reusing it.

### D3. The CFP panel answers four distinct absences, and they are not the same

This is the part worth getting right, because `null` means four different things today and the
dashboard already distinguishes two of them:

| State | What the panel says |
|---|---|
| `format` is `OPEN_SPACE` / no CFP concept | "No call for papers — open space format." No deadline row at all. |
| `format` is `CALL_FOR_PAPERS` but no `CfpOpened` recorded | **"CFP not recorded"** — an open question for Ted, with a *Record CFP* link. This is the "go and find the date" case. |
| `CfpOpened` recorded, `closesOn` in the future | "Open — closes {date}, in N days", plus the submission URL as a link if one is recorded. |
| `CfpOpened` recorded, `closesOn` past | "Closed {date}" — and this is where `SpeakingStatus` carries the weight: closed with `NOT_SPEAKING` means the window was missed. |

**`closesOn` is required, so a submission URL cannot exist without a deadline** (`CfpOpened`'s own
javadoc). The panel therefore never has to render a URL with no date, and a test should pin that
rather than leaving it as a happy accident.

**The zone trap, and it is a real one.** `CfpOpened.closesOn` is a `ZonedTimestamp` in the
*conference's venue zone*, derived from the dates already on `ConferencePlanned` — deliberately, so
one conference has one zone. **Changing a conference's venue to another zone therefore leaves a
stored CFP deadline in the old zone**, and nothing recomputes it. See Q2.

### D4. `ConferenceChanged`, a full snapshot, following `GatheringChanged`

Past-tense fact, per the event-naming rule. It carries **every** field of `ConferencePlanned` —
`name`, `startDate`, `endDate`, `venueName`, `venueAddress`, `format`, `infoUrl` — and the
projectors take the last one.

**The full-snapshot trap is live here and has bitten this repo before** (`HotelChanged`'s javadoc
records it for `cancelBy`). Two fields will be silently cleared by an edit unless the form
round-trips them:

- **`format`** — behavioural, non-null, and *not* an obvious thing to put on a change form. If the
  form omits it, every edited conference silently becomes `CALL_FOR_PAPERS`, which for an
  `ACCEPTANCE_REQUIRED` conference changes whether a rejection drops it from the calendar.
- **`infoUrl`** — empties to `""`, and the title stops linking anywhere for every viewer including
  anonymous.

Both must be on the form and asserted by a round-trip test that changes *one* field and checks the
other six survive.

**No schema bump and no upcaster**: this is a new event type, not a change to an existing one.
It needs a **golden sample** in `GoldenEventDeserializationTest` in the same change, per the
standing rule.

### D5. `ChangeConferenceCommand` validates the venue/city pair

CLAUDE.md's `EnteredLocation` rule is wired to the four commands that write a building-plus-city
today, and says explicitly: *"Not wired to gatherings, conferences or private events, which have
the same venue/city exposure; two of their stored events would trip rule 1 today. Extending is a
decision, not a chore."*

**This plan takes that decision for conferences only**, because it is the first time a conference's
city becomes *typeable* — until now it was set once on `/plan-conference`. A conference's city is
compared by `Place`/`ScheduleGapProjector` exactly as a hotel's is, so "Frankfurt (Main) Hbf" in the
city box invents a gap between a city and itself. Rejecting in the **command**, never in the record
(a rule in `ConferencePlanned`'s compact constructor would apply retroactively on replay and stop a
restore dead — that is the whole reason the rule lives where it does).

Needs the three tiers the rule demands: a case in `EnteredLocationTest`, one on
`ChangeConferenceCommand`, and a `@WebMvcTest` asserting both the field error and the rendered
`<span class="error">`.

---

## How you get there — options per surface

Ted asked for options at each place a conference shows up. Every surface below is a place one does.

### 1. `/conferences` — the dashboard *(the primary route in)*

| | Option | For | Against |
|---|---|---|---|
| **A1 ★** | **Conference name links to the detail page; `infoUrl` moves to a small external-link icon beside it** | Matches every other list in the app — a row's name is how you open the row. Costs no column width, and the Actions budget is untouched. Puts the app's own page first and the third-party page second, which is the right precedence for an owner dashboard. | Changes what a familiar link does. The `conf-info-link` styling and its "Open the conference's own page" title move to the icon. |
| A2 | A narrow trailing **chevron/Details column** | Leaves the name link alone. | A new fixed column on a table whose width is already argued out to the pixel; and CLAUDE.md says an action must not move — adding a column shifts Actions. |
| A3 | Whole **row** clickable | Biggest target. | The row contains links (name → infoUrl, up to three actions); nested click targets are exactly what the year overview avoided by having *nothing* inside its link. |
| A4 | Add **Details** to the Actions cell | Consistent with the other moves. | **Rejected on a finding, not a preference:** the column is budgeted for three and the state machine already offers three, so this forces a dropdown — and CLAUDE.md says use one only *above* three choices. |

**Recommendation: A1.** It is the only option that costs no layout.

### 2. `/calendar` — owner and family

| | Option | Notes |
|---|---|---|
| **B1 ★** | **An edit pencil in `ownerActions`**, exactly as lodging, gathering, flight and train have — replacing the explicit `case EntryDetails.Conference _ -> List.of()` arm | Needs `editPath` on `EntryDetails.Conference` (owner record only — see the constraint above). The icon already never moves between rows, so this fills a slot that is reserved rather than adding one. |
| B2 | Conference **title** links to the detail page | Rejected: the title's `infoUrl` link is **public** and shared with the anonymous rendering path. Diverging the title by audience is the "derive a public entry from an owner entry" mistake the allow-list exists to prevent. |
| B3 | Add "Conference details" to the **day menu** | The day menu is *Add …* actions plus "Open itinerary" — it is about the day, not about an entry on it. Wrong menu. |

**Recommendation: B1**, and note the pencil is `isOwner`-gated, so **FAMILY gets nothing** — correct
per CLAUDE.md (a viewer who could never trigger it gets nothing, not a greyed control).

### 3. `/itinerary`

| | Option | Notes |
|---|---|---|
| **C1 ★** | **A pencil beside the title**, exactly as `renderGathering` does today (`editPencil("/planned-gatherings/…", "Edit gathering")`, `isOwner`-gated) | One-line change, an existing pattern, and the itinerary is *already* the only place the venue is visible — so it is where Ted is standing when he wants more. |

**Recommendation: C1.** Lowest-cost entry point in the app, and it makes the conference card match
the gathering card next to it.

### 4. `/schedule-problems`

| | Option | Notes |
|---|---|---|
| D1 | Link the conference name where the report names it ("— for {conferenceName}", "{name} ({city})") | Tempting, but the report's own fix links all carry `?problem=…&from=…` and land on **decision-support** surfaces. A detail page is not a fix. |
| **D2 ★** | **Leave it alone** | The report is about the problem, and its established vocabulary is *fix* links. Adding a non-fix link to a problem card muddies what a link there means. |

**Recommendation: D2** — and if Ted disagrees, it should go through `ProblemFix` so it carries its
problem with it, not as a bare `<a>`.

### 5. The iCal feed's CFP-deadline VEVENT

| | Option | Notes |
|---|---|---|
| E1 | Add a `URL:` property pointing at the detail page, so tapping the CFP alarm on the phone opens it | `ICalWriter` sets no `URL:` today. Genuinely useful — a CFP alarm without a link means "go and find it". |
| **E2 ★** | **Not in this plan** | The detail page is OWNER-only behind a form login, so on a phone the link lands on `/login`. Worth doing *after* someone checks the mobile login story; premature now. |

**Recommendation: E2**, recorded so the idea is not lost.

### 6. The year overview — deliberately not

Its whole vocabulary is three tints and one glyph, and day cells are display-only with no link and
no hover text. Adding a per-entry link is exactly the "show everything, smaller" failure that
CLAUDE.md's zoom-out rule was written against. **No.**

---

## Slices

Each ships on its own and leaves the tree green.

**Slice 1 — the venue on `/conferences`.** No new route, no new event, no projector change:
`ConferenceView` already carries `venueName`. Render it under the name (or in the City cell as
`venue · city`, Ted's call). *This is the whole of Ted's original complaint and it is worth shipping
first, before any of the below.*

**Slice 2 — the detail page.** `ConferenceDetailController` (`GET /conferences/{id}`) +
`ConferenceDetailRenderer` (j2html) + an OWNER-only view record folding `ConferenceProgress`,
`ConferencePlanned` and `CfpOpened`. Entry points **A1** and **C1** land here. Unknown id →
`redirect:/conferences`, matching `ChangeGatheringController`.

**Slice 3 — Change Conference.** `ConferenceChanged` + golden sample + `ChangeConferenceCommand`
(with D5's validation) + `ChangeConferenceController` (`GET`/`POST /conferences/{id}/change`,
Thymeleaf) + a **Change** link on the detail page. Every projector that folds `ConferencePlanned`
grows a `ConferenceChanged` arm — find them via `EventTypes` and the lifecycle-propagation
scenario pattern, not by grepping.

**Slice 4 — the calendar pencil.** `editPath` on `EntryDetails.Conference`, the `ownerActions` arm,
and **both redaction tiers**: `PublicCalendarProjectorTest` asserting the path is not in what the
projector emits, and a `CalendarRedactionSecurityTest` case asserting the anonymous body
`doesNotContain` it. Entry point **B1**.

## Tests worth naming in advance

- **`AuthorizationMatrixTest`** rows for both new routes. `/conferences/*` already gates them, which
  is precisely why the matrix row matters — nothing else would notice if that pattern changed.
- **A round-trip test on `ConferenceChanged`** that edits one field and asserts the other six
  survive, `format` and `infoUrl` named explicitly (D4).
- **A CFP panel test per row of D3's table** — four states, four different sentences, driven from
  real events through the real fold. The `CFP not recorded` vs `no CFP exists` pair is the one that
  will be got wrong.
- **`GoldenEventDeserializationTest`** case for `ConferenceChanged`, same change.
- **`ProjectorsDependOnEventsAloneTest`** already covers the new projector if one is added — it
  scans every `*Projector` in `application`, so keep the suffix.

## Open questions

- **Q1. One page or two?** D1 argues two and says how to collapse it to one. Ted's call, and it is
  the only decision here that changes the shape of the work rather than its size.
- **Q2. Does changing a conference's venue zone re-stamp a recorded CFP deadline?** `CfpOpened`
  derives its zone from `ConferencePlanned`'s dates. Three answers: leave it (the deadline silently
  means a different instant), block a zone-changing edit while a CFP is recorded, or emit a fresh
  `CfpOpened` with the same wall-clock time in the new zone. **Leaning the third**, since
  re-recording a CFP is already how a moved deadline is corrected — but it means an edit to one
  aggregate writes a second event, which deserves an explicit yes.
- **Q3. Should `/conferences/{id}` be reachable for a *dropped* conference?** Rejected and
  organizer-cancelled conferences leave both calendars and survive only behind
  `/conferences?dropped=show`. The detail page is where "why did this drop out?" is answerable, so
  probably yes — but then the Change form has to decide whether a dropped conference is editable.
- **Q4. Does Change Conference need a Cancel/undo sibling?** `ConferenceCancelled` exists as an
  event but has no owner-facing action (`Future_Feature_Slices.md`). Out of scope here; noted so
  slice 3 does not grow one by accident.

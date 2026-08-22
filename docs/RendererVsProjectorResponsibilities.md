# Renderer vs. Projector: whose job is what?

**Status:** open discussion — nothing decided, nothing to implement yet.
**Opened:** 2026-08-18, out of the "seven controllers gained a `ScheduleGapProjector`" complaint.
**Reopened:** 2026-08-19, on a second and different occasion — `CalendarEntry` growing a field that
applies to one `EntryKind` only. See **"Second occasion"** below; the original discussion is
unchanged above it. **That occasion is DONE: S2 + E2 shipped 2026-08-21** in two commits — one
public calendar projector (which deleted `CalendarEntryRedactor`), and a core record plus a sealed
`EntryDetails` with `kind()` derived from the details. The *original* question at the top of this
document — renderer vs. projector responsibilities in general — remains open and undecided.
**Question to pick up:** *what is the job of a renderer, versus the job of a projector, and which of
them should a controller depend on?*

## How this came up

The state-aware Schedule Problems nav link (built 2026-08-17, reverted 2026-08-18) needed one fact —
"are there schedule problems?" — on eight pages. Getting it there meant injecting
`ScheduleGapProjector` into seven view controllers that had no other use for it, and threading a
`hasScheduleProblems` boolean through every renderer signature. Ted's reaction:

> I don't like that so many (seven!) controllers now have a new dependency just to decide whether
> schedule problems shows up in the nav. That's a very low return on a huge increase in
> dependencies, which causes an increase in coupling and complexity and testing.

The link is now unconditional and all of that is gone. But the episode exposed a question the
codebase has never answered explicitly, and the answer would have told us up front where that fact
should have been fetched.

## Where things stand today

Every view controller looks like this:

```java
@GetMapping("/booked-flights")
public ResponseEntity<String> bookedFlights(@RequestParam(required = false) String filter) {
    TimeView timeView = TimeView.fromParam(filter);
    Instant now = Instant.now(clock);
    return ResponseEntity.ok()
            .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
            .body(BookedFlightsRenderer.render(projector.views(timeView, now), timeView));
}
```

- the **projector** is an injected instance, and holds the read model
- the **renderer** is an all-static class, called directly, a pure function from view records to a
  markup `String`
- the controller does I/O only: parse params, capture `now` at the boundary, query, render, wrap

## The question

Ted's framing: *what's the job of a renderer vs. a projector?* Some ways to cut it:

- **Does a renderer only format data handed to it, or may it fetch what it needs?** Today: only
  formats. If a renderer may fetch, page-level facts like "are there schedule problems" stop being
  the controller's problem, but renderers stop being pure functions.
- **Is a projector a *read model* (a queryable store of view records) or a *page model* (everything
  one page needs)?** Today it's the former, and nothing owns the latter — which is exactly why the
  nav fact had nowhere to live and ended up smeared across seven controllers.
- **Is there a missing third thing?** A page/view-model object between projector and renderer that
  assembles everything a page needs (rows + nav state + zone display), leaving the renderer purely
  presentational and the controller purely I/O. That would have had an obvious home for the nav fact.
- **Should renderers be instances at all?** They're the codebase's main surviving static-utility
  classes, against the standing preference for instance methods. That preference exists to avoid
  hidden dependencies and unsubstitutable policy; a pure markup function arguably has neither.

## Options considered so far

### A. Status quo — inject the projector, call the renderer statically

Renderers stay pure functions, which makes the cheapest and most numerous tests in the codebase
possible: `render(List.of(view1, view2), FUTURE)` → assert markup, no mock setup. Roughly 100 such
call sites. Controllers stay honest I/O.

Cost: renderers are static, so unsubstitutable; and there is no home for a fact that spans pages.

### B. Inject the renderer into the controller; renderer holds the projector

Controller becomes `renderer.render(timeView, now)`. Maximally thin controller.

Cost: renderers stop being pure functions of their arguments, so every renderer test needs a
projector stub. That's a real loss across ~100 call sites, and it's the main argument against B.

### C. Inject both — renderer as an instance, still taking view data as arguments

`renderer.render(projector.views(timeView, now), timeView)`. Renderers stay pure functions of their
arguments (tests only change at construction), and the static-utility classes go away.

Cost: one more constructor param per controller, Spring wiring for eight renderer beans, and
`@WebMvcTest` slices need the renderer bean present (mock for mapping/status tests, `@Import` for
body assertions). Buys substitutability that nothing currently wants.

### D. A page/view-model layer between projector and renderer

The missing-third-thing option. Assembles what a page needs; renderer formats it; controller wires
HTTP. Most room for cross-page facts, most new machinery. Not sketched in any detail yet.

## The distinction worth keeping either way

Whatever we land on, this one held up under the nav episode and is worth preserving:

> Coupling a class needs **for its own job** is not the problem. A view controller holding its own
> projector *is* its job — turning an HTTP request into a read-model query and a response.
> Coupling a class carries for **someone else's concern** is the smell. `ScheduleGapProjector` in
> `BookedFlightsController` was the second kind.

A useful test when adding a dependency: *would this class still need it if the other page didn't
exist?*

## Trigger to revisit

Concretely, the moment a renderer needs a **real collaborator** — a stateful nav component, a
configured formatter, an i18n bundle — it stops being a pure function and option A stops being
tenable. That is the natural point to pick this document back up. Until then there is exactly one
user for any abstraction here, which is not enough to build one
(see the standing "no abstraction before the second user" rule).

**What actually happened (2026-08-19):** the document was picked up again, but *not* through this
trigger — no renderer has yet needed a real collaborator. It was a calendar read-model shape
question instead, coming at the same split from the projector side. See "Second occasion" below.
The trigger above is still unfired and still worth watching.

---

# Second occasion: `CalendarEntry` is growing fields that don't apply (2026-08-19)

**Status: DONE 2026-08-21 — S2 + E2, both commits shipped.** Raised at the start of slice 2 of
`docs/ConferenceSubmissionTrackingPlan.md`. The options below are kept for the reasoning trail; the
decision and its terms are in **"Decision (2026-08-19)"** at the end of this half.

## How this came up

Slice 2 stamps an attendance commitment onto each conference calendar entry, so the public calendar
can show a **"Maybe"** chip on a speculative conference. That plan's own note said to "give
`CalendarEntry` a convenience overload defaulting to `CONFIRMED` so the other projectors are
untouched and only the conference projector passes it." Ted's reaction:

> I'm not happy with this growing `CalendarEntry` that has fields that don't apply to all kinds.
> We need a better design here.

Then, on where the work should go instead:

> What if we push some of the rendering into the projectors? My initial design for this project
> intended projectors to do as much rendering as possible, with the desire that the renderer
> assembles pre-rendered pieces.

So this occasion asks the document's title question from the *other* end: not "may a renderer fetch?"
but "how much may a projector render?"

## What the code actually looks like (verified 2026-08-19)

- `CalendarEntry` is one record, 10 fields, shared by all six `EntryKind`s. Three fields already
  apply to some kinds only: `mapsUrl` (LODGING, GATHERING), `speaking` (GATHERING), `editPath`
  (FLIGHT, TRAIN, LODGING, GATHERING). `commitment` would be the fourth (CONFERENCE).
- Two convenience constructors (`CalendarEntry.java:33` and `:45`) exist **only** so that projectors
  with no use for those fields need not name them.
- 10 production construction sites across the six calendar projectors, plus 6 in the redactor.
- `CalendarEntryRedactor` deliberately uses the canonical constructor in all six branches, so a new
  field breaks its compilation. That is the forcing function of redaction rule 1.
- Redaction happens at exactly one line: `CalendarRenderer.java:300`,
  `.map(e -> isPublicUser ? REDACTOR.redact(e) : e)`.
- The calendar has **three** viewer states, not two: anonymous (`isPublicUser`), family, and owner
  (`isOwner`, which drives the edit pencil). Redaction splits public from the rest; the pencil splits
  owner from the rest.
- `CalendarViewBuilder` does two different jobs on one entry: **layout** — week splitting, day
  columns, spans, per-kind sub-row packing, the `entry--from-left` / `--to-right` / `--continuation`
  classes — and **content** — title, map link, edit pencil, subtitle lines, speaking badge.
- `SubtitleLine` (`Text` / `At` / `Range` / `FixedRange`) is already a small typed content tree the
  projector fills and the renderer walks. Whatever we do here has a working precedent in the codebase.

## These are two separate problems

Worth stating plainly, because the first pass of this discussion conflated them and the conflation
changed the recommendation:

1. **Shape.** One record carries fields that do not apply to all kinds.
2. **Redaction.** The redactor is a *deny-list applied after the fact*, over a record that already
   holds the private values.

**Per-audience projection fixes (2) and barely touches (1)** — it removes `editPath` from the public
record and nothing else; a public conference still carries `mapsUrl` and a public flight still does
not. **Per-kind typing fixes (1) and does nothing for (2).** A design that wants both takes one
option from each list below.

## Options for problem 1 — the shape of `CalendarEntry`

### E1. Sealed interface, one record per kind

`CalendarEntry` becomes a sealed interface; `ConferenceEntry`, `GatheringEntry`, `FlightEntry`,
`TrainEntry`, `LodgingEntry`, `PrivateEventEntry` each hold only their own fields. The interface
declares the common accessors (`start()`, `end()`, `mainTitle()`, `subTitle()`).

- A flight *cannot* hold a commitment; the compiler prevents it.
- The redactor switches on the type exhaustively, so a new kind breaks the build.
- Cost: 10 call sites change; the renderer must branch to draw kind-specific content; `EntryKind`
  becomes redundant except as the lane ordering.
- Cost: the largest diff of the four, landing before any behaviour change.

### E2. Common core plus a sealed `details` field

`CalendarEntry` keeps the common fields and gains one: `EntryDetails details`, a sealed interface
with `ConferenceDetails(commitment)`, `GatheringDetails(mapsUrl, speaking, editPath)`,
`TravelDetails(editPath)`, and so on.

- The core record stops growing; kind-specific fields live in one place per kind.
- The redactor switches on the details type and returns redacted details, so the forcing function
  survives.
- Cost: materially smaller than E1 — projectors keep their constructor shape plus one argument.
- Risk: two parallel hierarchies (`EntryKind` and `EntryDetails`) that must agree.

### E3. Decoration list

Replace `speaking` and `commitment` with `List<EntryBadge>`, built by the projector; the redactor
filters the list against a per-kind allow-list.

- New markers need no new field.
- Cost: does **not** fix `mapsUrl` and `editPath`.
- Risk: the allow-list is data, not code — the compiler stops checking it, which weakens redaction
  rule 1. Only acceptable when paired with S1/S2, which removes the need to filter at all.

### E4. Group the fields (rejected)

`EntryLinks(mapsUrl, editPath)` + `EntryMarkers(speaking, commitment)`. Field count falls from 11 to
8, but a flight still carries an empty marker group. This hides the problem rather than solving it.

## Options for "how much may a projector render?"

### M1. The projector emits markup

Each projector produces a ready HTML fragment for the entry's content; the renderer places the
fragment in the grid.

- Only the **content** job can move. Layout needs the week grid, which the projector does not have.
- **Blocker A:** the redactor runs between the projector and the renderer and works field by field.
  Against markup, redaction becomes string surgery on HTML. Redaction rule 1 dies — unless M1 is
  paired with per-audience projection (S1/S2), which removes the redactor entirely.
- **Blocker B:** the viewer is unknown at projection time. A projection is built once, from events;
  `isOwner`, `isPublicUser`, `today` and the viewer zone arrive later, per request. The edit pencil
  and the today tint cannot be baked in at all.
- Cost: `application` gains a dependency on j2html — a layering change.
- Cost: projector tests become markup tests, in six projectors.

### M2. The projector emits a typed content tree

Replace `mainTitle`, `subTitle`, `mapsUrl`, `speaking`, `commitment` with `List<EntryPart>`, a sealed
interface (`Title`, `LinkedTitle`, `TimeLine`, `TextLine`, `Badge`). The projector builds it; the
renderer walks it and emits markup.

- Delivers the intent behind "projectors do as much rendering as possible" without putting markup in
  `application`. `SubtitleLine` already proves the pattern.
- The record stops growing: a new marker is a new part type, not a new field.
- Cost: the redactor must *filter a list* — data-driven, not compiler-checked. Same weakness as E3,
  and the same fix: pair it with per-audience projection.
- Cost: the renderer keeps its layout code and gains a walker.

**A way to restore compile-time safety under M2:** type the parts by audience. Make `EntryPart`
sealed and add a sub-interface `PublicSafePart`; the public list is `List<PublicSafePart>`. A
`TimeLine` (it carries a `ZonedTimestamp` and emits a UTC `datetime` attribute) does *not* implement
`PublicSafePart`, so a travel time cannot compile into a public list — **redaction rule 2 encoded in
the type system**. `TextLine`, `Badge` and `FixedRange` do implement it; `EditPencil` and `MapLink`
do not.

## Options for problem 2 — per-audience projection

The idea: stop redacting after the fact, and never build the private value into the public model at
all.

### S1. Mirror per kind

`PublicConferenceCalendarProjector`, `PublicFlightCalendarProjector`, and four more. Six new classes,
six new beans, two aggregators; registered projectors go from 21 to 27.

### S2. One public calendar projector for every kind

A single class handles all 13 event types below and emits public entries directly. One new class, one
new bean; the six owner projectors are untouched.

The 13 event types it must handle: `ConferencePlanned`, `ConferenceCancelled`,
`ConferenceAttendanceDeclined`, `FlightBooked`, `FlightChanged`, `TrainBooked`, `TrainChanged`,
`HotelBooked`, `HotelChanged`, `HotelBookingCancelled`, `GatheringPlanned`, `GatheringChanged`,
`PrivateEventPlanned`. Slice 2 would add `ConferenceAttendanceConfirmed` to two projectors rather
than one.

S2 fits the shape of the thing: the public calendar is **one page with one audience**, not six read
models.

### Why per-audience projection is attractive

- **The public projector is an allow-list written as code.** It reads only the fields it names, so it
  cannot leak a field it never reads. The redactor is a deny-list applied afterwards.
- **The failure mode is fail-closed.** Forget to handle a new event or field in the public projector
  and the data is *absent* from the public calendar. A leak needs a deliberate line of code. That is
  CLAUDE.md redaction rule 6 ("when in doubt, redact") made structural.
- **One file to audit.** A security review reads one class and sees the entire public surface;
  today it reads six projectors plus the redactor.
- **The public model may differ in shape, not only in content.** A `NOT_GOING` conference is simply
  never added; a private event is added as "Busy" from the start.
- **It deletes the `PRIVATE_EVENT` reverse-engineering hack.** `CalendarEntryRedactor` currently
  rebuilds a private entry from the *owner's* subtitle — filter for a `Range`, then take "the city is
  the last `Text`". That is fragile. A public projector holds the `Address` and states the city
  directly.

### What it costs

- **The compile-time forcing function is gone.** Today a new field breaks six redactor branches;
  under S1/S2 nothing breaks when a projector forgets a case. The trade is a compiler check for a
  fail-closed default plus a test tier.
- **Event handling is written twice** (owner and public), and a new event type must be handled in two
  places — the known projector-exhaustiveness hazard, guarded the usual way with
  lifecycle-propagation scenario tests.
- One more full replay at boot for S2 (six more for S1). Cost scales with event count; the stream is
  small today, and this has not been measured.
- `CalendarEntryRedactor` and `CalendarEntryRedactorTest` are deleted;
  `web/CalendarRedactionSecurityTest` survives unchanged and becomes the primary guard. Keep it, and
  extend it whenever a kind is added — it replaces the compiler.

### Precise change list for S2, taken alone

**New — 4 files**

| File | Content |
|---|---|
| `application/PublicCalendarProjector.java` | One class, all 13 event types, public entries only |
| `application/PublicCalendarEntry.java` | Record: `CalendarEntry` minus `editPath` |
| `application/CalendarSegment.java` | Interface declaring what layout needs: `kind`, `start`, `end`, titles, subtitles; both records implement it |
| `test/.../application/PublicCalendarProjectorTest.java` | Per-kind public-content tests |

**Changed — 8 files**

| File | Change |
|---|---|
| `infrastructure/EventSourcingConfig.java` | One new bean + `bootstrapper.register(...)`; 21 projectors → 22 |
| `web/CalendarController.java` | Choose the source by audience: public projector, or `calendarAggregator.allEntries()` |
| `web/CalendarRenderer.java` | Delete line 300 (the `REDACTOR.redact` map); accept `List<CalendarSegment>` |
| `web/CalendarViewBuilder.java` | Accept `CalendarSegment`; `editPath` moves behind a type check or its own accessor |
| `web/CalendarRendererTest.java`, `web/CalendarViewBuilderTest.java` | Type changes |
| `application/PrivateEventCalendarProjector.java`, `application/EventCalendarSubtitle.java` | Comments naming the redactor |
| `CLAUDE.md` | The redaction section names `CalendarEntryRedactor` and its rules 1–2; rewrite for the new mechanism |

**Deleted — 2 files:** `application/CalendarEntryRedactor.java`,
`test/.../application/CalendarEntryRedactorTest.java`.

**Unchanged, and this is the point:** the six owner projectors and their six tests,
`CalendarAggregator` and its test, `CalendarFeedController` (the iCal feed is deliberately
unredacted owner data and never used the redactor), `web/CalendarRedactionSecurityTest`,
`SecurityConfig`, `AuthorizationMatrixTest`.

## Side question: should `EventStreamConsumer` advertise its event interest?

Asked 2026-08-19, together with a possible `findByEvents(Set<Class<?>>)` on `EventStore`, so a
projector is not handed events it ignores.

**The facts that decide it:**

- `EventStore` loads all events **once**, at construction, into an in-memory `List<StoredEvent>`
  (`EventStore.java:43`). Deserialization and upcasting happen once, not per projector.
- `findAll()` copies that list. At boot, 21 projectors cause 21 copies.
- Live dispatch hands each subscriber the 1–3 events of one append batch.
- Ignoring an event is a `default -> {}` arm of a pattern-match switch — nanoseconds.
- `EventStore` already instruments this: `eventstore.subscriber.duration` per subscriber, and a
  slow-notification warning above 100 ms.

**On performance: no.** `findByEvents(Set)` still has to traverse the same in-memory list to filter
it; it saves the copy size and some ignored switch arms, neither of which is a measured problem.
Read the existing timer before changing the API.

**On correctness: the obvious form makes it worse.** A declared `Set<Class<?>> interestedIn()` is a
second statement of what the `switch` already says. When the two disagree, events are dropped
silently — a new failure mode, and exactly the class of bug the projector-exhaustiveness rule already
guards against.

**One form is safe:** don't declare the set, *derive* it from registration — replace the switch with
per-type registration (`on(ConferencePlanned.class, this::conferencePlanned)`), so the interest set
*is* the registration and drift is impossible. `EventStore` can then index subscribers by type, and
`findByEvents` falls out of the same map. But that changes all 21 projectors and gives up the
exhaustive `switch` over payload types that the codebase uses today.

**Recommendation:** keep `handle(Stream)`; add nothing now. Revisit when the slow-notification
warning fires or boot replay is measurably slow.

## E1 vs. E2, assuming S2 — the analysis that settled it

The question Ted put: *number of records is not the only useful measure; as more kinds arrive, some
public and some private, what is the near-future impact on **coupling** and **testability**?*

The near future is concrete. `docs/ConferenceSubmissionTrackingPlan.md` and
`docs/archived/PrivateSocialEventPlan.md` between them require at least one more private kind — the
company-internal speaking engagement, which must **not** be modelled as a conference or gathering.
Every private kind after it is the same case. So assume: kinds keep arriving, and most new private
ones collapse publicly to "Busy".

### The difference that matters: the public side can collapse

Under S2 the public model is built independently. It does not have to mirror the owner model.

- **E2 lets it collapse.** The public side needs roughly three shapes: a fully public event, a travel
  item, and "Busy". A new private kind adds an owner details type and *reuses* `BusyDetails`
  publicly. **The public shape count stays flat as kinds grow.**
- **E1 cannot collapse.** E1 ties shape to kind, so each kind wants its own public record. To share
  one `PublicBusyEntry` across several kinds you must put `kind` back as a field — which is the enum
  E1 exists to remove. E1's own rule breaks on the public side first, and it breaks on the very next
  kind we know is coming.

### Coupling

- Adding a **field** to one kind: **equal**. E1 changes one record; E2 changes one details type. Small
  blast radius either way.
- Adding a **kind**: **near equal**. Layout code reads only the core fields in both designs, so
  layout never breaks. Content switches break in both — by design, and that is wanted.
- E2 carries one extra risk: `EntryKind` and `EntryDetails` can disagree.

**That risk is removable, and removing it is a term of the decision.** Do not store `kind` as a
field: derive it, `kind()` returns `details.kind()`. One source of truth, drift impossible. This
deletes E1's main advantage.

### Testability

- **Owner tests:** equal. Both let a test name only the fields that kind uses; neither forces a
  not-applicable argument. E1 fixtures are marginally cleaner (one object, not two).
- **Public tests:** E2 is better. One parameterized test states "every private kind projects to
  `BusyDetails`". Under E1 that is one test per public record type, and the count grows with kinds.
- **The security invariant:** this is the decisive one. Under E2, "public entries carry only the
  permitted details types" is **one test that never needs editing**. Under E1 the same test
  enumerates a record list that grows with every kind — and a test that must be edited on every
  change stops guarding, because editing it is exactly what a leaking change would do.

### What S2 does to E1's remaining advantage

With S2 the redactor is gone, so a kind/details mismatch is a **rendering bug, not a leak**. E1's
compile-time strictness was worth most when it was the thing standing between a private field and an
anonymous viewer. Under S2 that job belongs to the public projector's allow-list, and E1's strictness
is worth correspondingly less.

### Conclusion

E1 is the better *stand-alone* shape and would be the right call if the redactor stayed. Under S2 it
loses on the two things asked about: it cannot collapse the public side as private kinds accumulate,
and its version of the public security invariant grows with every kind.

## Decision (2026-08-19)

**Take S2 + E2**, with `kind()` derived from `details`. Decided by Ted after the analysis above.

**The terms:**

1. **S2** — one `PublicCalendarProjector` handling every event type, emitting public entries
   directly. `CalendarEntryRedactor` and `CalendarEntryRedactorTest` are deleted;
   `web/CalendarRedactionSecurityTest` survives and becomes the primary guard.
2. **E2** — a core `CalendarEntry` record plus a sealed `EntryDetails`; kind-specific fields live in
   the details type.
3. **`kind` is derived, never stored.** `kind()` returns `details.kind()`, so the two hierarchies
   cannot drift. This is a condition of choosing E2, not an optional refinement.
4. **The public side collapses deliberately.** Aim for a small, fixed set of public details types
   (public event / travel / "Busy"). A new private kind reuses `BusyDetails` rather than adding a
   public type.
5. **Add the invariant test** that asserts public entries carry only the permitted details types. It
   is the replacement for the compile-time forcing function that the redactor provided, and it must
   be written so that adding a kind does not require editing it.
6. **`EventStreamConsumer` is unchanged** — no advertised event interest, no `findByEvents`. See the
   side question above.

**Not taken, and why:** E1 (loses the public collapse; see the analysis above). E3/E4 (do not solve
the problem). M1 (puts j2html in `application`, and cannot bake in the per-request pencil). S1 (six
mirror classes where one public projector fits the one public page).

**M2 is not taken now, and is not closed.** A typed `EntryPart` content tree is compatible with
S2 + E2 and would shrink the core record further. Revisit it if the core record starts growing again,
or if the public details types begin duplicating markup decisions.

### Sequencing — decided 2026-08-19: slice 2 first

Slice 2 shipped **before** this refactor. Two small reviewable diffs, and CFP-season work proceeded
immediately. The cost, paid knowingly and visible in the tree today:

- `CalendarEntry` is an 11-field record with a fourth kind-specific field, `commitment`, null on the
  five non-conference kinds. That is the exact complaint that reopened this document, now one field
  worse — deliberately, and temporarily.
- The redactor's six branches each name `commitment` (one passes it through, five write `null`), so
  the compile-time forcing function still holds. `CalendarEntryRedactorTest` gained
  `commitmentIsDroppedFromEveryNonConferenceKind`, which loops the five kinds — a test that *would
  not need to exist* under S2, because a public projector never reads the field for those kinds.
  Treat it as a marker of the debt rather than a permanent fixture.

**What this changes for the refactor:** nothing structural. `commitment` becomes the first field of
`ConferenceDetails` under E2, and the public collapse under S2 is unaffected — the projector already
collapses every speculative state to `WATCHING` before the entry is built, which is the shape S2
wants. If anything slice 2 is a small argument *for* S2: `CalendarEntryRedactorTest`'s new loop is
precisely the "invariant test that grows with the kinds" the decision section warned about.

### The commit split — decided 2026-08-21: **E2 first**

Two commits, in this order:

1. **E2 as a pure reshape**, no behaviour change. `CalendarEntry` loses its kind-specific fields to a
   sealed `EntryDetails`; the seven projectors, the redactor and `CalendarViewBuilder` follow.
2. **S2**, the security change: `PublicCalendarProjector` plus the public details types, the
   controller choosing its source by audience, and `CalendarEntryRedactor` deleted.

**Why this order.** The reshape is the large mechanical diff — around seventy construction sites
across ten test files — and putting it first means it lands with the redactor's compile-time forcing
function still armed. The security change is then a small, focused diff that can be read on its own.
The reverse order gets the deletion out of the way with nothing thrown away, but it removes the guard
*before* the big diff, which is backwards.

The cost, paid knowingly: `publicRoute` lives in `EntryDetails.GroundTransfer` for exactly one
commit, and the redactor is retargeted onto the details types and then deleted. About fifteen lines
of throwaway.

**One-off migration check** (Ted, 2026-08-21): commit 2 adds a temporary test asserting that
`PublicCalendarProjector`'s output equals `redactor.redact(ownerEntry)` for every kind's fixtures,
and deletes it together with the redactor in that same commit. `CalendarRedactionSecurityTest` covers
only the cases it names; this proves the rewrite changed nothing an anonymous viewer sees.

### What commit 1 built (2026-08-21)

Shipped as designed, both tiers green (1343 unit + 50 js). Three notes worth carrying into commit 2:

- **The record was worse than this document recorded.** Ground transfer had landed since, so
  `CalendarEntry` was **13 fields with four convenience constructors**, not 11 with two — and there
  are **seven** calendar projectors, not six. It is now **7 fields with one** convenience
  constructor (the no-continuation case), and `kind()` is derived.
- **Three redaction tests became unwriteable, which is the point.**
  `conferenceSpeakingIsDropped` and `commitmentIsDroppedFromEveryNonConferenceKind` are **deleted**:
  no non-conference details type has a commitment, and `EntryDetails.Conference` has no `speaking`,
  so neither case can be constructed. The second was the "test that grows with the kinds" this
  document warned about; it is replaced by `redactionNeverChangesTheKind`, which states one
  invariant over a fixture list. Two assertions in `CalendarRedactionSecurityTest` went the same way
  (a flight's maps URL, a private event's edit link) and carry a comment saying the guarantee is now
  structural.
- **`CalendarViewBuilder` gained three exhaustive switches** — `titleLink`, `ownerActions`,
  `badges` — instead of reading four flat fields. A new kind cannot be added without deciding, for
  each, what it renders. Along the way the gathering's link stopped being called `mapsUrl`: it is an
  `infoUrl` and always was, and three test names said so already.

All six mutations tried against the new tests failed for the right reason (a leaked gathering
`editPath`, a transfer redacted into the wrong lane, each of the three switches neutered, and a
details record lying about its kind).

### What commit 2 built (2026-08-21)

`CalendarEntryRedactor` and `CalendarEntryRedactorTest` are **gone**. `PublicCalendarProjector`
handles all sixteen event types and emits public entries directly; `CalendarController` picks the
read model by audience; `CalendarRenderer` strips nothing and its `REDACTOR` field is deleted.
Both tiers green (1346 unit + 50 js).

**Resolving a tension the decision left open.** Term 3 says `kind()` is derived, never stored; term 4
says the public side collapses to a small fixed set. Those collide, because a collapsing public type
still has to answer `kind()` for its lane. The resolution: **`EntryDetails.PublishableTravel`, a
sealed interface over four empty records** (`PublicFlight`, `PublicTrain`, `PublicGroundTransfer`,
`PublicLodging`). Each answers its own constant kind, so nothing is stored and nothing can drift;
the renderer's switches match all four in a single arm, so the arm count does not grow either. The
public set is therefore `PublicConference`, `PublicGathering`, `PublishableTravel` and `Busy` — three
shapes, as term 4 asked.

**`Busy` collapses the lane as well as the details, and that is redaction rather than economy**: a
second private kind with a public lane of its own would let a stranger tell it apart from a dinner by
lane alone. Recorded in CLAUDE.md as a standing rule for new private kinds.

**Term 5's invariant is a compiler check *and* a test.** Inside the projector every entry is built
through a private `entry(...)` helper whose last argument is an `EntryDetails.Publishable`, so an
owner details type will not compile onto the public calendar;
`PublicCalendarProjectorTest.everyEntryCarriesOnlyPublishableDetails` is the runtime backstop, and it
names no type list, so adding a kind does not require editing it.

**`publicRoute` is deleted.** It existed only because the redactor could not derive a city from a
hotel name; the public projector builds the route from the event, so the owner's ground-transfer
entry no longer carries a field on the public calendar's behalf.

**Two test files changed character, both deliberately:**

- `CalendarRedactionSecurityTest` keeps its name and its role — it is now stated in its javadoc to
  be *the* primary guard, the compile-time forcing function being gone. Its anonymous fixtures are
  built by driving **real domain events through a real `PublicCalendarProjector`**, so the
  projection logic under test is production's, not a hand-written entry that could assert whatever
  the test wished. Owner and family fixtures stay hand-built entries.
- `CalendarRendererTest.publicUserSeesRedactedHotelName` is **replaced**, not deleted: the renderer
  no longer redacts, so the claim worth pinning is the opposite one —
  `entryContentRendersIdenticallyForEveryViewerBecauseTheRendererStripsNothing`, which is redaction
  rule 4 as a test and would catch a renderer that started re-deriving viewer identity.

**The one-off equivalence check did its job and is gone**, as agreed. It drove one event of every
kind through both pipelines, rendered each through the real `CalendarRenderer`, and asserted the two
anonymous pages were byte-for-byte identical — comparing markup rather than records, since the two
pipelines emit different details types by design. It caught both mutations tried against it (a
changed lodging title, and a private event's real title published instead of "Busy") before being
deleted with the redactor.

Three further mutations were verified against the permanent tests, each failing at all three levels
(projector unit, security chain, and — while it still existed — the equivalence check): a transfer
publishing its hotel name via `ownerLabel`, a flight's departure/arrival times reaching the public
subtitle, and the controller reading the owner's entries for an anonymous viewer.

## Related

- `docs/ConferenceSubmissionTrackingPlan.md` — slice 2, the change that raised the second occasion
- `CLAUDE.md` — "Redaction: anonymous viewers are a first-class threat model"; rules 1, 2 and 5 are
  the constraints every option above has to satisfy
- `docs/Cleanup_Tasks.md` — the nav standardization entry, including the revert and its reasoning
- `CLAUDE.md` — "Presentation formatting stays out of the domain" (the projector/renderer split as
  it applies to *formatting*, which is the one part of this question already settled)
- `CLAUDE.md` — "Time comes from the injected Clock" (the boundary rule that keeps `now` flowing
  inward rather than being fetched wherever it is wanted — the same shape of question as this one)

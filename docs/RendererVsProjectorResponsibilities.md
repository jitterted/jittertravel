# Renderer vs. Projector: whose job is what?

**Status:** open discussion — nothing decided, nothing to implement yet.
**Opened:** 2026-08-18, out of the "seven controllers gained a `ScheduleGapProjector`" complaint.
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

## Related

- `docs/Cleanup_Tasks.md` — the nav standardization entry, including the revert and its reasoning
- `CLAUDE.md` — "Presentation formatting stays out of the domain" (the projector/renderer split as
  it applies to *formatting*, which is the one part of this question already settled)
- `CLAUDE.md` — "Time comes from the injected Clock" (the boundary rule that keeps `now` flowing
  inward rather than being fetched wherever it is wanted — the same shape of question as this one)

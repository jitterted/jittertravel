# Correlation tracing — event → command → the page it was issued from

Status: `open` — plan only (2026-10-24), nothing built. **Replay is blocked on D6** (rewritten
event rows), which holds up slices 4 and 5. Slice 1 needs no new data and is not blocked. Slice 2
adds a `command_log` column, so it waits on D7 (the backup format), not on D6.

**Considered and tabled 2026-11-05 (Ted):** turning each id in a payload on `/admin/eventlog` or
`/admin/commandlog` into a link to that log filtered by the id. It would show which rows mention an
id, not which caused which: a `NotifyFamilyCommand` carries the subject and the fact, never the
event that triggered it, so "booked, then notified" would be read off the order. Slices 1 and 2
here record that relation instead of inferring it, and Ted chose to wait for them.

## The question this must answer

> "What was I looking at when I clicked the edit button on an itinerary page?"

**The purpose is the decision, not the pixels** (Ted, 2026-10-24): *what did I see that would
affect the command I triggered?* So the bar is **complete inputs**, not a byte-identical page. A
reconstruction may differ in markup; it must not differ in anything a decision could rest on.

Inspiration: EventStoreDB's *Event Flow Visualization* — a correlation/causation tree laid out on a
time axis, each edge labelled with the time since its parent, nodes expandable and re-rootable.

## What exists today

- **Event ↔ command:** `event_log.command_id` (FK) and `command_log.event_ids`. Complete for every
  row ever written.
- **Stream:** events of one aggregate share its id (`TrainTripId`, …), so "back to the creation
  event" along one stream needs no new data.
- **Missing:** anything linking a command to the page it was submitted from, that page to the page
  before it, or a reactor's command (`NotifyFamily`) to the event that triggered it.

## D1 — A page is reconstructed by replay, never stored

Ted does not want pages stored (2026-10-24). A page is a function of three things, and each is
pinned by something already event-sourced or recordable:

| What the page depended on | Pinned by |
|---|---|
| projector, upcaster, renderer and curated-table code | the **release**: the commit that was deployed (a push to `main` deploys) |
| the facts | **events up to sequence N**, as they were stored at the time (D6) |
| everything else — `now`, cookies, parameters, role | the **recorded inputs** (D3), plus config moved into events (D5) |

To reconstruct: build that commit, load events up to N into a scratch database, start it with the
recorded clock, and issue the recorded request.

**Replay works only for releases that contain the replay hooks.** The release being replayed must
accept a fixed clock (today the `Clock` bean is hard-wired to `Clock.systemDefaultZone()` in
`EventSourcingConfig`) and read every input from the request. Nothing rendered before those hooks
ship can be replayed, and the trace says so rather than guessing.

**Building from the commit, not from a kept image:** the Maven wrapper and the pom pin the build.
I don't know whether Railway keeps old images or for how long, so the plan doesn't depend on it.

## D2 — `query_log`, a table beside the event log, not in it

Rejected: view requests as events in `event_log`. Every GET would write, pages would fail in
read-only mode (`CommandExecutor` throws), a view is not a domain fact, and the log could never be
pruned. The reconstruction is the same either way; only the table differs.

| Column | Holds |
|---|---|
| `query_id` UUID PK | minted at the boundary before rendering |
| `parent_query_id` UUID null | the page whose link led here (D4) |
| `release` text | the deployed commit |
| `log_sequence` bigint | the event store's last sequence when rendering began. Projectors are notified synchronously after an append, so this is what they had applied, give or take one append racing the render |
| `path` text | the route |
| `inputs` jsonb | the page's request record (D3), including `now` and the clock's zone |

Small rows, no page bodies, so the stored-page concerns (archived forms, CSRF tokens, size) are gone.

Plus `command_log.query_id` (the form the POST came from) and `command_log.caused_by_event_id`
(set by reactors). **No foreign key from `command_log` to `query_log`**: retention and restore (D7)
leave dangling references, and the trace renders those as "view not recorded" rather than failing.

**No separate correlation id.** The parent pointers are the chain, walked both ways by a recursive
CTE. EventStoreDB needs one because it queries through a projection; we don't.

## D3 — Inputs are explicit, and two ArchUnit rules keep them that way

Counted 2026-10-24 across the 50 controllers in `web`: 5 take a `HttpServletRequest`, 5 read a
cookie, 3 read the role or user from the request, 32 use the injected `Clock`.

1. **General rule, every controller:** request-derived inputs (cookies, role, user, headers) arrive
   only as method parameters, via `@CookieValue` or a small argument resolver. No
   `HttpServletRequest`, no `WebUtils.getCookie`, no `SecurityContextHolder`. That touches about
   five controllers, and it enforces "external inputs from the boundary" for the request the same
   way `NoAmbientClockReadsTest` does for time. `Clock` stays allowed: it is already guarded, and
   its `now` is recordable.
2. **Recorded pages only:** all inputs arrive as **one request record** (e.g.
   `ItineraryViewRequest`: `date`, `tz`, the zone cookie, role, `now`), and that record is what
   `query_log.inputs` holds. Not for all 50 controllers: for a page that can never lead to a
   command it is ceremony.

**What neither rule sees: configuration read inside a projector or a policy.** That is D5.

## D4 — The link carries the page it came from

"Clicked the edit button" is a **GET** of the edit form. Neither the Referer header nor a cookie
can tell two renders of the same URL apart, or two tabs. So an owner action link carries the id of
the page it was rendered on: `?viewed=<query_id>`. The id is appended in one place, as
`ProblemFix.explaining` does for `?problem=`. The edit form's own render records that id as its
`parent_query_id`. The form's POST carries its own `query_id` as a hidden field into
`command_log.query_id`.

So the answer is two hops back from the command: command → edit form as rendered → itinerary as
rendered, each reconstructible by D1.

**Scope:** OWNER only (anonymous and family renders are never recorded — they cannot lead to a
command, and recording them would be a log of who looked at Ted's calendar), and only pages that
can lead to a command. Start with `/itinerary` and the edit forms its links reach. Widen with a
convention test in the shape of `ProblemContextFragmentConventionTest`.

## D5 — Config that changes what is shown becomes events

Audited `EventSourcingConfig` 2026-10-24. Two values change what a page shows:

- `jittertravel.home-cities` → `HomeCities` → `ScheduleGapProjector` decides "at home" days, and so
  which missing-hotel gaps appear on the itinerary. That is exactly the decision-affecting kind.
- `jittertravel.today.fallback-zone` → `ViewerTodayZone`, the zone for "today" when the viewer has
  no zone cookie.

`base-url` and `family-notify.enabled` affect email only.

**Home cities become an event** (`HomeCitiesSet`, or a better past-tense name), set on a form and
seeded once from the current variable. Then replaying up to N gets the right home cities for free,
and `ScheduleGapProjector` is built from events alone (R12). This is a slice of its own, with its
own form, and arguably right regardless of tracing. **The fallback zone** is cheaper to record:
it only matters when the cookie is absent, so the request record holds the zone actually used.

## D6 — Rewritten event rows: BLOCKING, Ted to decide

`PostgresPersister.migrateEventPayloads` runs `UPDATE event_log SET payload…, schema_version…`. An
old release replaying events up to N then reads payloads in a shape it never knew, and either fails
or shows something different. Replay needs every event **as it was stored at the time**. Two ways:

- **No more in-place rewrites.** Upcast on read only. Simplest; gives up the eager migration's
  benefits (`docs/archived/LegacyEventEagerMigrationPlan.md`).
- **Keep the original** when a migration rewrites a row (a `superseded_payload` column, or an
  `event_log_history` table), so a replay can select each row as of the time it was read.

Until one is chosen, no page rendered before the most recent migration can be replayed.

## D7 — Backup and restore

`query_log` is **not** in the backup by default. After a restore, every `command_log.query_id`
dangles and the trace shows "view not recorded". If traces should survive a restore, that's
backup v4, decided before the `command_log` columns ship (they change backup JSON).

## The view

`/admin/trace/{eventId}`, j2html, OWNER-only, in `SecurityConfig` **and**
`AuthorizationMatrixTest` in the same change. Uses the image's layout: a time axis, an edge label for
the time since the parent, distinct shapes for **render**, **command** and **event**. Two axes:
**causal** (parent pointers) and **stream** (same aggregate id back to its creation event).
Selecting a render shows its release, sequence and recorded inputs, and how to reconstruct it.

Not copied from the image: "hover to see details" and "hold for two seconds to re-root". Neither
works on the iPad. Use tap to select and a visible "make root" link.

## Not covered, named out loud

- **What happened in the browser after the HTML arrived** (zone localisation by
  `BrowserZoneScript`, opened `<details>`, scroll). Its inputs (the browser's zone) could be
  recorded later if they turn out to matter for a decision.
- **Arrivals without a link:** a typed URL, a bookmark, or a form opened in a fresh tab ends the
  chain, marked as such.
- **Rows and releases from before this ships:** no cause, no replay hooks. Nothing is backfilled;
  an inferred cause would read as fact.

## Slices

1. **Stream axis** on `/admin/trace/{eventId}` — no new data.
2. `command_log.caused_by_event_id` from reactors.
3. **D3 rule 1**, converting the ~5 controllers that read the raw request.
4. **Replay hooks:** a fixed-clock setting, and a scripted reconstruction (build commit, scratch
   database up to N, issue the request).
5. `query_log` + request record + `?viewed=` for `/itinerary` and the edit forms it links to.
   With D6 settled, this slice answers the question.
6. Home cities as an event (D5).
7. Widen to every owner page with action links, with the convention test.

## Open questions for Ted

- **D6 (blocking):** stop in-place rewrites, or keep the original rows?
- D7: should traces survive a restore (backup v4)?
- D5: is home cities as an event wanted on its own merits, or only for tracing?

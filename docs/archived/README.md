# Archived plans

Plan docs whose work is **finished**. Nothing here is a task list, and nothing here is tracked:
if you are looking for what to do next, you are in the wrong directory — go to `../Backlog.md`
(the index of every plan and its status) or `../Cleanup_Tasks.md` (smaller fixes).

These are kept, not deleted, because the *reasoning* outlives the work. Most of the rules in
`CLAUDE.md` were argued out in one of these files, and several record alternatives that were tried
and rejected — which is exactly what you want before "improving" something that already lost this
argument once.

## The rule for putting a doc here

**A plan is archived when it owns no remaining work.** Shipping is not enough on its own: six of
the docs below shipped with a follow-up still named inside them, and each of those follow-ups was
**lifted into `../Cleanup_Tasks.md` first**, with a pointer back here for the reasoning. Archiving
never drops an open item — if a doc still owns work nobody else tracks, it stays in `docs/`.

**Moving the file is only half of it — `../Backlog.md` has to move too.** Its row leaves
**Open features**, gets cut to one line, and lands under **Done**; add a row here at the same time.
Skipping that is how eleven finished items were still filed under "Open features" on 2026-08-23,
which is exactly the thing that table exists not to do. The full checklist is in `../Backlog.md`'s
own intro, so there is one copy of it.

Established 2026-08-21, when eighteen docs moved in one pass. Anything added since arrived one at a
time, on the day its last open item closed.

## What's here

| Doc | Landed | Note |
|---|---|---|
| `YearOverviewPlan.md` | 2026-09-01 (archived 2026-09-04) | The zoomed-out map of `/calendar`. **Read this before adding anything to a summary or overview**: seven tints and seven glyphs were built to the mockup and cut to *three tints and one plane* the same day, because a busy month became a wall of pictures in which nothing's **absence** was noticeable — and absence is the signal (no plane on a future trip = its flights are not booked). That argument is now CLAUDE.md's "Zooming out is lossy on purpose". Also keeps why the **sticky month bands** went: a week is filed under its Sunday, so a band could not anchor a jump and a `gridEnd` on the 1st–5th left a month with none. Opens with a banner naming the five plan decisions the build reversed — do not read its body as describing the code. |
| `ConferenceSubmissionTrackingPlan.md` | 2026-08-18 → 2026-08-23, five slices | Conference commitment, the talk pipeline, and the CFP. **Read this before touching anything about how conferences are shown publicly** — CLAUDE.md's conference redaction rules were argued out here, and the argument is subtler than the rules: commitment is publishable *only because* every speculative state collapses to one `WATCHING`, so un-collapsing that enum silently makes the "Maybe" chip a leak. Also keeps a run of decisions that were reversed on contact with real use — "best-outcome-wins" (broken by the ordinary `Submitted → Rejected`), waitlisting (dropped, never met one), "radar" (my coinage, did not read), and slice 5's "only `GOING` occupies the schedule" (would have emptied the commitment-blind away band). Its one gap, no way to *change* a conference, is in `../Cleanup_Tasks.md` (Deferred). |
| `ProblemContextOnFixPagesPlan.md` | 2026-08-21 (archived 2026-08-23) | Why a fix link carries its problem with it. **Read this before adding a fix target** — CLAUDE.md cites it, and the three-step wiring it describes is invisible otherwise (the banner is an advice no controller mentions, so a missed step fails silently). Keeps the rule that the link carries a **reference, never the words** — a URL can be edited and goes stale the moment the problem is fixed in another tab — and that the wording is **reused** from the problem calendar's own view types rather than restated. Its one follow-up, ground-transfer endpoint prefill, went to `../Cleanup_Tasks.md` (Deferred). |
| `GroundTransferEndpointReadModelPlan.md` | 2026-08-23, three slices | Train stations as ground-transfer endpoints, and the read model that made them cheap. **Read this before adding a fourth endpoint kind** — the venue half of the hole is still open, and the shape to copy is here. Keeps the argument for **`Place`** (two readers deriving the same place from the same event, or a submitted transfer removes the gap it was entered to close — a missing value type, not a missing test), for **a read model per view** (the endpoints are their own projector, so a cancelled stay is *absent* rather than filtered and trains cost one arm), and for the line between **a fact about the event** and **anything needing `now`**. Also the two pairs that look like duplication and are not — a stay's label city against its matching place, and a stay's moment against the check-out that decides whether it is still offered — and why a station reads its zone off the event where a hotel still re-derives it. Four tails → `../Cleanup_Tasks.md` (Deferred). |
| `CuratedResolversToDomainPlan.md` | 2026-08-23 | Four curated resolvers and `ZoneResolutionException` moved from `application` to `domain`. **Read this before arguing about what belongs in `domain`** — CLAUDE.md's rule cites it, and the two readings people get wrong are argued here, not there: **a curated in-memory table is data, not I/O**, and an interface kept as a seam for a future non-static lookup is ordinary ports-and-adapters, so the interface is the domain's and any I/O-backed implementation is the `infrastructure` adapter. Also keeps the audit behind `DomainIsPureTest` — why the import check is a **whitelist** rather than a blacklist of today's libraries, and why `UUID` is exempt (the seven `*Id.random()` factories have **zero** `src/main` call sites, so production already mints at the boundary). One sub-story worth its own read: `Address`'s Jackson `@JsonAlias("state")` was **retired rather than relocated**, and the doc records the reasoning that was sound but wrong, the measurement that corrected it, and the mix-in that got written and then deleted the same day. Its one tail, the matching `setState` shims, → `../Cleanup_Tasks.md` (Open). |
| `SessionizePrefillPlan.md` | 2026-08-23 | Paste a Sessionize URL on `/plan-conference` and fill the form. Keeps the reasoning for **regex over jsoup** (and the rules that make that honest — per-field isolation, `&amp;` decoded last), and for why the planned Slice 1 / Slice 2 split **had to be abandoned**: the deadline field is read in the venue zone, which only the scrape supplies. Also the one place this codebase says **blank beats a defensible value** — an unresolvable zone leaves the deadline empty, because a shifted deadline looks exactly like a correct one. No follow-ups; its "deliberately does not do" list is non-goals, not deferred work. |
| `CalendarAwayBandPlan.md` | 2026-08-21 (`d4a9a3f`) | Turquoise away stripe on `/calendar`. Its one hypothetical follow-up — the same band on `/schedule-problems` — was **closed** by Ted, not deferred. |
| `EventTypeColumnNormalizationPlan.md` | built 2026-08-19, **run in production 2026-08-21** | `event_log.type` now holds one spelling per type. Keeps the runbook, the rollback analysis, and why the `EventTypes` aliases must **stay** append-only afterwards. Two unconfirmed post-click steps → `../Cleanup_Tasks.md`. |
| `GroundTransferPlan.md` | 2026-08-20 | The taxi/subway/shuttle hop that no entry kind could record; plan + cancel. **Change** is in `../Cleanup_Tasks.md` (Deferred, with its trigger). |
| `ProblemCalendarPlan.md` | 2026-08-20 | Slices 1–5 of the calendar view of `/schedule-problems`, ending in clash markers and fix links. Two open questions → `../Cleanup_Tasks.md`. |
| `ScheduleProblemsRewritePlan.md` | 2026-08-20 | Detection rebuilt on one located timeline. The mirror of D14 (gaps *into* home) → `../Cleanup_Tasks.md`. |
| `CalendarSubscriptionFeedPlan.md` | 2026-08-18 | Phase 1: token-gated iCal feed for hotel cancel deadlines, validated on a real iPhone. Phase 2 (full travel calendar) → `../Cleanup_Tasks.md`. |
| `LegacyEventEagerMigrationPlan.md` | 2026-08-16 | Eager legacy-row migration, the per-event `schema_version` stamp, and the boot-replay preflight. Referenced from `LegacyEventMigration`, `EventTypes`, `PostgresPersister`. |
| `PrivateSocialEventPlan.md` | 2026-08-13 | `EntryKind.PRIVATE_EVENT` and its own redacting branch. **Read this before adding any private-ish entry kind** — CLAUDE.md names it as the pattern to copy. Change + list view → `../Cleanup_Tasks.md`. |
| `EventOrientedBackupRestorePlan.md` | 2026-08-11 | Event-verbatim backup/restore replacing command replay; why restore is validate-then-apply, and why the live rebuild was reverted. |
| `GatheringConferenceUtcRolloutPlan.md` | 2026-07-27 / 2026-08-05 | UTC + zone storage rolled out to gatherings, then conferences. |
| `HomeCityPlan.md` | 2026-07-26 (`26d15ac`) | `HomeCities` and the home-aware behaviour the away band and gap detection both now rest on. |
| `ChangeGatheringPlan.md` | 2026-07-25 (`f28cb49`) | |
| `TrainFlightUtcRolloutPlan.md` | 2026-06-24 | Trains `daa7107`, flights `d2884fb`. |
| `ChangeHotelPlan.md` | 2026-06-21 (`d98af11`) | |
| `ChangeTrainPlan.md` | 2026-06-17 (`8390262`) | The template the other two Change slices followed. |
| `Remove-Local-Profile.md` | 2026-06-17 (`e962915`) | |
| `j2html_Migration_Analysis.md` | 2026-06-06 (`b0e6f11`) | Views migrated; the shared renderer infrastructure it proposed was never extracted — that follow-up lives in `../Backlog.md`. |
| `GeneralControllerRefactorPlan.md` | shipped | Open items 1 and 2 resolved; item 3 moved to `../Backlog.md`. |
| `UtcDatetimePlanReview.md` | folded in | The 2026-08-05 review of `../UtcDatetimeStoragePlan.md`; its findings were folded into that plan, so its checkboxes are no longer maintained. Historical record only. |

## Links, from in here

Paths in these docs were rewritten when they moved, so they still resolve: a sibling is a bare
name, a doc still in `docs/` is `../Name.md`, and repo-root files (`CLAUDE.md`, `DEPLOYMENT.md`,
`EventSourcingRulesHeuristics.md`, `TaggedEventStoreQueryingDesign.md`) are bare by the existing
convention. Source files that cite a doc here say `docs/archived/…`.

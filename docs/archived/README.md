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

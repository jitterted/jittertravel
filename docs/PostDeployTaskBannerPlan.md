# Post-Deploy Task Banner — one-off migrations and backfills that must not be forgotten

**Status: `open` — designed 2026-08-19 (Ted + discussion), nothing built.**
Scope was narrowed by Ted on 2026-08-19: this is about **one-off tasks that follow a deploy** —
migrations to run and backfills of data that did not exist before. It is *not* a general data-quality
nag. A hotel with no `cancelBy` is explicitly **not** a task (sometimes there is no free
cancellation: booked close to check-in, or a special offer), and a `CALL_FOR_PAPERS` conference with
no `CfpOpened` is at most a low-priority "timeout" nice-to-have, not part of this.

## Problem

A deploy can leave work that only Ted can do, and nothing in the running app says so:

- **Migrations** — `/admin/migrate-legacy-events`, `/admin/migrate-conferences`, and the
  `event_log.type` normalization shipped in `5df5358`, which is **outstanding against prod right
  now** as of 2026-08-19.
- **Backfills** — the conference attendance pass (dev2next, ExploreDDD, SoCraTes; J-Fall waits for
  slice 3), and every future one: `CfpOpened` (slice 3), the submission stream (slice 4),
  `datesConfirmed` (slice 5), booking provenance (`BookingProvenancePlan.md`). **Each schema
  addition mints a backfill**, so this is a recurring shape, not a one-off feature.
- **Config a deploy needs but does not have** — when the calendar feed shipped, `CALENDAR_FEED_TOKEN`
  and `JITTERTRAVEL_BASE_URL` had to be set on Railway. Missing one does not break loudly, it makes
  the feature **inert** (the feed 404s and reads as a wrong URL). Arguably the highest-value case
  here, because it is the only one that fails silently.
- **Restart required after a restore** — read models rebuild on restart only (the live rebuild was
  built then reverted, see `EventOrientedBackupRestorePlan.md`), so a restored database serves stale
  projections until someone restarts it.

Today the only record of any of this is a doc, which is invisible while using the app.

## The mechanism (Ted, 2026-08-19)

A **one-way latch**: a task ships with the feature that requires it, Ted ticks it off when done, a
"done" event is appended, and the latch goes inert. Later the now-dead declaration is deleted from
the code, so inert scaffolding does not accumulate.

### The split that shapes it: derivable vs acknowledged

- **Derivable** — the app can *count* the outstanding work: legacy rows remaining, missing config.
  These clear themselves; at zero the banner goes away with no bookkeeping and no way to lie.
- **Acknowledged** — the app **cannot tell**. A conference with no `ConferenceAttendanceConfirmed`
  is indistinguishable from one Ted genuinely has not decided on: `WATCHING` is a legitimate resting
  state, not evidence of a skipped backfill. There is nothing to count, so completion has to be
  asserted, and the assertion has to be durable.

Both flavours live in one registry; only the second needs the event.

### Recommended shape — declare in code, record only the completion

```
OneOffTask(id, description, actionPath, declaredOn, Optional<check>)   // in code
OneOffTaskCompleted(taskId, completedOn)                               // the only event
```

The banner shows every declared task that has **no** completion event and whose `check` (if it has
one) still reports outstanding work. Ticking a task off appends `OneOffTaskCompleted` through
`CommandExecutor.appendEvents(...)` — the internal-action path, per the CommandExecutor rule in
CLAUDE.md.

**Recommendation: do NOT append a declaration event at startup.** Ted's sketch had the task itself
latched in by a boot-time append; a declaration held in code and only the completion in the log is
strictly simpler and removes four problems:

1. **Read-only mode.** After a failed boot replay the store is read-only and `CommandExecutor`
   throws `ReadOnlyModeException`. A startup append would have to be wrapped so it cannot take the
   boot down — and read-only is *exactly* when Ted most needs to see a banner. With code-declared
   tasks the banner still renders with no write at all.
2. **Idempotency.** A boot-time append needs a "have I already declared this?" fold on every start,
   and gets it wrong once for every restart edge case. Nothing to get wrong if there is no write.
3. **The event log stays domain facts.** `OneOffTaskDeclared` is a fact about a *deployment*, not
   about Ted's travel, and it would be copied verbatim into every backup by `BackupService`.
   A completion is at least a real decision Ted made.
4. **Deleting the code is clean.** Removing a declaration removes the task; the completion event
   stays behind as harmless history. If the declaration were an event, removing the code would leave
   a live declaration in the log with no code to explain it.

**Restore semantics fall out correctly.** Restoring a backup taken *before* a completion resurrects
the task — which is right: the data is back in the state where the backfill had not happened.

## The automation loop: don't let inert code linger

Ted's idea: a pre-commit/pre-push hook that queries the site, sees the task was completed, and
prompts to delete the declaration.

**Worth doing, but advisory — never blocking.** The push gate is for correctness (`MUST-PASS gate`);
a housekeeping reminder does not deserve veto power over a push, and hooks that reach the network
fail on a plane, in a tunnel, and whenever Railway is redeploying. Concretely:

- print `task 'normalize-event-log-type' was completed 2026-08-20 — its declaration can be deleted`,
  then **exit 0**;
- treat any network failure as silence, never as a finding.

It also needs somewhere to query: `/admin/*` sits behind form login, which a hook cannot do. That
means a **token-gated read-only endpoint** in the shape of the calendar feed —
`/admin/one-off-tasks.json?token=…`, constant-time compare, 404 on wrong-or-missing token, its own
`SecurityConfig` matcher and `AuthorizationMatrixTest` row. That is real surface area for a
convenience, which is why the alternatives below may be better value.

**Two alternatives that need no network at all:**

- **Age check in the test suite.** Every declaration carries `declaredOn`; a registry test fails once
  a task is older than a generous limit (a few months). The build nags instead of the hook. Caveat:
  it fails for a task Ted simply has not done yet, which is not a code defect — so the limit has to
  be long enough that tripping it really does mean "this has been sitting too long".
- **Ride the weekly iCal heartbeat.** The feed already fires local alarms on-device and carries a
  liveness VEVENT (`CalendarSubscriptionFeedPlan.md`). "2 completed tasks still have code shipping"
  is one more line in a mechanism that is already proven, with no scheduler and no new endpoint.

## The banner

Home page, top, above the hero — the same slot and shape as the existing read-only banner
(`index.html:312`, `role="alert"`, `readOnly` model attribute set in `GeneralController:62`).

**OWNER-only.** The read-only banner deliberately shows to everyone; this one names admin internals
and pending data work, so it follows `showDataEntryNav` (`GeneralController:64`) instead. Per the
deny-by-default rule, whatever route the banner links to needs a `SecurityConfig` matcher **and** an
`AuthorizationMatrixTest` row in the same change.

Each item states the task and links straight to where it is done — the migration page, or
`/conferences` for the attendance backfill. Sequencing matters for the destructive ones: a migration
item should say **back up first** and link `/admin/backup`, because that is the step that makes it
undoable.

## Open questions

1. **Where does "tick it off" live?** A control in the banner itself, or a small `/admin/tasks` page?
   The banner is fewer clicks; the page has somewhere to show already-completed tasks.
2. **Does a completed-but-still-declared task keep showing, greyed?** Per the affordance rule in
   CLAUDE.md this is a *state* case, so showing it greyed with "code can be removed" would be
   consistent — but it also re-clutters the banner the moment Ted has done the work.
3. **Config checks:** which are worth declaring, and does a missing secret read as a task ("set
   `CALENDAR_FEED_TOKEN`") or as its own warning? It differs from the others in that Ted fixes it in
   Railway, not in the app.
4. **Restart-after-restore:** same banner, or does it belong with the read-only banner it resembles?
5. Whether the hook, the age check, or the heartbeat carries the "code can be removed" nudge —
   see above; the recommendation is to start with the age check (no network, no new endpoint) and
   only add the hook if that proves too quiet.

## Build order (proposed)

1. The registry + `OneOffTaskCompleted` + the OWNER-only banner, with the **outstanding
   `event_log.type` normalization as its first real customer** and the conference backfill as its
   first acknowledged one.
2. Derivable checks (legacy-row count, missing config).
3. The "code can be removed" nudge — age check first.

## Testing

Both tiers as usual: a projector/registry unit test for outstanding-vs-completed, a `@WebMvcTest`
asserting the banner renders for OWNER and **not** for FAMILY or anonymous, an
`AuthorizationMatrixTest` row for any new route, and a golden-sample case for
`OneOffTaskCompleted` per the standing practice for every new event. Mutation-verify each.

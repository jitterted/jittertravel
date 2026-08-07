# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`.

## Open

- [ ] **Add a private social event type** (e.g. a dinner with friends). Today the only social
      entry kind is GATHERING, which is treated as a *public* event — name, venue, city,
      `infoUrl`, and times all render for anonymous viewers on `/calendar` (deliberate:
      gatherings are public events like conferences). A private dinner modelled as a gathering
      would therefore be fully exposed. Needs its own `EntryKind` plus a redacting branch in
      `CalendarEntryRedactor` (anonymous should see, at most, a "Busy"-style block with day
      granularity), tests in both redaction tiers, and a way to mark an event private at entry.
      Important — this is a real leak waiting for the first private event Ted enters.
- [ ] Clean up usage of Mockito, replacing it with better test doubles.
- [ ] Add event-type filtering to `/admin/eventlog` (the command-log filter is already done).
- [ ] `/admin/commandlog`'s "Out of order" badge only detects divergence *within* a page.
      `PostgresPersister.loadTimelinePage` resets `runningMaxSeq` to `Long.MIN_VALUE` on every
      call (`PostgresPersister.java:288`), so a command whose event sequence numbers interleave
      with those of a command on the *previous* page is silently unflagged — the first entry of
      any page can never be marked. Fix means seeding `runningMaxSeq` from the max event
      sequence of all commands before the page's window rather than starting fresh. Pre-existing
      behaviour, untouched by the newest-first paging fix (`PageWindow`), which only changed
      *which* window is fetched, not how it's scanned.

- [ ] **Dry-run validation for an import file.** `/admin/zone-audit` only sweeps `event_log` via
      `LocationAuditProjector` (an `EventStreamConsumer`), so it can only report on data that is
      *already imported* — it cannot pre-check a backup file. That is backwards for the case it
      was meant to protect: on 2026-08-06 a production import failed on three conference venues
      the audit could not have warned about. `CommandImporter.importJson` already runs
      validate-then-apply with pass one writing nothing and collecting *all* errors, so a
      validate-only entry point (plus a page or a textarea button) would list every unresolvable
      location in a file before touching the database. Note the plan docs currently overstate the
      audit as sweeping "`event_log` / `command_log`" — correct that wording too.

## Done

- [x] **Every application service goes through `CommandExecutor`** (2026-08-05, with the conference
      UTC slice). `ConferencePlanning` was the last service injecting `EventStore` directly; it now
      uses `CommandExecutor` like the rest. The enforcement test landed in the same change as
      `ApplicationServicesUseCommandExecutorTest` — plain reflection over `application`-package
      constructors, **no ArchUnit dependency**, with `CommandExecutor` itself excluded as the
      authorized holder. This unblocks the conditional-append work in
      `CommandConsistencyEventStore.md`: no service can now bypass the guard that lives in
      `CommandExecutor`.
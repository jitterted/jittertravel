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

## Done

- [x] **Dry-run validation for an import file** (2026-08-06). `CommandImporter.validateJson` runs
      pass one of the real import — deserialize every entry and recompute its events — and writes
      nothing, returning a `ValidationReport(validCount, errors)`. A "Validate only" button on
      `/admin/import` posts the textarea to `/admin/import/validate` (covered by the existing
      `/admin/**` OWNER matcher, so no `SecurityConfig` change) and re-renders the same page with
      either every problem found or "all N entries would import. Nothing was written." The
      textarea keeps its content so the file can be fixed in place. Written because
      `/admin/zone-audit` reads `event_log` — data that is *already imported* — which is backwards
      for a wipe-then-import workflow; it gave no warning before the 2026-08-06 production import
      failed on three venues. Mutation-verified: making `validateJson` call `apply` fails both
      dry-run tests.

- [x] **Every application service goes through `CommandExecutor`** (2026-08-05, with the conference
      UTC slice). `ConferencePlanning` was the last service injecting `EventStore` directly; it now
      uses `CommandExecutor` like the rest. The enforcement test landed in the same change as
      `ApplicationServicesUseCommandExecutorTest` — plain reflection over `application`-package
      constructors, **no ArchUnit dependency**, with `CommandExecutor` itself excluded as the
      authorized holder. This unblocks the conditional-append work in
      `CommandConsistencyEventStore.md`: no service can now bypass the guard that lives in
      `CommandExecutor`.
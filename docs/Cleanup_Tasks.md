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
- [ ] Migrate the one remaining application service that still injects `EventStore` directly onto
      `CommandExecutor` (`execute`/`appendEvents`): `ConferencePlanning`. (`ChangeFlight` and
      `FlightBooking` have since been migrated; `CommandImporter` moved over with the
      validate-then-apply import work.) **Then** add an ArchUnit test verifying no class in the `application`
      package has a field of type `EventStore` (enforces "use CommandExecutor, never
      EventStore directly"). Home: `src/test/java/.../architecture/`. Hard prerequisite for
      the conditional-append work in `CommandConsistencyEventStore.md` (the consistency guard
      lives in `CommandExecutor`; any service bypassing it bypasses the guard).
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
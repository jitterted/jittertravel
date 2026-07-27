# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`.

## Open

- [ ] Clean up usage of Mockito, replacing it with better test doubles.
- [ ] Migrate the three application services that still inject `EventStore` directly onto
      `CommandExecutor` (`execute`/`appendEvents`): `ChangeFlight`, `ConferencePlanning`,
      `FlightBooking`. **Then** add an ArchUnit test verifying no class in the `application`
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
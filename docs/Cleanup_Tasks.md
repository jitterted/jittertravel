# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`.

## Open

- [ ] Clean up usage of Mockito, replacing it with better test doubles.
- [ ] Add an ArchUnit test verifying no class in the `application` package has a field of
      type `EventStore` (enforces "use CommandExecutor, never EventStore directly").
      Home: `src/test/java/.../architecture/`.
- [ ] Add event-type filtering to `/admin/eventlog` (the command-log filter is already done).

## Done
# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`. For an index of every
plan doc and its status — including these items — see `Backlog.md`.

## Open

- [ ] **Add a private social event type** (e.g. a dinner with friends). Promoted to its own
      plan doc — see `PrivateSocialEventPlan.md`. Modeling decided 2026-08-12 (own entry kind,
      own slice); anonymous view decided 2026-08-07 (`Busy` / time range with zone / city+country).
      Still a real leak until built: today a private dinner can only be modelled as a public
      GATHERING.
- [ ] **Standardize headers/footers for navigation.** Give pages a consistent header/footer with
      shared nav so you can move around the app directly instead of returning to the home page
      every time. Currently many pages are dead-ends that force a trip back to `/`.
- [ ] **Boot-replay preflight (pre-deploy).** A check that replays *every* stored event through the
      real read path — `EventPayloadUpcaster.upcast` → `EventTypes.classFor` → `treeToValue`,
      including zone resolution — and fails if any row cannot upcast/bind, run against a copy of
      production *before* deploying. This is what caught the Morocco/Antwerp zone failures on
      2026-08-16 (a throwaway test over the 69 prod rows); without it they would have been a second
      failed deploy. `/admin/zone-audit` is not a substitute: it only sweeps already-imported
      `event_log` at runtime and went stale (it passed in June yet two unresolvable locations slipped
      in after). Options: a JUnit tier that loads a dump into a scratch DB and asserts a clean
      replay, or a CLI/`--dry-run` boot mode that replays and exits non-zero on any failure. Wire it
      into the deploy step so a bad legacy row blocks the push, not the boot.
- [ ] **Eager-migrate legacy bare-scalar datetime events to `{utc, zone}` (retire read-time zone
      resolution during replay).** Today legacy events (written before zones were captured) store a
      bare wall-clock scalar and are upcast *lazily* — `EventPayloadUpcaster` re-resolves their zone
      from the curated `LocationZoneResolver` on **every boot**, forever. That permanently couples
      replay to the resolver's coverage and makes every legacy data-entry error a permanent resolver
      entry (see the `Europe/Brussels → "antwerp"` city-table hack added 2026-08-16 for a hotel whose
      country field says "Brussels"; it cannot be removed while that original event exists, because
      editing the hotel appends a *new* corrected event but never rewrites the bad original).
      Fix: a one-time migration that reads each legacy bare-scalar event, resolves its zone **once**,
      and rewrites the `event_log` payload to the current `{utc, zone}` shape. Afterwards no
      bare-scalar rows remain, the upcaster's legacy branches become dead (eventually retire them),
      the resolver leaves the replay path entirely, and the per-error special entries (Antwerp) can
      be deleted. Caveat: this mutates stored payloads (a controlled event-shape migration that
      *materializes* the zone already being derived — meaning preserved), and old bare-scalar backup
      files would still need the read-time upcaster on restore, so keep the upcaster until no
      old-shape backup can be restored. May warrant its own plan doc.
- [ ] **Consistent edit affordances on calendar and itinerary entries.** Make it easy to jump from
      a calendar cell or an itinerary entry straight to the edit form for that hotel/flight/train/
      conference/gathering, and do it *uniformly* across all entry kinds. Today it's haphazard —
      some entry types show an edit icon/link and others don't. Every editable entry kind should
      expose the same edit affordance from both surfaces (OWNER view only — never in the redacted
      anonymous calendar). Related to the standardized header/footer nav item above.
- [ ] **Split every page that combines an Edit and a Cancel form into separate pages.** Cancel Hotel
      was already split out (`f5971ef`); apply the same treatment to the remaining entry kinds so no
      page hosts both an edit form and a cancel/delete form together. Keeps each form's errors
      rendering on its own page (see the "errors render on the form page" convention) and avoids one
      form's submit clobbering the other's state.
- [ ] **Calendar day-number popup is broken.** Clicking a day number on `/calendar` opens the
      day-menu popup, but it doesn't dismiss: clicking elsewhere on the page doesn't close it, nor
      does pressing Escape, and every click on the number opens *another* stacked popup instead of
      toggling the existing one. Fix: close on outside-click and on Escape, and make a repeat click
      toggle (or reuse) the single popup rather than spawn duplicates. JS-behavior fix — cover with a
      `@Tag("js")` Playwright test (`page.setContent`, no server) per `docs/JS-Behavior-Tests.md`.
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

- [x] **Startup-failure (read-only) warning banner on the home page** (2026-08-16). When
      `EventStore.isReadOnly()` is true — a failed boot replay or a failed save flipped the app to
      read-only — the home page renders a prominent red `role="alert"` banner across the top
      ("Read-only mode — a startup or save error occurred, so changes are disabled and some data may
      be missing"), shown to **every** viewer (the banner reveals no travel detail, and an anonymous
      visitor seeing a degraded site is honest). `GeneralController` now injects `EventStore` and
      exposes a `readOnly` model flag; the banner sits above the local badge and pending banner in
      `index.html`. Two `@WebMvcTest` cases (shown when read-only, hidden when writable),
      mutation-verified by hardcoding the flag to `false`. `EventStore` mock also fed to the two
      `GeneralController` auth slices (`AuthorizationMatrixTest`, `SecurityAuthorizationTest`).
      Motivated by the 2026-08-16 deploy, where a replay bug dropped the app to read-only with empty
      projections and nothing on the page said so.
- [x] **"Schedule problems" nav card is state-aware** (2026-08-15). `GeneralController` surfaces
      `scheduleProblemCount` (OWNER-only) from `ScheduleGapProjector.problems().size()`; the card in
      `index.html` is amber only when there are problems, a green tint otherwise, and its subtitle
      shows the count ("3 problems" / "1 problem" / "No problems"). Three `@WebMvcTest` cases
      (amber+count, green+none, singular), all mutation-verified. Also fed the new dependency to the
      other `GeneralController` web-slice tests (`AuthorizationMatrixTest`, `SecurityAuthorizationTest`).
- [x] **GET stale-link not-found no longer attaches a dead flash** (2026-08-15). The four edit-page
      GET handlers (`ChangeHotel`/`ChangeFlight`/`ChangeTrain`/`ChangeGathering`) redirected a
      not-found id to their view-only j2html list with a `notFoundMessage` flash the list can't
      render. Dropped the flash (and the now-unused `RedirectAttributes` param/import) so they
      navigate to the list silently. `CancelHotelController` has the same dead-flash pattern but is
      out of scope here and has a test asserting the flash — left as a separate item.
- [x] **`CancelHotelController` dead flash removed** (2026-08-16). The follow-up to the item above.
      All three `notFoundMessage` flashes (GET stale link, POST malformed id, POST already-cancelled)
      redirected to the view-only `/booked-hotels` j2html list, which can't render a flash, so each
      was silently dropped. Removed all three (and the now-unused `RedirectAttributes` param/import
      from both handlers) so they navigate silently. The one test asserting the flash
      (`unknownBookingRedirectsWithAFlashMessage`) was renamed to
      `alreadyGoneBookingRedirectsToListInsteadOfThrowing` and now asserts only the redirect;
      mutation-verified. All 7 controller-slice tests green.
- [x] **Schedule-problem day-boundary anchored to "Anywhere on Earth" instead of UTC** (2026-08-16).
      The two `LocalDate`-only `ScheduleProblem` variants (`MissingHotel`, `DifferentCityConflict`)
      anchored `relevantUntil()` at `ZoneOffset.UTC`, so west of UTC — the owner's whole realistic
      range (SFO home is UTC-7/-8, all US travel) — a problem dropped off FUTURE during the *previous*
      local afternoon: a missing hotel with checkout tomorrow vanished right as the owner arrived for
      the last night. Now both anchor at `ANYWHERE_ON_EARTH` (`ZoneOffset.ofHours(-12)`), keeping a
      day-granularity problem live until its date has passed everywhere the owner could be. The
      boundary only ever moves *later* (12h) than a UTC anchor, never earlier, so the fix is strictly
      surfacing-safe — it cannot hide a problem the old code showed; the only cost is a moot problem
      lingering up to ~12h longer (accepted papercut for a safety-net view). Deep boundary coverage in
      new `ScheduleProblemTest` (exact instants, never-earlier-than-UTC guard, SFO/Hawaii last-night
      surfacing, inclusive-boundary + one-second-past exclusion, east-of-UTC lingering, and guards
      that the two instant-backed variants stay anchored to their endpoint instant). Both mutants
      (AoE→UTC per record) verified; full suite green at 814.
- [x] **Responsive, no-horizontal-scroll treatment on the other list views** (2026-08-15).
      `/tentative-conferences` (`995caab`), `/booked-trains` (`86af549`, `9bb5245`, `bebf6e6`),
      `/booked-flights` (`b6c5f92`, `9bb5245`, `370c33a`, `c67444f`, `1027144`, `46e39e6` superseded)
      and `/planned-gatherings` (`375761e`, `8c558ac`, `0755edd`). Dropped every `.page`/container
      `max-width` cap and the `overflow-x` scroller; date/time uses
      `ZonedTimeTag.renderDateTimeStacking`. The grid views (flights, trains, conferences,
      gatherings) were unified on **one grid + `grid-template-columns: subgrid`** so columns align
      across rows with min-content floors (no drift, no overlap), collapsing to a single stacked
      column with per-column leg labels below 640px. Flights keep the change-history disclosure by
      putting the list inside the `<summary>` (so it spans all columns) and hiding it while closed.
      `/planned-gatherings` was additionally reshaped from cards into a
      When/Speaking/Gathering/Venue/actions table. Each renderer's exact-markup tests updated and
      mutation-verified. Note: the `minmax(fixed, fr)` and plain-`min-width:0` attempts were tried
      and rejected (overlap / drift) — subgrid is the chosen approach.
- [x] **`ClearConflictController` POST now has error handling** (2026-08-15, `6df5f63`).
      `clearConflictSubmit` wraps the parse + `clearConflict(...)` in a `try/catch`: a malformed id
      or generic append failure re-renders the `clear-conflict` form with a global error via
      `bindingResult` (never redirecting the error to the view-only `/schedule-problems`), and
      `ReadOnlyModeException` redirects to `/read-only`. The conflict-summary fields ride the POST as
      hidden inputs so a rejected submit re-renders the summary intact. Three `@WebMvcTest` cases
      (malformed id, service failure, read-only), all mutation-verified — including a hardened
      assertion matching the visible `<strong>` summary markup, since the value also rides a hidden
      input's `value=` and a bare substring check passed even with the visible summary broken.
- [x] **`/booked-hotels` now shows the booking's real `bookingIntent`** (2026-08-15, `6df5f63`).
      `BookedHotelsProjector.put` takes a `BookingIntent` and threads it from both
      `HotelBooked.bookingIntent()` and `HotelChanged.bookingIntent()` into the view instead of
      hardcoding `TENTATIVE`, so a FINAL booking no longer reads "Tentative" next to its own FINAL
      edit form. New projector test books a FINAL hotel and asserts the view status;
      `hotelChangedOverwritesBookingUnderSameId` was extended to assert the change to FINAL lands.
      Both mutation-verified by reinstating the hardcode. Promoted to a rule in
      `EventSourcingRulesHeuristics.md` (**R8**: a projector that derives a field must derive it
      from the events, never ignore relevant data the events carry).
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
      dry-run tests. *(2026-08-11: `CommandImporter` was retired with the event-oriented
      backup/restore rework; this dry run now lives in `BackupService.validateJson`, posted from
      `/admin/restore` to `/admin/restore/validate`, and validates event payloads rather than
      recomputed command events.)*

- [x] **Every application service goes through `CommandExecutor`** (2026-08-05, with the conference
      UTC slice). `ConferencePlanning` was the last service injecting `EventStore` directly; it now
      uses `CommandExecutor` like the rest. The enforcement test landed in the same change as
      `ApplicationServicesUseCommandExecutorTest` — plain reflection over `application`-package
      constructors, **no ArchUnit dependency**, with `CommandExecutor` itself excluded as the
      authorized holder. This unblocks the conditional-append work in
      `CommandConsistencyEventStore.md`: no service can now bypass the guard that lives in
      `CommandExecutor`.
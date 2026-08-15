# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`. For an index of every
plan doc and its status — including these items — see `Backlog.md`.

## Open

- [ ] **GET stale-link not-found drops its flash on a view-only list.** The four edit-page GET
      handlers (`ChangeHotel`/`ChangeFlight`/`ChangeTrain`/`ChangeGathering`) redirect a not-found
      id to their view-only j2html list (`/booked-hotels`, `/booked-flights`, `/booked-trains`,
      `/planned-gatherings`) with a `notFoundMessage` flash those pages can't render, so it's
      silently dropped. Not a form-post error (it's following a stale edit link for something
      already gone), so left as-is 2026-08-13 — but the dead flash is pointless. Either drop the
      flash and navigate to the list silently, or give "this booking no longer exists" a real page.
- [ ] **Give the other list views the same responsive, no-horizontal-scroll treatment as
      `/booked-hotels`.** `booked-flights`, `booked-trains`, `planned-gatherings` and
      `tentative-conferences` renderers still use the old pattern — a `.page` `max-width` cap plus
      `white-space: nowrap` cells — so on a narrow viewport (iPad portrait) their tables can
      overflow and force a horizontal scrollbar. No page may ever scroll horizontally. Apply the
      `BookedHotelsRenderer` fix to each: drop the `.page` max-width (let it fill the centered
      space), remove the blanket cell `nowrap`, and split wide cells into no-break units that stack
      when squeezed — City/Country as two `.nowrap` spans, date/time via
      `ZonedTimeTag.renderDateTimeStacking` (date + time as two `.nowrap` spans in one `<time>`),
      and row actions as inline links that wrap. `.nowrap` is already a shared utility in
      `site.css`. Update each renderer's exact-markup tests and mutation-verify.
- [ ] **Make the "Schedule problems" nav card state-aware** (`index.html`, OWNER Admin group).
      Today the card is always amber (`background: #fef3c7; border-color: #d97706`) with a static
      "Missing travel & hotels" subtitle. Instead: amber **only when there are actual problems**,
      otherwise a light-green tint; and the subtitle should show the problem *count* (e.g.
      "3 problems") or "No problems" when clear. Needs a problem count surfaced to the home model —
      `GeneralController` would read it (same source as `/schedule-problems`) and pass it in, so the
      home page can style/label the card. Keep it OWNER-only; the `/schedule-problems` report stays
      OWNER-gated per the redaction rules.
- [ ] **Add a private social event type** (e.g. a dinner with friends). Promoted to its own
      plan doc — see `PrivateSocialEventPlan.md`. Modeling decided 2026-08-12 (own entry kind,
      own slice); anonymous view decided 2026-08-07 (`Busy` / time range with zone / city+country).
      Still a real leak until built: today a private dinner can only be modelled as a public
      GATHERING.
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
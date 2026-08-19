# Cleanup Tasks & Smaller Fixes

A running list of smaller fixes, cleanups, and tech-debt items that don't warrant a
dedicated planning doc. Add an item when you notice it; check it off (or delete it) when
done. For larger structural refactors, see `Refactoring_Opportunities.md`. For an index of every
plan doc and its status — including these items — see `Backlog.md`.

## Open

- [ ] **Itinerary: add-entry day dropdown** (like the calendar's). The `/calendar` future-day
      disclosure menu lets the owner add an entry for a specific day; the itinerary has no such
      affordance. Add the same per-day "add an entry" dropdown to the itinerary so a day can be
      populated directly from that surface (OWNER-only; reuse the `DAY_MENU` pattern).
- [ ] **Itinerary: show current location (from hotel) when a day has no other events.** So the
      owner can tell *where they are* on a day whose only context is an ongoing hotel stay, surface
      the current location (derived from the active hotel booking) instead of an empty/eventless day.
- [ ] Clean up usage of Mockito, replacing it with better test doubles.
- [ ] Add event-type filtering to `/admin/eventlog` (the command-log filter is already done).
- [ ] `/admin/commandlog`'s "Out of order" badge only detects divergence *within* a page.
      `PostgresPersister.loadTimelinePage` resets `runningMaxSeq` to `Long.MIN_VALUE` on every
      call (`PostgresPersister.java:291`), so a command whose event sequence numbers interleave
      with those of a command on the *previous* page is silently unflagged — the first entry of
      any page can never be marked. Fix means seeding `runningMaxSeq` from the max event
      sequence of all commands before the page's window rather than starting fresh. Pre-existing
      behaviour, untouched by the newest-first paging fix (`PageWindow`), which only changed
      *which* window is fetched, not how it's scanned.

## Done

- [x] **Login "have to sign in twice"** (2026-08-18). The custom sign-in form's CSRF token was
      session-bound (default `HttpSessionCsrfTokenRepository`), so it died whenever the in-memory
      session did — every redeploy, every local devtools restart, every idle timeout. A login page
      rendered before that death then submitted a token with no session to match; the `CsrfFilter`
      rejected it and the custom `accessDeniedHandler` bounced it **silently to `/`**, looking
      exactly like "not logged in". The retry rendered a fresh `/login` with a matching token and
      worked — hence "log in twice, then it sticks". Two changes in `SecurityConfig`: (1) CSRF token
      now lives in an **HttpOnly cookie** (`CookieCsrfTokenRepository`), not the session, so it
      outlives restarts/timeouts and needs no server session — the first login after a restart
      validates (kept HttpOnly: the form gets its token server-side, so JS never reads it, no weaker
      than a session token against XSS); (2) a rejected CSRF token (`CsrfException`) now routes to
      `/login?expired` with a notice instead of silently to `/`, where an expired login is
      indistinguishable from never having signed in — authenticated-but-wrong-role denials still go
      to `/`. `login.html` shows the `?expired` notice. New mutation-verified test
      `SecurityAuthorizationTest.staleCsrfTokenReturnsToLoginWithExpiredNotice`; reproduced and
      verified end-to-end against the running app; full suite green (938 + 36 js). **Single-instance
      only fixes CSRF** — going multi-instance would also need the auth `HttpSession` externalized
      (Spring Session JDBC on the existing Postgres), **deferred until actually scaling**.
- [x] **Lateral nav across the read-only view pages** (2026-08-17). The old "nav" was the same
      two-link `JitterTravel · Calendar` breadcrumb on every page (and inconsistently built — the
      calendar's was an inline-styled indigo link, trains wrapped it in `<h3>`), so from any view
      you could only reach `/` and `/calendar` — every other view page was a lateral dead-end.
      Replaced it with a single shared `Page.viewNav(NavAudience, activePath)`: a flex-**wrapping**
      bar (no horizontal scroll) that links each view page to the others the viewer may reach, with
      the current page rendered as a non-link `<span class="active" aria-current="page">`. Applied
      to all eight j2html view renderers — `/itinerary`, `/calendar`, `/booked-flights`,
      `/booked-trains`, `/booked-hotels`, `/planned-gatherings`, `/tentative-conferences`,
      `/schedule-problems`. **Tier-gated (deny-by-default), matching `SecurityConfig`:** OWNER sees
      all eight; FAMILY sees only Itinerary + Calendar (the pages it can open); anonymous on the
      public calendar sees only the home link — a link to a page the viewer would 403 on is both a
      papercut and a hint the page exists, so it's never rendered. `NavAudience.of(isPublicUser,
      isOwner)` derives the tier from the flags controllers already hold. Base `.view-nav` styling
      in `site.css`; the calendar scopes a `4rem` horizontal margin so the bar aligns with its
      `.calendar-outer` body. New `PageTest` (link sets per tier, active-span, `NavAudience.of`) and
      two new `CalendarRedactionSecurityTest` cases (anonymous exposes no owner/family surface;
      owner links to the other views) — all mutation-verified (anonymous-leak, missing active-span,
      owner-missing-links). **The Schedule Problems nav link is unconditional (OWNER only).** A
      state-aware version was built and then **reverted on 2026-08-18**: making the link appear only
      when `ScheduleGapProjector.problems(now)` was non-empty meant threading a
      `hasScheduleProblems` flag through `Page.viewNav` and all eight view renderers *and* injecting
      `ScheduleGapProjector` into seven view controllers that otherwise have no interest in it. Ted's
      call: that's a large increase in coupling, test setup, and constructor noise for a small UX
      gain, so the bar now reflects only the viewer's tier and the report page renders its own empty
      state when the schedule is clean. The **home card** on `index.html` stays state-aware —
      `GeneralController` legitimately depends on the projector for its count (see the entry below).
      Full suite green at 936. **Deliberately scoped this session (Ted's
      call):** *view pages only* — the Thymeleaf **form** templates keep their existing breadcrumb
      (nav matters less there, and forms are reached from the lists / the calendar's add-entry
      dropdown), and **no footer** (the calendar dropdown covers adding entries). Admin pages left
      out too. This retires the "many pages are dead-ends" cleanup item as far as the view surfaces
      are concerned.
- [x] **Consistent edit affordances on calendar and itinerary entries** (2026-08-17). Every editable
      entry kind now exposes the same OWNER-only edit pencil from **both** surfaces. Before this,
      the calendar showed a pencil only for flights and trains (`editPath` set), and the itinerary
      showed one for flights/trains/hotels but not gatherings. Filled the two gaps: `HotelCalendarProjector`
      and `GatheringCalendarProjector` now set `editPath` (`/booked-hotels/{id}`,
      `/planned-gatherings/{id}`), so the calendar renders the pencil for hotels and gatherings too;
      `ItineraryRenderer.renderGathering` now takes `isOwner` and appends the pencil (needed a new
      `gatheringId` field on `GatheringItineraryEntry`, threaded from `ItineraryProjector`). The
      pencil is OWNER-only on both surfaces — the calendar redactor drops `editPath` and the
      renderers gate on `isOwner`; a new `CalendarRedactionSecurityTest` case plus the strengthened
      lodging redactor test prove the hotel deep link never reaches anonymous eyes (full-stack
      mutation-verified). **Conference and private event are intentionally excluded** — they have no
      edit flow yet (`ChangeConference` / `ChangePrivateEvent` are separate unbuilt features), so
      there is nothing to link to; when each edit page ships it should set `editPath` / take `isOwner`
      the same way and inherit the affordance. Projector + renderer + both-tier redaction tests, all
      mutation-verified.
- [x] **Split every page combining an Edit and a Cancel form** (2026-08-17). No page hosts a
      second cancel *form* — the only entry cancel form, `cancel-hotel.html`, has been its own page
      since `f5971ef`, and every other edit page hosts a single edit form (`change-flight.html`'s
      second form is the flight-number **lookup**, not a cancel; `change-train.html` /
      `change-gathering.html` have one form each; there is no `change-private-event` page). The
      other entry kinds (flight, train, gathering, conference, private event) have **no cancel
      action at all** yet — separate, still-open features (`ConferenceCancelled` organizer cancel;
      gathering cancellation, out of scope in `ChangeGatheringPlan.md`) — and when each ships it
      must land on its own page from the start, per the "errors render on the form page" convention.
      **Correction (same day):** the first pass called this done on the form-vs-link technicality
      and left the "Cancel this booking" **section** (a `.danger-zone` heading + hint + link to the
      cancel page) sitting on the `change-hotel` edit page — which still reads as edit-and-cancel
      combined. Removed that whole section (and its now-dead `.danger-zone`/`.danger-link` CSS), so
      the change page is genuinely edit-only; cancel is reached from the per-row **Cancel** link on
      `/booked-hotels` (which already exists next to Edit). `ChangeHotelControllerTest`'s GET render
      test now asserts the page `doesNotContain` "Cancel this booking" or "/cancel" (mutation-verified),
      and the explanatory note on the template is a Thymeleaf parser comment so it isn't rendered.
- [x] **Calendar day-number popup now dismisses** (2026-08-17). The owner future-day disclosure
      menu on `/calendar` is a native `<details class="day-menu">`, which on its own never
      dismisses — clicking away left it open, Escape did nothing, and opening a second day left the
      first open so the absolutely-positioned menus stacked and overlapped. Added `DAY_MENU_SCRIPT`
      in `ConfirmedCalendarRenderer` giving the three behaviors a popup is expected to have: only
      one open at a time (a `toggle` listener closes the others when one opens), close on
      outside-click (document `click` where the target isn't inside a `.day-menu`), and close on
      Escape (document `keydown`). Harmless when no day menus are present (owner-only render).
      Covered by `ConfirmedCalendarDayMenuJsTest` (`@Tag("js")`, `page.setContent`, no server) —
      three cases (outside-click, Escape, no-stacking), each mutation-verified. **Note:** while
      doing this I found `ConfirmedCalendarToggleJsTest` was **pre-existing broken** (failed without
      any of my changes) — the 2026-08-16 "default `from` = one week before today" change
      (`0435623`) shrank the rendered range so the tests' expected collapsed-week counts no longer
      held; the `js` tier is opt-in (excluded from the default build), so it shipped invisibly.
      **Fixed 2026-08-17** (green, 5/5), and the native pre-push MUST-PASS gate now runs the `js`
      tier too so a broken js test can no longer ship unseen.
- [x] **Add a private social event type** (2026-08-13). Shipped as its own entry kind — see
      `PrivateSocialEventPlan.md` (done) and the Backlog row. `EntryKind.PRIVATE_EVENT` with a
      `PlanPrivateEvent` command / `PrivateEventPlanned` event / context, `PrivateEventCalendarProjector`,
      a plan form + controller + nav card, and a `PrivateEventItineraryEntry` on `/itinerary`. The
      redactor gets its own `PRIVATE_EVENT` branch: an anonymous viewer sees `Busy`, a zone-labelled
      time range, and city+country — never the title — via the plain-text `SubtitleLine.FixedRange`
      (no `<time datetime>` leak). Both tiers of redaction test plus command/projector/controller/golden.
      Retires the "private dinner can only be a public GATHERING" leak. Follow-ups still open (edit
      flow, `/planned-private-events` list) live in the plan doc.
- [x] **Eager-migrate legacy events + per-event schema-version stamp** (2026-08-16). Owning doc
      `LegacyEventEagerMigrationPlan.md` (now `built`). Added an `event_log.schema_version` column
      (nullable; per-type version in `EventTypes`, the nine `ZonedTimestamp` types = 2, others = 1);
      the append path stamps new rows, restore carries it verbatim, backup format bumped to **v3**
      (restores v2 and v3, so old backups aren't orphaned). `LegacyEventMigration` +
      `PostgresPersister.migrateEventPayloads` rewrite each stale row's payload and stamp in one
      transaction (identity columns untouched); idempotent (already-current rows skipped),
      validate-then-apply (one unbindable/unresolvable row aborts with zero writes), read-only-guarded.
      OWNER-only `/admin/migrate-legacy-events` (GET preview + POST run) with an `AuthorizationMatrixTest`
      row and an admin nav card. Decided with Ted: **column not payload-key**, accept the backup bump,
      in-place admin UPDATE, FQCN→logical `type` normalization **deferred**, versioning *framework*
      deferred (stamp only for now). Every new/changed test mutation-verified. Retirements still gated
      on old backups leaving rotation: the upcaster's legacy timezone rungs (the `*TimeZoneUpcaster`
      classes — see `EventPayloadUpcasterDesign.md`), the FQCN mapping, and the Antwerp-style resolver
      hacks.
- [x] **Boot-replay preflight (pre-deploy)** (2026-08-16). `BootReplayPreflightTest`, a
      `@Tag("replay-preflight")` tier excluded from the default build (`mvn test -Preplay-preflight
      -Dpreflight.dump=/path/to/backup.json`; no dump ⇒ skips). Restores a production backup into a
      scratch Testcontainer DB — whose validate pass runs the exact `upcast → classFor → bind` boot
      uses, incl. zone resolution — then drives `loadAllEvents()` over the loaded rows, failing with
      the offending row named on anything that would abort boot. Verified end-to-end against a clean
      dump (passes) and a bad one (fails, naming the unresolvable row) — the 2026-08-16 Morocco/Antwerp
      failure mode. This is the tool that certifies each retirement the eager migration unlocks;
      `/admin/zone-audit` is not a substitute (runtime-only, went stale).
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
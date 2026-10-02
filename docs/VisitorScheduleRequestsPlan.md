# Visitor Schedule Requests — Plan

## Context

Ted wants people he trusts to be able to ask him to a gathering or private event straight from the
public `/calendar`: they tap a free day, fill in a short form, and it arrives in an OWNER inbox where
Ted accepts (turning it into a real planned gathering/private event) or declines. It is the first
thing in the app a non-owner can **write**, so it is gated by per-person invites, never open to the
public. On landing, the plan doc is copied to `docs/VisitorScheduleRequestsPlan.md` and indexed in
`docs/Backlog.md`.

Affordance preview (option B chosen): https://claude.ai/artifact/P1qdpXopS35jNj8Nnsv3Dm

## Decisions (Ted, 2026-09-28)

| # | Question | Decision |
|---|---|---|
| 1 | Storage | Event log + OWNER inbox; **only invite holders may submit** |
| 2 | Invite shape | Per-person, minted in-app, labelled; `InviteIssued` / `InviteRevoked` |
| 3 | Lifetime | Until revoked; unlimited requests |
| 4 | Carrying | `/calendar?invite=X` → HttpOnly cookie → 302 to clean `/calendar` |
| 5 | Maybe conference | Busy |
| 6 | Date shape | 1–3 date options + one shared time range. **Deferred:** multi-day private workshop (new entry kind) |
| 7 | Location | City + country required; zone derived, `CommonZone` picker fallback |
| 8 | Notify | App emails Ted on arrival (Reply-To = visitor); blocked on FamilyEmail slice 1 |
| 9 | Accept | Pick a date → prefilled `/plan-gathering` or `/plan-private-event` `?request=<id>`; plan + accept in one command. Both kinds offered, requested first |
| 10 | Availability | UI guide only; write path checks invite + future dates; inbox shows "busy now" live |
| 11 | Visitor view | Confirmation page only |
| 12 | PII | Name/email/note in the event, accepted as permanent |
| 13 | Decline | `ScheduleRequestDeclined`, no reason; all-dates-past ⇒ "Lapsed" (derived) |
| 14 | Affordance | "Request" chip per free day, in a dedicated row under the day labels (only in weeks that have one) |
| 15 | Token | Plaintext in `InviteIssued`; `/invites` re-shows link + Copy |
| 16 | Date drift | Planning on a non-option date still accepts; event records the planned date; inbox flags it |

**Busy** = any entry of kind conference (Going or Maybe), gathering, private event, flight, train,
ground transfer. **Available** = no entries or lodging only. Today and the past are never requestable.

## Slices (each ships alone; order matters)

### S1 — Invites (OWNER)
- Domain: `InviteId` (copy `GatheringId`); events `InviteIssued(inviteId, label, token, issuedAt)`,
  `InviteRevoked(inviteId, revokedAt)`; `IssueInviteCommand` (blank label refused);
  `RevokeInviteCommand` + context folded from `eventsForDecision()` — copy
  `application/CancelPrivateEvent.contextFor`, **not** `ChangeGathering` (reads a projector, breaks R1).
- Application: `InviteIssuing`, `InviteRevocation`, `InvitesProjector` (`views()`, `activeByToken(token)`).
- Web: `InvitesController` GET/POST `/invites`, POST `/invites/{id}/revoke`. UUID + token
  (`SecureRandom`, 32 bytes, base64url) minted in the controller (domain has no randomness).
  `InvitesRenderer` with `ListToolbar`; link base from `jittertravel.base-url`. Revoke = red, no typed word.
- `EventTypes` registration; `SecurityConfig` `/invites/**` OWNER; `AuthorizationMatrixTest` row.
- Tests: golden samples ×2, command tests, `InvitesProjectorTest`, `@WebMvcTest` for the label error.

### S2 — Invite cookie, request form, event, read-only inbox
- `web/InviteCookie.resolve(req, resp) → Optional<ActiveInvite>`: HttpOnly, SameSite=Lax, Path=/,
  90d, `Secure = request.isSecure()` (per the remember-me note in `SecurityConfig`); clears a cookie
  whose invite is revoked/unknown.
- `CalendarController`: any `?invite=` → always 302 `/calendar` (cookie only when valid — the
  response never reveals validity). Add `@MockitoBean InvitesProjector` to every
  `@WebMvcTest(CalendarController)` incl. `CalendarRedactionSecurityTest`.
- Domain: `ScheduleRequestId`, `RequestedKind {PUBLIC_GATHERING, PRIVATE_EVENT}`,
  `ScheduleRequestReceived(requestId, inviteId, kind, dates, startTime, endTime, zone, city, country,
  name, email, note, receivedAt)`. `SubmitScheduleRequestCommand` + `ScheduleRequestContext(now,
  inviteActive)` (folded from the stream). Reports **all** problems together via
  `InvalidScheduleRequest(List<ScheduleRequestProblem>)` (shape of `InvalidEnteredLocation`): each
  date strictly after today in the request's zone, no duplicate dates, end after start, email shape,
  non-blank fields, length caps on **every** free-text field. Unresolved zone ⇒ zone problem and
  date checks skipped (per-end ordering rule). Inactive invite ⇒ `InviteNotActive`.
- Application: `ScheduleRequestSubmission` + handler reusing `VenueZone` / `LocationZoneResolver`.
- Web: `ScheduleRequestController` GET/POST `/request`, GET `/request/sent`; **404 without an
  active invite** (like `CalendarFeedController`). `requestId` minted on POST, never from the form.
  PRG with flash summary. `ScheduleRequestForm` (`@OptionalEntry` on date2/date3),
  `ScheduleRequestFormErrors extends FormErrors`, `request.html` with a `<span class="error">` per
  input, count banner, no `required`. Kind radio wording says a public gathering appears on Ted's
  public calendar.
- CSRF: in `SecurityConfig`'s access-denied handler, a `CsrfException` on `/request` → `/request?expired`
  ("Form expired, please send again") instead of `/login?expired`. CSRF stays on.
- Inbox (read-only): `ScheduleRequestsProjector` (events alone: invite labels + Received/Accepted/
  Declined), `views(now)` derives Lapsed. `ScheduleRequestsController` composes "busy now" per date
  a layer up from `CalendarAggregator.allEntries()` via `DayAvailability` (ship it here if S3 is later).
  `ScheduleRequestsRenderer` shows invite label, `mailto:` email, note, options.
- Home: `GeneralController` `visitorRequestCount` (OWNER); amber banner in `index.html` copied from
  `.pending-banner`; nav cards for `/schedule-requests` and `/invites`. `AuthorizationMatrixTest`
  (it's `@WebMvcTest(GeneralController)`) needs the new `@MockitoBean`.
- Security: `/schedule-requests/**` OWNER; matrix rows for it and for `/request` (permitAll, 404 by controller).
- Tests: command test (today/tomorrow boundary in a far-east zone; all problems at once); plain
  parameterized controller test problem→field; one `@WebMvcTest` per input asserting the whole span;
  404 without cookie / with revoked cookie (cookie cleared); CSRF → `/request?expired`; calendar
  redirect + cookie attributes; golden sample; `PublicCalendarProjectorTest` adds nothing for the
  new events; add `request.html` to `NoBrowserRequiredOnServerValidatedFormsTest`.

### S3 — Request chips on `/calendar`
- `application/DayAvailability` (plain class, not a projector): `isBusy(entries, date)` with an
  **exhaustive switch over `EntryKind`** (LODGING available, all else busy — a new kind forces a
  decision); spans start..end inclusive like `CalendarViewBuilder.renderWeek`'s day counts;
  `requestableDays(entries, today, horizonEnd)`.
- Extract the window arithmetic from `CalendarRenderer.render` into a shared `CalendarWindow` so the
  controller's horizon matches the grid.
- Controller: non-empty `requestableDays` only when `isPublicUser` **and** an active invite; computed
  from `publicCalendarProjector.entries()`.
- Renderer: thread `requestableDays` through `CalendarRenderer.render` → `CalendarViewBuilder.render`
  → `renderWeek` (fold with `awayDays` into a `CalendarOverlays` record rather than a 9th positional
  param). Weeks intersecting the set get a chip row at grid-row 2; lane rows shift by one (both
  `2 + subRow` and `2 + kindOffset`, plus `grid-template-rows`). Chip = always-visible link
  "Request" → `/request?date=…`; chip CSS emitted only when chips are present.
- Tests: `DayAvailabilityTest` (every kind, lodging-only day); `CalendarViewBuilderTest` (row only in
  weeks with a set day, lanes shifted, draws what it's handed); `CalendarRendererTest`;
  `CalendarRedactionSecurityTest` through the real `PublicCalendarProjector`: no cookie ⇒ no
  `href="/request` and no chip CSS; valid invite ⇒ chips on free days, none on a `PrivateEventPlanned`
  day, and body `doesNotContain` private title/venue, invite label, or token; owner/family ⇒ no chips.

### S4 — Accept / Decline
- Domain: wrapper commands `AcceptRequestAsGatheringCommand(requestId, PlanGatheringCommand)` and
  `AcceptRequestAsPrivateEventCommand(requestId, PlanPrivateEventCommand)` — existing plan commands
  untouched. Context `ScheduleRequestAcceptanceContext(now, status)` folded from the stream; refuse
  unless PENDING (`ScheduleRequestNotPending`); emit the plan's events + one
  `ScheduleRequestAccepted(requestId, acceptedAs, plannedId, plannedDate, acceptedAt)` in one
  `execute` (atomic). No check that the date is among the options (decision 16). Command id = planned id.
  `DeclineScheduleRequestCommand` → `ScheduleRequestDeclined(requestId, declinedAt)`.
- Application: `planGatheringForRequest(...)` on `GatheringPlanning`, same on `PrivateEventPlanning`.
- Web: inbox links `/plan-gathering?date&startTime&endTime&city&country&zone&request=` (query prefill
  as in `ProblemFix`); add those `@RequestParam`s to both GET handlers; POST carries `request` via
  `th:action`. `ScheduleRequestNotPending` = uncounted global error. `ScheduleRequestContextAdvice`
  mirroring `ProblemContextAdvice`, **scoped `assignableTypes = {PlanGatheringController,
  PlanPrivateEventController}`** (it prints visitor PII) + `fragments/schedule-request-context.html`
  in both plan templates. Decline only on pending rows (state-machine rule). Inbox flags an accepted
  request whose planned date wasn't an option.
- Tests: wrapper command tests (pending/accepted/declined/unknown; plan refusals propagate);
  controller table rows; banner `@WebMvcTest`; spy executor asserting both events in one `execute`;
  golden samples.

### S5 — Email Ted (blocked on FamilyEmailNotificationsPlan slice 1)
- Needs `ExternalAction`, `CommandExecutor.executeExternalAction`, `BrevoEmailClient` (none exist yet;
  only slice 0's `EventReactor` / `subscribeAsync` has shipped).
- `infrastructure/ScheduleRequestNotificationTranslator implements EventReactor`, filtering to
  `ScheduleRequestReceived` only (its own append re-enters dispatch); registered via `subscribeAsync`.
- `application/NotifyOwnerOfScheduleRequest`: blank address ⇒ no-op (WARN at boot); skip if
  `OwnerNotifiedOfScheduleRequest(requestId)` already in stream; else `executeExternalAction` →
  send (to Ted, Reply-To visitor) → `OwnerNotifiedOfScheduleRequest(requestId, notifiedAt)`.
  Restore-safe by that plan's §3 (reactors never replay; restore bypasses `append`).
- `SCHEDULE_REQUEST_NOTIFY_EMAIL` (empty default = off): `DEPLOYMENT.md` row + `Pre-Push-Tasks.md` box.
- Tests: translator with `Runnable::run`; notifier with a spy executor; `BrevoEmailClientTest` for
  `replyTo` JSON; golden sample.

## Risks to keep in view
- Visitor text is permanent in the log and every backup; revoking an invite stops only future writes.
- The invite is the only rate limit on anonymous writes — a forwarded link is handled by revoking.
- `CalendarController`'s new dependency ripples into every `@WebMvcTest` slice of it.

## Verification
- Per slice: `./mvnw test` and `./mvnw test -Pjs-tests` (pre-push gate); mutation-verify each new
  test by breaking prod and watching it fail; run IDEA "All Tests".
- End-to-end (run-jittertravel skill): as owner mint an invite on `/invites`; in an incognito
  window open the link → confirm redirect to clean `/calendar`, chips only on free/hotel-only future
  days; submit a request with a deliberately bad date + blank email → both errors at once; submit
  valid → confirmation; as owner see the amber banner, inbox row, "busy now" markers; accept one
  date as a gathering → prefilled form with banner → planned gathering + request shows Accepted;
  revoke the invite → incognito calendar loses chips, `/request` 404s. Check at 820px width in
  headless WebKit for no horizontal scroll with the chip row.

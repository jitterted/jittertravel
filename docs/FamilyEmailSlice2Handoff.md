# Family email, slice 2 (conferences) — hand-off

> **Superseded 2026-10-05: slice 2 is built.** What was built, and where it differs from this file,
> is "Slice 2 as built" in `FamilyEmailNotificationsPlan.md`. Kept as the record of the decisions
> and of the rules of engagement; the "Nothing for conferences is built" line below is no longer true.

Written 2026-10-05 at the end of the session that shipped slice 1 (flights and itineraries), the
itinerary cancel, the email-template files, the email preview page and the Settings test button. Read
this first, then `FamilyEmailNotificationsPlan.md` §4.4, §4.5, §5 and §7 for the reasoning. This file
says what exists, what to build, and what must be asked.

## Where things stand

- **Slice 1 is live.** Pushed as of `a1ad0a9`. The kill switch (`FAMILY_NOTIFY_ENABLED`) is Ted's to
  flip; check `/admin/settings` for whether it is on before assuming conference emails would go out.
- **Nothing for conferences is built.** `FamilyNotificationTriggerCompletenessTest` lists the five
  conference trigger events under `SILENT` with a reason, and `ConferencePlanned` and
  `ConferenceDatesChanged` also `SILENT`. Slice 2 moves the five triggers to `TELLS_FAMILY`.
- **Everything is pushed except this file.** Do not commit the IDE run config
  `.run/JitterTravelApplication (prod-preview).run.xml` (it has held a secret).

## What slice 2 is

Tell family when Ted is going to a conference, and when a conference they were told about is off.
The design is settled (Ted, 2026-09-09/10/15); do not reopen it. The decisions, in one place:

| Decision | Settled |
|---|---|
| Triggers | The five events that move `AttendanceCommitment`: `ConferenceAttendanceConfirmed`, `ConferenceAttendanceDeclined`, `ConferenceCancelled`, `TalkAccepted`, `TalkRejected`. They ship **together** (plan §9.1), never split |
| Not triggers | `TalkSubmitted`, `TalkWithdrawn`, `InvitedToSpeak`, `ConferencePlanned`, `ConferenceDatesChanged`. A speaking-axis change while still attending is **silent** (a stale "his talk was accepted" is accepted) |
| Fact comparison | Send only when the **latest** `FamilyNotified` for the subject has a different `NotifiedFact` than the one the trigger now produces |
| Positive-first | An exit (`CONFERENCE_NOT_GOING`) is sent **only where `CONFERENCE_GOING` went first**. Write it as its own named predicate, not a condition inside the comparison. Same rule `NEVER_TOLD` already implements for itinerary cancels |
| What family believe | Fold the conference's events: `ConferencePlanned` (name, dates, venue, `infoUrl`), `ConferenceProgress` (commitment, speaking), and `ConferenceCancelled` **separately** (it hard-removes, it is not a `ConferenceProgress` transition). Gone or `NOT_GOING` -> `CONFERENCE_NOT_GOING`; `GOING` -> `CONFERENCE_GOING`; `WATCHING` -> nothing, ever |
| Content | `AttendanceBasis` may be included (family already know his working life). A decline or cancellation `reason` **never** reaches family. The rejection is said plainly: "His talk was rejected, so he is not going." |
| Not about | Private events and the CFP pipeline are not conferences family hear about unless he is going. A conference never confirmed and then rejected stays silent |

## What to build, against the code as it is

Slice 1's shape is the pattern. Use it, do not invent a second one.

1. **Domain.** `NotifiedFact`: add `CONFERENCE_GOING`, `CONFERENCE_NOT_GOING`. `NotifiedSubject.Kind`:
   add `CONFERENCE` with a `conference(ConferenceId)` factory. `NotifiedSubject` is a record
   `(Kind, UUID)`, **not** the sealed interface plan §4.4 draws (Jackson/`DomainIsPureTest`). Both
   enums are additive: no schema migration, backup stays v3. Add a `GoldenEventDeserializationTest`
   case for a conference `FamilyNotified` (CLAUDE.md: every new shape gets one).
2. **Application.** `NotifyFamily` already folds `history()` for the last `FamilyNotified`
   (`lastToldAbout`) and takes a clock-free `now`. Add a conference entry point beside
   `notifyOfCancelledItinerary`. It folds the conference's events, decides the fact, applies the
   fact-comparison and positive-first rules, then sends. Add outcomes only if a new silent reason
   is distinct from the existing `Outcome` values (read `NotifyFamily.Outcome` first).
   `ConferenceProgress` lives in `application`; reuse it, do not re-derive the commitment.
3. **Infrastructure.** `FamilyNotificationTranslator`: the five triggers produce a conference
   notification (a `TalkAccepted` and a `ConferenceAttendanceConfirmed` can both occur; the
   fact-comparison makes the second silent). `FamilyNotificationMessages.messageFor` is an
   exhaustive switch over `NotifiedFact`, so the compiler will force the two new arms. Each arm
   chooses a template file in `src/main/resources/email/` (new files, e.g. `conference-going.txt`,
   `conference-not-going.txt`). The "not going" sentence is chosen from the **triggering event**
   (decline, cancellation, rejection are three different sentences, plan §5), so the arm needs
   that as an input.
4. **Completeness test.** Move the five events from `SILENT` to `TELLS_FAMILY` in
   `FamilyNotificationTriggerCompletenessTest`; `ConferencePlanned` and `ConferenceDatesChanged`
   stay `SILENT` with a reason.
5. **Preview page.** `/admin/email-preview` shows every email from `EmailPreviewSamples` and sends
   the three flight emails to Ted. New templates need sample conferences there. **Ask Ted** whether
   "Send all three" becomes "all N" or stays flight-only (plan §5 and his rule below: he reviews
   wording by sending himself the real thing).
6. **Docs.** Update the `FamilyEmailNotificationsPlan.md` "Slice 1 as built" block with a "Slice 2 as
   built" block, the Backlog row (`partial` -> what is left), and CLAUDE.md's email-text section if
   the template list changes. A `Pre-Push-Tasks.md` box is needed only if a new environment
   variable is introduced, and none should be.

## Rules of engagement that bit this session

- **Ted approves the exact email wording before it is committed.** Show him the literal subject and
  body, per fact and per exit variant, and wait. The plan's samples in §5 are drafts, not approvals.
  Put the new text in the template files so he can open and edit them himself.
- **Ask multiple questions with the interactive question tool**, each with a worked example (a real
  conference, real dates). Mockups are required for non-trivial UI; this slice has no new UI beyond
  the preview page's rows, so a mockup is needed only if that page's layout changes.
- **Mutation-verify every new test** (mutate production, watch red, revert) before the commit; run
  default PIT before the commit and `-Ppit-spring` narrowed before the push. The fact-comparison and
  positive-first rules are exactly the kind that tests pass without pinning: a fixture of one
  proves nothing about which one, so pin accept -> reject -> ticket-confirm (three sends) and
  confirm -> re-confirm (one send).
- **Never shell-edit files** (a hook blocks it); use Edit/Write.
- **A reactor is never replayed into** and the executor is single-thread; do not add a second
  thread or send from boot. Read `NotifyFamily.blocked()` before changing the send path.
- **Redaction does not apply to family emails the way it does to `/calendar`**, but the owner
  bound does: nothing the OWNER dashboard would not show. Do not render the email from a shared
  view record (plan §5, item 6).
- **Before the push the gate runs both test suites** (`./mvnw test`, and the JS tier). The
  isolated-test rules in CLAUDE.md apply: no `@DirtiesContext`.

## Access rule that changed this session

Claude's user settings (`~/.claude/settings.json`) now deny reading `~/.config/spring-boot`,
`~/.ssh`, `~/.aws` and `.env` files, because the first holds Ted's real Brevo key and overrides
environment variables in a dev run. Any run that could send must use a fake home
(`.claude/skills/run-jittertravel/driver.sh start` already does). Do not click a send button on a
local instance without checking `/admin/settings` for the key's last four characters.

## First steps for the next session

1. Read this file, plan §4.4/§4.5/§5/§7, `NotifyFamily.java`, `FamilyNotificationTranslator.java`,
   `FamilyNotificationMessages.java`, `ConferenceProgress.java` and the five trigger events.
2. Draft the conference email wording (going: three basis variants plus speaking line; not going:
   decline, cancellation, rejection) and show Ted the literal text for approval before any code.
3. Then the tests, then the code, smallest slice first: the positive-first predicate and the
   fact-comparison against a recording `CommandExecutor` (a spy), then the translator, then the
   templates and the preview page.

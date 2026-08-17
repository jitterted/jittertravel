# Conference Submission Tracking Plan — commitment, speaking status, and the CFP pipeline

> **Status: OPEN (designed 2026-08-12). Nothing built.** Design agreed with Ted in conversation;
> implementation not started. See `docs/Backlog.md` for the status of everything else.

## Problem

Every conference in the app is a `ConferenceTentativelyPlanned`, and "tentative" is now carrying
four unrelated meanings at once:

1. accepted to speak, and going;
2. submitted a talk, waiting to hear;
3. want to go but undecided — either the CFP has not opened yet (hold the slot), or not submitting
   and no ticket bought yet;
4. ticket bought as a pure attendee, not speaking.

They all render identically on `/calendar` and in `/planned-conferences`, so the list cannot answer
"what am I actually committed to?" — which is the question that drives flights, hotels, and the
Schengen count.

## Design

### These are two dimensions, not one list

The four cases above are a cross-product of two independent questions, which is why a flat enum
kept feeling wrong:

| Case | Attendance commitment | Speaking status |
|---|---|---|
| Accepted, attending | `GOING` | `ACCEPTED` |
| Submitted, waiting | `WATCHING` | `SUBMITTED` |
| CFP not open yet | `WATCHING` | `CFP_PENDING` |
| Not submitting, no ticket | `WATCHING` | `NOT_SPEAKING` |
| Ticket bought, attendee | `GOING` | `NOT_SPEAKING` |

Modelling the two separately makes the combinations Ted had not enumerated fall out without new
code — most usefully **rejected-but-undecided** (`WATCHING` + `REJECTED`), where the calendar slot
must turn into a visible "decide: still go as an attendee, or drop it?" rather than silently
vanishing.

**Attendance** is the axis everything downstream cares about:

```
WATCHING  ──commit──▶  GOING
    │                    │
    └──── decline ───▶ NOT_GOING ◀── decline ────┘
```

**Speaking** is the axis this feature adds, and it is a pipeline per *submission*, not per
conference — one conference can take three talk proposals and accept one:

```
CFP_PENDING → CFP_OPEN → SUBMITTED → ACCEPTED / WAITLISTED / REJECTED
                             └──── WITHDRAWN
INVITED (no CFP, organizers asked)
```

### The public/private line

**Decision (Ted, 2026-08-12): commitment is public, status is private.**

Speculative conferences **do** render for anonymous viewers. That is the point of the public
calendar: it invites "if you go to Speculative Conference, I'll see you there" and "I run a user
group near there — if you end up going, let's schedule a gathering." Hiding a maybe kills the
message that makes the maybe worth having.

**Public for a conference at any commitment level:** name, venue, city, dates, `infoUrl`, and the
commitment level itself — rendered as **one collapsed label**. Every speculative state maps to the
*same* public label ("tentative"), so CFP-pending, not-speaking, submitted-and-waiting, and
rejected-but-undecided are publicly indistinguishable. That collapse is what makes commitment
publishable without leaking status.

**OWNER-only, never in a `CalendarEntry`:**

- the entire submission pipeline — talk titles, submitted / accepted / waitlisted / rejected /
  withdrawn, and every decision date;
- CFP window dates;
- any free-text `reason` / `note` / `basis` field on the commitment events;
- the commitment *basis* — whether `GOING` is because a talk was accepted or a ticket was bought.

That third item is the easy leak: `ConferenceAttendanceDeclined(reason: "rejected")` publishes
exactly the fact being protected the moment any renderer prints a reason. **Reasons and bases must
not reach `CalendarEntry` at all** rather than relying on the redactor to strip them — per CLAUDE.md
redaction rule 1, a field that never enters the view cannot leak from it.

**Residual inference, accepted knowingly:** someone watching `/calendar` over time can guess at a
rejection when a tentative entry disappears a week after a known notification date. Nothing to be
done, and cheap next to the value of a public "maybe."

### Events

Two streams. Commitment events key on the existing `ConferenceId`; submissions get their own
`SubmissionId` because a conference has many.

**Conference stream:**

| Event | Notes |
|---|---|
| `ConferenceTentativelyPlanned` (exists) | Unchanged, name kept for backup compatibility. Reads as "on my radar" — the `WATCHING` entry point. |
| `CfpWindowRecorded(conferenceId, opensOn, closesOn)` | Optional; either date may be absent. Drives `CFP_PENDING` → `CFP_OPEN` and the "submit before you lose it" nudge. |
| `ConferenceAttendanceCommitted(conferenceId, basis, committedOn)` | `basis` ∈ `SPEAKING_ACCEPTED`, `SPEAKING_INVITED`, `TICKET_PURCHASED`, `ATTENDING_ANYWAY`. **Owner-only.** |
| `ConferenceAttendanceDeclined(conferenceId, reason, declinedOn)` | *Ted* decided not to go. Distinct from a rejection and from an organizer cancellation. |
| `ConferenceCancelled` (exists) | Organizers cancelled. |

**Submission stream:**

| Event | Notes |
|---|---|
| `TalkSubmitted(submissionId, conferenceId, talkTitle, submittedOn)` | |
| `SubmissionAccepted(submissionId, decidedOn)` | |
| `SubmissionWaitlisted(submissionId, decidedOn)` | Real for several CFPs; cheap to add now. |
| `SubmissionRejected(submissionId, decidedOn)` | |
| `SubmissionWithdrawn(submissionId, reason, withdrawnOn)` | Ted pulled it — schedule clash, better offer the same week. |
| `SpeakingInvitationReceived(conferenceId, invitedOn)` | No CFP; organizers asked. Conference-keyed, since there is no submission. |

**Why explicit events rather than a status field or one `ConferenceStatusChanged`:** a status field
on `ConferenceTentativelyPlanned` would mean rewriting the very thing that changes after the fact,
and would break existing backup files. A single `ConferenceStatusChanged(status, note)` would lump
genuinely different facts ("organizers rejected me" vs. "I changed my mind"), give talk titles
nowhere to live, and could not represent three submissions to one conference. Explicit events also
cost far less than they used to: `ImportableCommand` and the command-replay round-trip plumbing were
deleted with `EventOrientedBackupRestorePlan.md`, so a new command is now just the command, the
handler, and the projector branch.

### Derived status, not stored status

Labels are computed in the projector from the two streams, so new labels need no new events:

- `attendance` folds the commitment events; default `WATCHING`.
- `speaking` folds the submission events for that conference, plus `CfpWindowRecorded` compared
  against `today` (passed in from the boundary, per the external-inputs rule).
- **`REJECTED` + `WATCHING` surfaces as "decide"** — the single most useful derived label.
- `WAITLISTED` behaves as `SUBMITTED` for every downstream purpose.

**Decision (Ted, 2026-08-12): `SubmissionAccepted` auto-commits attendance.** The fold derives
`GOING` with basis `SPEAKING_ACCEPTED` from the acceptance alone — no separate
`ConferenceAttendanceCommitted` event on the speaking path. "Accepted, then couldn't go" stays
representable because **the last decision wins**: a later `ConferenceAttendanceDeclined` overrides the
acceptance, which is exactly Ted's "if something comes up, I would cancel."

An earlier draft recommended keeping them separate with a one-click commit. Overruled, and the
reasoning is sound: submitting a talk *is* opting in, so acceptance completes a decision Ted already
made rather than presenting him with a new one.

**`SpeakingInvitationReceived` does not auto-commit**, and the distinction is deliberate: an
invitation is unsolicited, so it is an offer awaiting Ted's yes, not the completion of something he
started. It needs an explicit `ConferenceAttendanceCommitted(basis: SPEAKING_INVITED)`.

## Consequences elsewhere in the codebase

### Redaction

- `CalendarEntryRedactor.java:41` currently copies conferences through field-by-field. Adding a
  `commitment` field to `CalendarEntry` **breaks that branch's compilation**, which is the intended
  forcing function (redaction rule 1). Give `CalendarEntry` a convenience overload defaulting to
  `CONFIRMED` so the other projectors are untouched and only the conference projector passes it.
- The redacted conference branch keeps name, venue, city, dates, `infoUrl`, and `commitment`, and
  names every other field explicitly.
- `NOT_GOING` and organizer-cancelled conferences render for **nobody** — they leave the calendar
  entirely, as `ConferenceCancelled` already does.
- **`CLAUDE.md` must be amended in the same change.** Its redaction section currently reads
  "**conferences and gatherings in full** — name, venue, city, `infoUrl`, and start/end times" under
  *Public by decision*. That stays true, but it needs two additions: commitment level **and the
  speaking flag** are public; submission status, talk titles, CFP dates and commitment basis are
  OWNER-only. It also needs the private-engagement caveat beside the existing private-dinner one — a
  company-internal talk must not be modelled as a conference or gathering. The section is accurate
  today (nothing is built), so it is not stale yet — it becomes stale the moment step 2 lands, or the
  gathering speaking badge ships, whichever comes first.
- Both tiers of test, per redaction rule 5: a `CalendarEntryRedactorTest` case per private field,
  and `CalendarRedactionSecurityTest` cases asserting the anonymous body `doesNotContain` a talk
  title, a rejection, a CFP date, and a commitment basis — asserting absence of the private value,
  not presence of a placeholder.

### Any new route is deny-by-default

A submissions surface (`/submissions`, or an extension of `/planned-conferences`) and its POST
endpoints are OWNER-only. Add the matcher to `SecurityConfig` **and** the `policy()` row to
`AuthorizationMatrixTest` in the same change (redaction rule 3). Hiding the nav card is not access
control. Per the `index.html` convention, ask Ted which nav group, which Font Awesome Pro fill-based
SVG from the travel-icons row, and what background before adding a card.

### `ScheduleGapProjector`

Only `GOING` conferences should occupy the schedule. A slot Ted is merely holding must not raise a
different-city conflict or a lodging gap against travel he has actually booked.

### Schengen counter

Speculative conferences feed the **ceiling**, not the floor. `NOT_GOING`, rejected-and-dropped,
withdrawn, and organizer-cancelled conferences must be excluded from the ceiling too, or it inflates
with dead entries and stops meaning anything.

**The scope of that dependency is narrow, and worth knowing before building for it.** After the
2026-08-12 amendment, `SchengenDayCounterPlan.md` derives its floor from *border-crossing envelopes*
where travel is booked, and falls back to conference/hotel dates only where it is not. So commitment
changes the Schengen number **only for conferences no envelope covers**. Once flights bracket a
conference, its days are already counted and the `WATCHING` / `GOING` label is irrelevant to the count
— it still matters on the calendar. Measured on the 2026-08-11 backup, exactly one conference
(Agile Testing Days, November) was in that position.

There is also **nothing to backfill for past conferences**: measured over the same data, conferences
contribute zero unique past Schengen days. See "Historical data" in that doc.

### `datesConfirmed`

A slot held before the CFP opens usually carries *last year's* dates. Because those guessed dates now
drive the Schengen ceiling, `ConferenceTentativelyPlanned` needs a `datesConfirmed` flag (or the
inverse, `datesProvisional`) so a guess is visibly marked wherever it is counted. Promoted out of
"nice to have" into the first slice for that reason.

### Read models

`TentativeConferenceProjector` / `TentativeConferenceView` become the conference **radar**: grouped
by derived status, with the actions inline (`WATCHING` → Submit / Buy ticket / Drop; `SUBMITTED` →
Accepted / Waitlisted / Rejected / Withdraw; `REJECTED` → Go anyway / Drop). Keep the
`TemporalView.relevantUntil()` + `TimeView` + `TimeFilterToggle.render(...)` trio — the FUTURE/ALL
convention is enforced by `TimeFilterToggleConventionTest`.

## Backfilling existing conferences (OPEN QUESTION — Ted, 2026-08-16)

Distinct from the Schengen "nothing to backfill" note above (which is only about the *day count*):
once the two status dimensions exist, every conference already in the app is a bare
`ConferenceTentativelyPlanned` with **no** commitment or speaking status. Figure out how to backfill
that — i.e. how the existing conferences get their real `attendance`/`speaking` state instead of all
defaulting to `WATCHING`/`NOT_SPEAKING`. Options to weigh when the time comes: a one-off admin
back-entry pass (append the commitment/submission events by hand from memory), a small guided
"catch up" UI on the radar view, or accept the default and only enrich going forward. Decide before
step 2 ships so the calendar doesn't briefly mislabel conferences Ted is actually committed to.

The `DeclineConferenceAttendance` slice shipped 2026-08-16 (the `ConferenceAttendanceDeclined` event,
its command/handler/projectors/controller and a Decline affordance on `/tentative-conferences`) is a
thin, forward-compatible first piece of this plan — it uses this plan's own event name and needs no
rework here.

## Build order

1. `datesConfirmed` on `ConferenceTentativelyPlanned` + the plan-conference form. Small, and the
   Schengen ceiling depends on it.
2. Commitment events, handlers, and the derived `attendance` fold. `CalendarEntry.commitment`, the
   redactor branch, and both tiers of redaction test land here — this is the slice that makes the
   calendar tell the truth.
3. `CfpWindowRecorded` + the radar view grouped by derived status.
4. The submission stream and its pipeline actions.
5. `ScheduleGapProjector` and Schengen ceiling filtering by attendance.

## Testing

- **Domain command tests** per new command, in the `PlanTentativeConferenceCommandTest` style.
- **Lifecycle-propagation scenarios** in each affected projector test — the preferred guard against
  a projector silently missing an event (sealing `Event` was rejected). A decline, a rejection, and
  an organizer cancellation must each move every read model that shows conferences.
- **Derived-status tests** over event sequences, including accept-then-decline, submit-then-withdraw,
  and reject-then-go-anyway.
- **`CalendarEntryRedactorTest` + `CalendarRedactionSecurityTest`**, as above.
- **`AuthorizationMatrixTest`** rows for every new route.
- **`@WebMvcTest`** slices for new controllers; `@WithMockUser`, and `.with(csrf())` on every POST.
  Any Thymeleaf-rendering endpoint needs one — template errors only surface at render time.
- Every new or changed test proven by mutating production code so it fails for the right reason,
  then reverted.

## Where a dropped conference goes

**Decision (Ted, 2026-08-12).** A `NOT_GOING` conference:

- **leaves the calendar and the itinerary entirely** — for owner and anonymous viewers alike, the same
  way `ConferenceCancelled` already behaves;
- **stays on the full conference list** (`/tentative-conferences`), so "looked at it, said no" is a
  record next year's entry can benefit from;
- is **hidden there by default, behind a toggle**.

**That toggle is orthogonal to the existing FUTURE/ALL one and must stay a separate parameter** —
`?dropped=show` alongside `?filter=all`, not a third value crammed into `filter`. The two ask
unrelated questions (when, versus whether Ted is going), and folding them together yields a
combinatorial parameter whose values have to be enumerated. Keep `TimeFilterToggle.render(...)`
untouched so `TimeFilterToggleConventionTest` keeps passing, and add the dropped toggle beside it.

No redaction concern on the list: `/tentative-conferences` is already OWNER-only
(`SecurityConfig.java:61`). The calendar is where the public/private line lives, and dropped
conferences are absent from it for everyone.

## Acceptance is public — there is no embargo

**Decision (Ted, 2026-08-12): once a submission is accepted, it is public.** Ted is talking about it
on social media as soon as the acceptance email lands, so nothing needs holding back and there is no
window where the app knows something he has not announced.

**What that settles:** the calendar may flip a conference from "tentative" to "attending" the moment
the acceptance is recorded, with no timing rule, no embargo flag, and no "announced yet?" state. This
was the last thing that might have required one.

**Speaking is public too (Ted, 2026-08-12).** *That Ted is speaking* at a conference or gathering is
never private information, because the event's place and time are already public. So the anonymous
calendar should show it. An earlier draft withheld the commitment *basis* on the grounds that
publishing it makes its **absence** meaningful — a conference reading only "attending" would say Ted
is not speaking there. Overruled, and rightly: "attending without speaking" is an ordinary thing
(the pure-attendee case), and it reveals a rejection only to someone who already knows he submitted,
which stays private either way.

So the public surface for a committed conference or gathering is: name, venue, city, dates, `infoUrl`,
commitment level, **and whether Ted is speaking**. Still OWNER-only: the submission pipeline, talk
titles, CFP dates, decision dates, and every free-text reason.

**The caveat, and it is a real one (Ted, 2026-08-12): a private talk at a company is not this.** An
internal corporate engagement has no public venue or time, and must **not** be modelled as a
conference or gathering to get a speaking badge — it needs its own `EntryKind`, exactly as the private
social event does. This is the same trap CLAUDE.md already flags for private dinners: reusing
GATHERING for a private-ish thing publishes it in full. See `docs/PrivateSocialEventPlan.md` for the
sibling pattern; a private speaking engagement is a second instance of it, not a variation of this
plan.

**Gatherings can ship this independently and first.** `GatheringPlanned.speaking` already exists and
already renders as a badge on owner/family surfaces (`ItineraryRenderer.java:231`, plus the OWNER-only
planned list), but `GatheringCalendarProjector.toEntry` does not even accept the field — it is dropped
at the projector. So the gathering half is: thread `speaking` through `toEntry`, add it to
`CalendarEntry`, keep it in the redactor's GATHERING branch, render the badge, and add both tiers of
test. No new events, no dependency on this plan's pipeline. The conference half has to wait for step 4,
because until submissions exist there is no speaking fact to publish.

# Conference Submission Tracking Plan — commitment, speaking status, and the CFP pipeline

> **Status: IN PROGRESS. Slice 1 (`ConferenceFormat`) shipped 2026-08-18; the Decline slice shipped
> 2026-08-16; slice 2 (commitment) shipped 2026-08-19. Slices 3–5 not started.** Design agreed with
> Ted in conversation. See `docs/Backlog.md` for the status of everything else.
>
> **Slice 2 as built (2026-08-19).** `AttendanceBasis` (3 values) + `ConferenceAttendanceConfirmed`
> (basis non-null, fails loud) + `ConfirmConferenceAttendanceCommand`/`Context` + a
> `ConfirmConferenceAttendance` application service folding "is the conference still live?" from the
> stream, exactly like `DeclineConference`. The derived level is `AttendanceCommitment` in
> `application` with **two** values (`WATCHING`, `GOING`) — no `NOT_GOING`, because a declined or
> cancelled conference leaves every read model, so "not going" is absence, not a value a renderer
> could ever be handed. Both conference projectors fold it: `ConferenceCalendarProjector` stamps
> `CalendarEntry.commitment` (rendering as the "Maybe" chip) and `ConferenceProjector` stamps
> `ConferenceView.commitment` (rendering as a **Going?** column on `/conferences`, with the
> **Confirm** link shown on `WATCHING` rows only — one word, because that cell's links are
> nowrap units and a longer label widens the table past a narrow viewport). **Confirming twice is allowed** — a
> second confirmation with a different basis is how "I'd bought a ticket, then the talk was
> accepted" is recorded, and the fold takes the last one.
>
> **Sequencing decided 2026-08-19: slice 2 shipped *before* the S2 + E2 calendar refactor** of
> `RendererVsProjectorResponsibilities.md`. Two small reviewable diffs instead of one large one.
> **That refactor has since landed (2026-08-21)**, so `commitment` is no longer a nullable field on a
> flat `CalendarEntry`: it is a component of `EntryDetails.Conference` for the owner and
> `EntryDetails.PublicConference` for anonymous viewers, and `CalendarEntryRedactor` is gone. Read the
> box at **"Consequences elsewhere → Redaction"** before starting slice 3 or 4 — it says what that
> changes for the work still to come.
>
> **What slice 2 did *not* build:** `TalkAccepted`'s auto-commit and `InvitedToSpeak`'s
> offer-to-commit. Those events land in slice 4, so their arms of the fold land with them — there is
> nothing to write a branch against today. The confirm form is the manual path, and it is the one
> the backfill uses.
>
> **Slice 1 as built (2026-08-18):** `ConferenceFormat` (CALL_FOR_PAPERS / ACCEPTANCE_REQUIRED /
> OPEN_SPACE) rides on `ConferencePlanned` as a **non-null** field; the plan form picks it
> with **radio buttons** (Ted's UI preference), defaulting to CALL_FOR_PAPERS. **`datesConfirmed` was
> deferred** out of slice 1 — its only consumer is the slice-5 Schengen ceiling, so it will land with
> that slice rather than shipping a form control months ahead of its behaviour.
>
> **How the default reaches legacy rows — decided this session (choice A).** An absent `format` is
> injected as CALL_FOR_PAPERS by the **read-time upcaster** as an *independent* schema increment
> (v2→v3), applied by its own absence check so a row written after the datetime migration but before
> `format` existed is still brought current; `ConferencePlanned` is bumped to schema
> **version 3** in `EventTypes`, so the eager migration rewrites stored rows to physically carry the
> field. The record's compact constructor **fails loud** on a null `format`, since production always
> upcasts before binding. This was chosen over a compact-constructor null-default so the value is
> stored/migrated/versioned (a domain-behavioural field, not a read-time ghost) through the one
> general-purpose payload-migration mechanism — the version-ladder of `EventUpcaster` rungs described
in `EventPayloadUpcasterDesign.md` (this migration, `format` v2→v3, is the `ConferenceFormatUpcaster`
rung that shaped that framework out of the previously single-class upcaster).
>
> **Slice 2 decisions taken 2026-08-19, before any code:** `AttendanceBasis` is **three values**
> (`ATTENDING_ANYWAY` dropped — see "AttendanceBasis" below); the public calendar shows a **"Maybe"
> chip on speculative conferences only**, identical for owner and anonymous viewers (see "The public
> calendar label"); and the app-layer vocabulary is realigned from "tentative conferences" to plain
> **"conferences"**, route included, as a separate rename commit landing *before* slice 2 (see "The
> tentative → conferences realignment").
>
> **The 2026-08-18 refinement (this pass) settled the last open questions and simplified the model.**
> It is driven by a concrete need — CFPs for next year are opening now — and by four real conferences
> already entered (dev2next, ExploreDDD, SoCraTes DE, J-Fall, PLoP). The decisions below **supersede**
> the corresponding parts of the original design where they differ; the superseded text is kept for
> the reasoning trail. Jump to **"Refinement (2026-08-18)"** for the current shape.

## Problem

Every conference in the app is a `ConferencePlanned`, and "tentative" is now carrying
four unrelated meanings at once:

1. accepted to speak, and going;
2. submitted a talk, waiting to hear;
3. want to go but undecided — either the CFP has not opened yet (hold the slot), or not submitting
   and no ticket bought yet;
4. ticket bought as a pure attendee, not speaking.

They all render identically on `/calendar` and in `/conferences`, so the list cannot answer
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
| `ConferencePlanned` (exists) | Renamed from `ConferenceTentativelyPlanned` 2026-08-19 (old wire ids aliased in `EventTypes`). Reads as "on my radar" — the `WATCHING` entry point. |
| `CfpWindowRecorded(conferenceId, opensOn, closesOn)` | Optional; either date may be absent. Drives `CFP_PENDING` → `CFP_OPEN` and the "submit before you lose it" nudge. |
| `ConferenceAttendanceCommitted(conferenceId, basis, committedOn)` | `basis` ∈ `SPEAKING_ACCEPTED`, `SPEAKING_INVITED`, `TICKET_PURCHASED` (`ATTENDING_ANYWAY` dropped 2026-08-19). **Owner-only.** |
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
on `ConferencePlanned` would mean rewriting the very thing that changes after the fact,
and would break existing backup files. A single `ConferenceStatusChanged(status, note)` would lump
genuinely different facts ("organizers rejected me" vs. "I changed my mind"), give talk titles
nowhere to live, and could not represent three submissions to one conference. Explicit events also
cost far less than they used to: `ImportableCommand` and the command-replay round-trip plumbing were
deleted with `archived/EventOrientedBackupRestorePlan.md`, so a new command is now just the command, the
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

## Refinement (2026-08-18) — the current shape

Everything in this section supersedes the original where they differ. Grounded in four real
conferences: **dev2next** (speaking, accepted), **ExploreDDD** (had a CFP, submitted, rejected,
attending anyway), **SoCraTes DE** (open-space, no CFP, buys a ticket), **J-Fall** (CFP open now),
and **PLoP** (a writers'-workshop where **acceptance is required to attend**).

### `ConferenceFormat` — how a conference forms its program

The open-space case is **not** a new `EntryKind`: SoCraTes is a fully public, multi-day event with
name/venue/city/dates/infoUrl — publicly identical to any conference, so it shares the `CONFERENCE`
redaction branch. A new `EntryKind` is only for something *private* (the company-internal talk, the
private dinner). What actually varies is **how you get on the program**, which is intrinsic to the
conference and known when it is entered — so it is a **field on `ConferencePlanned`**, not
a separate "what happened" event (nothing happened; SoCraTes simply *is* open-space).

A boolean `hasCfp` was rejected (Ted: "booleans eventually become enums anyway" — and PLoP proved it
on the same day). The enum, with three real values, no speculation:

```
ConferenceFormat { CALL_FOR_PAPERS, ACCEPTANCE_REQUIRED, OPEN_SPACE }
```

| Value | Example | Submission? | On `TalkRejected` |
|---|---|---|---|
| `CALL_FOR_PAPERS` | dev2next, ExploreDDD, J-Fall | open CFP; attend regardless of outcome | `REJECTED + WATCHING` → visible **"decide: go anyway or drop?"** |
| `ACCEPTANCE_REQUIRED` | PLoP | acceptance **gates** attendance | **auto-drops** → `NOT_GOING`, leaves the calendar; no "go anyway" |
| `OPEN_SPACE` | SoCraTes DE | none — sessions chosen on the day | n/a (no CFP; a `CfpOpened` on this format is **rejected by the command handler**) |

Note `CALL_FOR_PAPERS` + `NOT_SPEAKING` is a real, distinct state (ExploreDDD before he submitted, or
a conference he only attends): the conference *has* a CFP, he just didn't submit — different from
"no CFP exists." The enum keeps them apart. `ACCEPTANCE_REQUIRED` bundles a behavior (rejection
auto-drops) into the format value deliberately — all three values answer the one question "how do you
get in," and the fold branches on the value.

Existing conferences default absent→`CALL_FOR_PAPERS` via the upcaster — the safe default (offers the
CFP action rather than silently hiding it); the backfill pass re-marks SoCraTes to `OPEN_SPACE` and
PLoP to `ACCEPTANCE_REQUIRED` once. **Shipped 2026-08-18** as slice 1 (schema bump to v3 on
`ConferencePlanned`); `datesConfirmed` was **deferred** to the slice that consumes it
(slice 5), so slice 1 was `ConferenceFormat` alone.

### Event names are "what happened", not CRUD (Ted, 2026-08-18)

Renamed from the original tables. See the global rule in memory / CLAUDE-adjacent guidance.

| Original draft | Current name |
|---|---|
| `CfpWindowRecorded(conferenceId, opensOn, closesOn)` | **`CfpOpened(conferenceId, closesOn)`** — opens = the event; carries only the close date (no "watch for it to open" case yet) |
| `TalkSubmitted(submissionId, conferenceId, talkTitle, submittedOn)` | **`TalkSubmitted(conferenceId, submittedOn)`** — conference-keyed, see below |
| `SubmissionAccepted` / `SubmissionRejected` / `SubmissionWaitlisted` / `SubmissionWithdrawn` | **`TalkAccepted` / `TalkRejected` / `TalkWaitlisted` / `TalkWithdrawn`** `(conferenceId, decidedOn)` |
| `SpeakingInvitationReceived(conferenceId, invitedOn)` | **`InvitedToSpeak(conferenceId, invitedOn)`** |
| `ConferenceAttendanceCommitted(conferenceId, basis, committedOn)` | **`ConferenceAttendanceConfirmed(conferenceId, basis, confirmedOn)`** — pairs with the shipped `ConferenceAttendanceDeclined` |
| `ConferenceTentativelyPlanned` | **`ConferencePlanned`** (2026-08-19; retired wire ids aliased), gaining `datesConfirmed` **and** `format` fields |

`ConferenceCancelled` (organizers cancelled) and `ConferenceAttendanceDeclined` (Ted's own decision —
**distinct** from a `TalkRejected`, which is the organizers' decision) are unchanged.

### No per-talk tracking yet — submissions are conference-keyed

For calendaring it does not matter whether Ted submitted one talk or three, or of what type
(Ted, 2026-08-18). So `SubmissionId` and `talkTitle` are **dropped for now**: the submission events
key on `ConferenceId`, and the speaking fold collapses to **best-outcome-wins** per conference
(`accepted` beats `waitlisted` beats `submitted` beats `rejected` beats `withdrawn`). `TalkSubmitted`
reads as "I submitted (one or more talks) to this CFP." When per-talk state earns its keep, add
`SubmissionId` + title then — not before (no abstraction before the 2nd user).

**ExploreDDD's "rejected, attended anyway"** (nice-to-have) needs no extra machinery: `TalkRejected`
(conference-keyed) then `ConferenceAttendanceConfirmed(basis: TICKET_PURCHASED)`. The rejection is
recorded; the confirm is the "go anyway." Only reachable for `CALL_FOR_PAPERS`; for
`ACCEPTANCE_REQUIRED` the rejection auto-drops.

### `AttendanceBasis` is three values — `ATTENDING_ANYWAY` dropped (Ted, 2026-08-19)

```
AttendanceBasis { SPEAKING_ACCEPTED, SPEAKING_INVITED, TICKET_PURCHASED }
```

The original four carried `ATTENDING_ANYWAY` alongside `TICKET_PURCHASED`, but they name the same
fact: buying a ticket **is** how "I'll go anyway" happens (Ted, 2026-08-19) — the SoCraTes open-space
case and the ExploreDDD rejected-then-attended case are both just a purchased ticket. Nothing
downstream ever branched on the difference.

The narrative the fourth value was trying to preserve — *that Ted went anyway after a rejection* —
is **already recorded by the preceding `TalkRejected` event**, so encoding it a second time in the
basis duplicates a fact the stream holds, and duplicated facts drift. Read the sequence, not the
label.

The surviving three partition cleanly into speaking (`SPEAKING_ACCEPTED`, `SPEAKING_INVITED`) and
not (`TICKET_PURCHASED`), which is exactly the read the slice-4 conference speaking badge needs —
and, before it, the `SPEAKER` marker on the `/conferences` radar (see below).
`basis` stays **OWNER-only and never enters `CalendarEntry`** regardless.

### The public calendar label — a "Maybe" chip, speculative only (Ted, 2026-08-19)

Slice 2 stamps a commitment onto `CalendarEntry`. **The collapse to the public label happens in the
projector, not the redactor:** `CalendarEntry` carries only the already-collapsed level, so `basis`
and submission status never enter the view at all. That is redaction rule 1 doing the work
structurally rather than the redactor stripping fields.

What renders:

| Commitment | Calendar |
|---|---|
| `WATCHING` (every speculative state) | a **"Maybe"** chip on the entry |
| `GOING` | no chip — a plain conference entry |
| `NOT_GOING` / organizer-cancelled | absent entirely, for everyone |

**Chip on the speculative case only.** A badge earns its place when it says something non-obvious:
"Ted might be at this one" is non-obvious, while "Ted is going" is the default reading of any
calendar entry — a `Going` chip would be noise on every committed conference, and would make its own
absence something a reader has to reason about. This matches the existing precedent: the "A Ted
Talk" speaking badge shows only in the exceptional case.

**The same chip for owner and anonymous viewers.** One rendering path, one collapse, nothing for the
redactor to get wrong. Richer per-conference status (submitted / waitlisted / decide) belongs on the
conference radar list in slice 3, not on the calendar.

A styling-only distinction (muted vs. solid) was rejected: it is invisible to anyone who does not
already know the convention, and muted conventionally reads as *cancelled*.

### The `/conferences` speaking marker — **SPEAKER** in the Going? column (Ted, 2026-08-19)

The OWNER-only list marks that Ted is speaking in the **same `Going?` column** as the commitment
chip, and the word is **`SPEAKER`** — deliberately *not* the calendar's "A Ted Talk".

| surface | wording | why |
|---|---|---|
| public `/calendar` entry | "A Ted Talk" | the entry cell owns a whole row of a day column; the longer, playful wording fits and reads well |
| OWNER `/conferences` row | **`SPEAKER`** | one nowrap unit in a seven-column table that only just fits — the page started scrolling sideways at ~860px and fits at ~820px only after the container gave up its gutter (measured 2026-08-19), so every extra word in that column costs real width |

Same fact, two surfaces, two lengths. This is not a new dimension: it renders alongside
`Maybe`/`Going` rather than replacing either, so a speculative conference Ted has been invited to
can read `Maybe` + `SPEAKER`.

**Where the fact comes from — and it changes at slice 4.**

- *Before slice 4:* from `AttendanceBasis` on `ConferenceAttendanceConfirmed` —
  `SPEAKING_ACCEPTED` and `SPEAKING_INVITED` are speaking, `TICKET_PURCHASED` is not. This is the
  partition the basis was chosen for, and it is the **only** speaking evidence the backfilled
  conferences will carry.
- *From slice 4 on:* the **submission fold** (`TalkAccepted`, `InvitedToSpeak`) is authoritative,
  because that is where speaking becomes a tracked fact rather than a one-off label. The basis
  stays as the stand-in for conferences recorded before those events existed. If the two ever
  disagree, the stream wins — the basis is a manual annotation, the submission events are history.

**What it needs.** `ConferenceProjector` today reads `event.basis()` and deliberately discards it
(nothing rendered it). It stops discarding it and carries a **derived `speaking` boolean** — *not*
the basis itself — onto `ConferenceView`. Carrying the boolean keeps accepted-vs-invited out of the
view for the same reason `CalendarEntry` carries only the collapsed commitment: a field that never
enters a view cannot leak from it, and "which of the two speaking bases" is submission status.

**No redaction question here.** `/conferences` is OWNER-only (`SecurityConfig` matcher +
`AuthorizationMatrixTest` row), so this marker is invisible to family and anonymous viewers by
construction. It says nothing about the *public* conference speaking badge on `/calendar`, which
remains a separate slice-4 decision — see "Redaction" below and the gathering precedent.

**Sequencing.** Shippable any time after slice 2 (the basis exists now), but it belongs with
**slice 3**, which reworks this list into the radar and therefore the column layout; its source then
flips to the submission fold in **slice 4** with the rest of the pipeline.

### The `tentative` → `conferences` realignment (Ted, 2026-08-19)

Once commitment is its own derived dimension, "tentative" is precisely the concept being *removed*
from the model — so a `GOING` conference living in a class called `TentativeConferenceProjector` is
an active lie. The app-layer vocabulary is realigned to plain "conferences" as a **separate rename
commit landing before slice 2**, so the rename diff stays reviewable at a glance instead of hiding
inside a behavioural change. (Both touch `SecurityConfig` and `AuthorizationMatrixTest`; renaming
first makes slice 2's change there a single added row.)

**Renamed:** `TentativeConferenceProjector` → `ConferenceProjector`, `TentativeConferenceView` →
`ConferenceView`, `TentativeConferencesRenderer` → `ConferencesRenderer`, the route
`/tentative-conferences` → **`/conferences`**, and the command side
`PlanTentativeConference{Command,Handler,Context,Request}` → `PlanConference*` (already inconsistent
with its own `PlanConferenceController`, which never carried "Tentative").

**The event too — decided the same day, after a second look.** The first pass deliberately left
`ConferenceTentativelyPlanned` alone; on review that was reversed and it is now **`ConferencePlanned`**
(class *and* `EventTypes` logical name). Three things settled it:

- **The rename made the command/event pair asymmetric.** `PlanConference` emitting
  `ConferenceTentativelyPlanned` is the only place in the model where a command and its event do not
  share a stem — every other family reads `Plan X` → `XPlanned` (`GatheringPlanned`,
  `PrivateEventPlanned`).
- **The old name encoded a status the model no longer stores.** "Tentatively" is an assessment of
  commitment, and this whole plan makes commitment *derived* (`WATCHING`/`GOING`, folded from later
  events). Per the "events are what happened" rule, the fact is that a conference went on the
  schedule; the state assertion does not belong in its name.
- **Slice 2 adds siblings** (`TalkSubmitted`, `TalkRejected`, `ConferenceAttendanceConfirmed`), and a
  `Conference*` family whose entry point alone says "Tentatively" reads as a leftover. Cheaper before
  those land than after.

The cost, paid in full in the rename commit: `EventTypes` gains an `alias` line for **each** wire id
the type was ever stored under — the retired logical name `"ConferenceTentativelyPlanned"` *and* the
FQCN from before logical names existed — so stored rows and older backup files keep resolving,
untouched. Both upcaster rungs (`ConferenceTimeZoneUpcaster`, `ConferenceFormatUpcaster`) key on the
logical name, so their `canHandle` moved to the new one in the same change. The golden legacy sample
in `GoldenEventDeserializationTest` deliberately **keeps** the old wire id: a stored row carries the
name it was written under, so that is what the sample must exercise.

What this does *not* fix is the divergence that motivated the original decision: `event_log` and any
backup taken before today still say `ConferenceTentativelyPlanned`, so the log holds both spellings.
Normalizing the `type` column (an eager-migration-style rewrite, the way `schema_version` is stamped)
was **considered and not done** — it is available later if the two names become a real nuisance.

**Not renamed, deliberately:** `TentativeHotelBooking*`, `status-tentative`, "Tentative/Final" — a
second, unrelated meaning of "tentative" (hotel booking status). Out of scope; a blanket rename would
have hit these.

Command class names are safe to change: per `EventTypes`' javadoc a command's `type` is stored
opaquely and never resolved back to a class. The only consequence is `/admin/commandlog` showing two
names for the same command across the rename boundary.

### Backfilling existing conferences — resolved

The open question from 2026-08-16 is settled: **backfill is a one-off pass through the real UI**, not
a special admin tool and not accept-the-default. Backfill records each conference's *current end
state* with a single event through the same affordances new conferences use — it never reconstructs
CFP history:

| Conference | Format | Backfill event(s) | Derived |
|---|---|---|---|
| dev2next (speaking) | `CALL_FOR_PAPERS` | `ConferenceAttendanceConfirmed(basis: SPEAKING_ACCEPTED)` | `GOING` + speaking badge |
| ExploreDDD (attending only) | `CALL_FOR_PAPERS` | `ConferenceAttendanceConfirmed(basis: TICKET_PURCHASED)`; optionally `TalkRejected` first to record the "anyway" | `GOING`, no badge |
| SoCraTes DE (open-space) | `OPEN_SPACE` | `ConferenceAttendanceConfirmed(basis: TICKET_PURCHASED)` | `GOING`, no CFP nudge |
| J-Fall (CFP open) | `CALL_FOR_PAPERS` | `CfpOpened(closesOn)`; then submit forward | `WATCHING` + `CFP_OPEN` |

Accept-the-default is out: it would mislabel dev2next as not-speaking, the one outcome we can't have.
There is a handful of conferences and Ted knows each one's state, so this is minutes of clicking once
the actions exist — decided **before** slice 2 ships so the calendar never briefly mislabels a
committed conference.

### CFP closing deadline rides the existing iCal feed (72h + 24h)

`CfpOpened.closesOn` is structurally identical to a hotel `cancelBy` — a deadline not to miss — so it
becomes a VEVENT at the closing instant with **two `VALARM`s, 72h and 24h before** (Ted, 2026-08-18),
fired locally by iOS exactly like the hotel cancel-deadline reminders. No scheduler; a pure
projection over the event. Safe on the private side: CFP dates are OWNER-only, and that feed is
already token-gated **unredacted owner data** (never the public `/calendar`).

Architecturally this is the moment the deferred `ICalEventSource` abstraction earns itself:
`archived/CalendarSubscriptionFeedPlan.md` deliberately held it back "until the 2nd contributor (no abstraction
before 2nd user)." **CFP deadlines are that second contributor** — so introduce `ICalEventSource`
cleanly here, alongside the existing hotel-cancel source, rather than speculatively earlier.

No "watch for the CFP to open" reminder yet (Ted, 2026-08-18) — closing deadline only.

### Revised build order (sequenced for CFP season)

The original bottom-up order is re-cut so the CFP-season payoff and the backfill land together:

1. **`ConferenceFormat`** on `ConferencePlanned` + the plan-conference form (schema bump to
   v3; upcaster injects absent format→`CALL_FOR_PAPERS` as an independent increment). **SHIPPED
   2026-08-18.** `datesConfirmed` was split out and deferred to slice 5 (its only consumer is the
   Schengen ceiling), so this slice was format alone.
2. **Commitment fold + `ConferenceAttendanceConfirmed`** + `CalendarEntry.commitment` + the "Maybe"
   chip + the redactor branch + both tiers of redaction test + CLAUDE.md amend. This is the slice
   that makes the calendar tell the truth, and the slice the **backfill runs through** — so the
   backfill decision (above) is its gate. Preceded by the rename commit (see "The `tentative` →
   `conferences` realignment"). Note the backfill lands in **two sittings**: dev2next, ExploreDDD and
   SoCraTes are reachable after this slice, but J-Fall's `CfpOpened` row needs slice 3.
   **SHIPPED 2026-08-19** — see "Slice 2 as built" at the top. `TalkAccepted` auto-commit and
   `InvitedToSpeak` offer-to-commit moved to slice 4 with the events they fold.
3. **`CfpOpened` + the CFP-deadline iCal source (72h + 24h)** + the radar view grouped by derived
   status, `OPEN_SPACE` in a "nothing to submit" group. Pulls the deadline reminder forward because
   that is the concrete CFP-season value. Also lands the **`SPEAKER` marker in the `Going?`
   column**, basis-sourced for now (see "The `/conferences` speaking marker").
4. **The submission stream** (`TalkSubmitted` / `TalkAccepted` / `TalkRejected` / `TalkWaitlisted` /
   `TalkWithdrawn`, conference-keyed) + the pipeline actions on the radar, incl. the
   `ACCEPTANCE_REQUIRED` auto-drop-on-reject fork and the `CALL_FOR_PAPERS` "decide" affordance. The
   conference **speaking badge** unlocks here, and the `Going?` column's `SPEAKER` marker re-sources
   from this fold instead of `AttendanceBasis`.
5. **`ScheduleGapProjector` + Schengen ceiling** filtering by attendance (only `GOING` occupies the
   schedule; speculative feeds the ceiling).

## Consequences elsewhere in the codebase

### Redaction

> **⚠️ The mechanism described below was replaced on 2026-08-21 — read this box before slice 3 or 4.**
> `CalendarEntryRedactor` is **deleted**. The public calendar is no longer the owner's read model with
> fields stripped out; it is its own read model, built from events by `PublicCalendarProjector`
> (decision S2 + E2, `docs/RendererVsProjectorResponsibilities.md`). **What that changes for the
> slices still to come:**
> - There is no redactor branch to add. To publish something new about a conference, add it to
>   `EntryDetails.PublicConference` **and** read it in the projector's `ConferencePlanned` /
>   `ConferenceAttendanceConfirmed` arms. To keep something private, simply never read it — a field
>   the projector does not read cannot leak, and needs no test proving it was stripped.
> - The **conference speaking badge** (slice 4) is a component on `EntryDetails.PublicConference`,
>   the way `speaking` already is on `EntryDetails.PublicGathering`.
> - Tests: `PublicCalendarProjectorTest` replaces `CalendarEntryRedactorTest` everywhere it is named
>   below, and its `theEveryKindFixtureCoversEveryKind` must keep covering every kind.
>   `CalendarRedactionSecurityTest` is unchanged in role and is now explicitly the primary guard —
>   its anonymous fixtures are built by driving **real events through a real projector**.
> - The **collapse precondition still binds**: `AttendanceCommitment` may be published only while it
>   cannot be mapped back to a submission outcome. Slice 4 adds the submission stream, so that is the
>   slice most likely to break it — re-read "Public by decision" in CLAUDE.md before touching the enum.
>
> The historical account below is kept for the reasoning, not as instructions.

- `CalendarEntryRedactor` copied conferences through field-by-field. Adding a `commitment` field to
  `CalendarEntry` **broke every branch's compilation**, which was the intended forcing function
  (redaction rule 1). **As built:** the two existing convenience constructors defaulted it to `null`
  ("not applicable"), so the five non-conference projectors were untouched, and the conference
  projector named it through the canonical constructor. Every redactor branch named it too — the
  CONFERENCE branch passed it through, the other five wrote `null` — and a test asserted the drop on
  each of those five kinds. *(Both that pass-through and that test are gone: no non-conference
  details type has a commitment any more, so the case cannot be constructed.)*
- The redacted conference branch kept name, venue, city, dates, `infoUrl`, and `commitment`, and
  named every other field explicitly.
- `NOT_GOING` and organizer-cancelled conferences render for **nobody** — they leave the calendar
  entirely, as `ConferenceCancelled` already does.
- **`CLAUDE.md` must be amended in the same change.** *(Done 2026-08-19 with slice 2: the private
  list gained the submission pipeline / CFP dates / `AttendanceBasis`, and "Public by decision"
  gained the commitment chip, its collapse precondition, and the "never model a private talk as a
  conference either" caveat.)* Its redaction section read
  "**conferences and gatherings in full** — name, venue, city, `infoUrl`, and start/end times" under
  *Public by decision*. That stays true, but it needs two additions: commitment level **and the
  speaking flag** are public; submission status, talk titles, CFP dates and commitment basis are
  OWNER-only. It also needs the private-engagement caveat beside the existing private-dinner one — a
  company-internal talk must not be modelled as a conference or gathering. The section is accurate
  today (nothing is built), so it is not stale yet — it becomes stale the moment step 2 lands, or the
  gathering speaking badge ships, whichever comes first.
- Both tiers of test, per redaction rule 5: a `PublicCalendarProjectorTest` case asserting the
  private field is absent from what the projector emits, and `CalendarRedactionSecurityTest` cases asserting the anonymous body `doesNotContain` a talk
  title, a rejection, a CFP date, and a commitment basis — asserting absence of the private value,
  not presence of a placeholder.

### Any new route is deny-by-default

A submissions surface (`/submissions`, or an extension of `/conferences`) and its POST
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
drive the Schengen ceiling, `ConferencePlanned` needs a `datesConfirmed` flag (or the
inverse, `datesProvisional`) so a guess is visibly marked wherever it is counted.

**Deferred to slice 5 (Ted, 2026-08-18).** It was briefly promoted into slice 1, but its only consumer
is the Schengen ceiling (slice 5) — shipping a form control months ahead of the behaviour that reads
it is a papercut with no payoff, so slice 1 shipped `ConferenceFormat` alone and `datesConfirmed`
lands with the ceiling. Adding it later is a second schema bump on `ConferencePlanned`
(v3→v4) with its own upcaster increment (absent→provisional), exactly like the format increment.

### Read models

`ConferenceProjector` / `ConferenceView` become the conference **radar**: grouped
by derived status, with the actions inline (`WATCHING` → Submit / Buy ticket / Drop; `SUBMITTED` →
Accepted / Waitlisted / Rejected / Withdraw; `REJECTED` → Go anyway / Drop), and a derived
`speaking` boolean on the view rendering as **`SPEAKER`** in the `Going?` column. Keep the
`TemporalView.relevantUntil()` + `TimeView` + `TimeFilterToggle.render(...)` trio — the FUTURE/ALL
convention is enforced by `TimeFilterToggleConventionTest`.

## Backfilling existing conferences (RESOLVED 2026-08-18 — see "Refinement" above)

> **Resolved:** backfill is a one-off pass through the real UI (record each conference's current end
> state with a single `ConferenceAttendanceConfirmed` / `CfpOpened`, never reconstruct history). The
> per-conference mapping and the reasoning are in **"Backfilling existing conferences — resolved"**
> under the Refinement section. The original open-question text is kept below for the trail.

Distinct from the Schengen "nothing to backfill" note above (which is only about the *day count*):
once the two status dimensions exist, every conference already in the app is a bare
`ConferencePlanned` with **no** commitment or speaking status. Figure out how to backfill
that — i.e. how the existing conferences get their real `attendance`/`speaking` state instead of all
defaulting to `WATCHING`/`NOT_SPEAKING`. Options to weigh when the time comes: a one-off admin
back-entry pass (append the commitment/submission events by hand from memory), a small guided
"catch up" UI on the radar view, or accept the default and only enrich going forward. Decide before
step 2 ships so the calendar doesn't briefly mislabel conferences Ted is actually committed to.

The `DeclineConferenceAttendance` slice shipped 2026-08-16 (the `ConferenceAttendanceDeclined` event,
its command/handler/projectors/controller and a Decline affordance on `/tentative-conferences`) is a
thin, forward-compatible first piece of this plan — it uses this plan's own event name and needs no
rework here.

## Build order (SUPERSEDED 2026-08-18 — see "Revised build order" under Refinement)

> The order below is replaced by **"Revised build order (sequenced for CFP season)"** in the
> Refinement section, which folds in `ConferenceFormat`, the CFP-deadline iCal source, and the
> event renames. Kept for the trail.

1. `datesConfirmed` on `ConferencePlanned` + the plan-conference form. Small, and the
   Schengen ceiling depends on it.
2. Commitment events, handlers, and the derived `attendance` fold. `CalendarEntry.commitment`, the
   redactor branch, and both tiers of redaction test land here — this is the slice that makes the
   calendar tell the truth.
3. `CfpWindowRecorded` + the radar view grouped by derived status.
4. The submission stream and its pipeline actions.
5. `ScheduleGapProjector` and Schengen ceiling filtering by attendance.

## Testing

- **Domain command tests** per new command, in the `PlanConferenceCommandTest` style.
- **Lifecycle-propagation scenarios** in each affected projector test — the preferred guard against
  a projector silently missing an event (sealing `Event` was rejected). A decline, a rejection, and
  an organizer cancellation must each move every read model that shows conferences.
- **Derived-status tests** over event sequences, including accept-then-decline, submit-then-withdraw,
  and reject-then-go-anyway.
- **`PublicCalendarProjectorTest` + `CalendarRedactionSecurityTest`**, as above.
- **`AuthorizationMatrixTest`** rows for every new route.
- **`@WebMvcTest`** slices for new controllers; `@WithMockUser`, and `.with(csrf())` on every POST.
  Any Thymeleaf-rendering endpoint needs one — template errors only surface at render time.
- Every new or changed test proven by mutating production code so it fails for the right reason,
  then reverted.

## Where a dropped conference goes

**Decision (Ted, 2026-08-12).** A `NOT_GOING` conference:

- **leaves the calendar and the itinerary entirely** — for owner and anonymous viewers alike, the same
  way `ConferenceCancelled` already behaves;
- **stays on the full conference list** (`/conferences`), so "looked at it, said no" is a
  record next year's entry can benefit from;
- is **hidden there by default, behind a toggle**.

**That toggle is orthogonal to the existing FUTURE/ALL one and must stay a separate parameter** —
`?dropped=show` alongside `?filter=all`, not a third value crammed into `filter`. The two ask
unrelated questions (when, versus whether Ted is going), and folding them together yields a
combinatorial parameter whose values have to be enumerated. Keep `TimeFilterToggle.render(...)`
untouched so `TimeFilterToggleConventionTest` keeps passing, and add the dropped toggle beside it.

No redaction concern on the list: `/conferences` is already OWNER-only
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
GATHERING for a private-ish thing publishes it in full. See `docs/archived/PrivateSocialEventPlan.md` for the
sibling pattern; a private speaking engagement is a second instance of it, not a variation of this
plan.

**Gatherings can ship this independently and first.** `GatheringPlanned.speaking` already exists and
already renders as a badge on owner/family surfaces (`ItineraryRenderer.java:231`, plus the OWNER-only
planned list), but `GatheringCalendarProjector.toEntry` does not even accept the field — it is dropped
at the projector. So the gathering half is: thread `speaking` through `toEntry`, add it to
`CalendarEntry`, keep it in the redactor's GATHERING branch, render the badge, and add both tiers of
test. No new events, no dependency on this plan's pipeline. The conference half has to wait for step 4,
because until submissions exist there is no speaking fact to publish.

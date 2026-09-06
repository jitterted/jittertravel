package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceDetailView;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.SpeakingStatus;
import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static j2html.TagCreator.*;

/**
 * One conference in full, at {@code /conferences/{id}} — the app's first read-only detail page.
 * <p>
 * <strong>Laid out by volatility, not by field</strong> (Ted, 2026-09-06, choosing this over three
 * alternatives). The first version gave When, Where, Attendance, Call for papers and Talk five
 * identical bordered panels in an {@code auto-fit} grid. Ted: <em>"nothing stands out, all
 * information is treated the same"</em> — and he was right for a reason worth writing down: border,
 * radius, padding and heading were spent evenly across all five, so a street address that has not
 * changed since he typed it carried the same weight as a talk status that changes every time the
 * organizers write. Emphasis spent evenly is emphasis not spent.
 * <p>
 * So the page is now two tracks:
 * <ul>
 *   <li><strong>The live column</strong> — the three things that can change while he is not looking:
 *       attendance, the talk, the CFP. Each is a row carrying its state, what that means, and
 *       <em>its own moves</em>.</li>
 *   <li><strong>The fact rail</strong> — when and where, set small and quiet, because it is
 *       reference rather than news.</li>
 * </ul>
 * Two fixed grid tracks rather than {@code auto-fit}, so a row is in the same place on the iPad as
 * on the desktop; the old grid reflowed 3 → 2 → 1 columns and moved every panel with it.
 * <p>
 * <strong>Each move sits beside the state it changes</strong>, which is the whole point of the
 * arrangement, and it is classified by the command it posts rather than by what it is about: the
 * {@code /talk} moves are the talk's, the {@code /confirm} and {@code /decline} moves are
 * attendance's. That is why "Invitation Accepted" is on the attendance row even though an invitation
 * is talk-shaped — it commits attendance, and the row it changes is the row it belongs on.
 * <p>
 * <strong>Colour is semantic, and it is the app's existing language.</strong> An amber left edge
 * means waiting — on someone else, or on a deadline; green means settled; no edge means there is
 * nothing happening on that track. It is the same amber the pending-commands banner uses for work
 * waiting, and the same green as the Going chip.
 * <p>
 * <strong>No commitment chip in the header</strong> (Ted, 2026-09-06): the attendance row says
 * "Going" two lines below it, and saying it twice is what made the old header feel like decoration.
 * The speaking badge stays, because it is <em>not</em> restated anywhere — {@code speaking()} is
 * derived, and it can be true while the talk row reads "Invited to speak" or even "Nothing
 * submitted".
 * <p>
 * <strong>OWNER-only, and one row depends on it.</strong> {@code /conferences/*} is
 * {@code hasRole("OWNER")} in {@code SecurityConfig}, which is what makes the attendance
 * <em>basis</em> showable here and nowhere else — see {@link ConferenceDetailView}. Everything CFP-
 * and talk-shaped on this page is on CLAUDE.md's private list for the same reason.
 * <p>
 * <strong>No pencil.</strong> A pencil means edit and nothing else (Ted, 2026-09-04), and there is
 * nothing to edit yet — Change Conference is deferred. When it ships, the pencil is what it earns.
 */
public class ConferenceDetailRenderer {

    /**
     * The full date <em>and</em> the clock time, unlike every other conference surface: the
     * calendar and the dashboard show days because that is what they are scanned by, and this is
     * the page that answers "what time does it actually start?" It is in the rail rather than the
     * live column, but it is still here at full precision.
     */
    private static final String DAY_AND_TIME = "EEE, MMM d, yyyy 'at' h:mm a";

    /** The far end of a range, where the year is already established by the near end. */
    private static final String DAY_AND_TIME_SHORT = "EEE, MMM d 'at' h:mm a";

    /**
     * A CFP deadline names its zone, and it is the only time on this page that does (Ted,
     * 2026-09-06). The conference's own start and end are read as "when am I there", and the rail
     * says which zone those are in once, underneath both. A deadline is different: it is a hard
     * cutoff set by someone else, and Ted is routinely not in its zone when he is deciding whether
     * he still has time — "5:00 PM" alone is a question rather than an answer, and getting it wrong
     * costs him the submission.
     * <p>
     * {@code zzz} resolves against the instant, not the zone id, so a deadline in January reads CET
     * and one in September CEST. A zone with no short name falls back to an offset, which is still
     * an answer.
     */
    private static final String DEADLINE_WITH_ZONE = "EEE, MMM d, yyyy 'at' h:mm a '('zzz')'";

    private static final String CSS = """
            .page { max-width: 900px; }
            /* The name, and beside it the one badge that is not restated below. */
            .conf-detail-head {
                display: flex; flex-wrap: wrap; align-items: baseline; gap: 0.5rem 0.75rem;
                margin: 0 0 0.25rem;
            }
            .conf-detail-head h1 { margin: 0; }
            .conf-detail-kind {
                color: var(--muted-text); font-size: 0.875rem; margin: 0 0 1.5rem;
            }
            /* Two fixed tracks — the live column and the fact rail — collapsing to one on a narrow
               viewport. Fixed rather than auto-fit on purpose: a row must be in the same place on
               the iPad as on the desktop, and auto-fit moved every panel at every breakpoint. */
            .conf-detail-grid {
                display: grid; grid-template-columns: 2fr 1fr; gap: 2rem; align-items: start;
            }
            @media (max-width: 640px) {
                .conf-detail-grid { grid-template-columns: 1fr; gap: 1.5rem; }
            }
            /* One track of the conference's state. The left edge is the whole colour vocabulary:
               amber waiting, green settled, grey nothing happening. */
            .conf-track {
                border-left: 3px solid var(--border-color);
                padding-left: 0.875rem;
                margin-bottom: 1.5rem;
            }
            .conf-track:last-child { margin-bottom: 0; }
            .conf-track--settled { border-left-color: #166534; }
            .conf-track--waiting { border-left-color: #b45309; }
            .conf-track h2 {
                font-size: 0.6875rem; font-weight: 700; text-transform: uppercase;
                letter-spacing: 0.06em; color: var(--muted-text); margin: 0 0 0.15rem;
            }
            /* The one value each track exists to say. */
            .conf-track-state { font-size: 1.125rem; font-weight: 600; margin: 0; }
            .conf-track-note { color: var(--muted-text); font-size: 0.875rem; margin: 0.15rem 0 0; }
            /* A track's own moves, directly under the state they change. */
            .conf-track-actions {
                display: flex; flex-wrap: wrap; gap: 0.5rem 1.25rem; align-items: baseline;
                margin-top: 0.6rem;
            }
            /* Underlined here, and only here. On the dashboard an action is a nowrap row in a 240px
               cell with no other link near it, and three underlines in that space read as noise; on
               this page it sits four lines above a `.conf-panel-link` in the same column, and two
               links a thumb apart that do not look alike is worse than either treatment. Same
               reasoning as the "Speaker" chip against the calendar's "A Ted Talk" — one fact, two
               surfaces, two densities. */
            .conf-track-actions .conf-action,
            .conf-track-actions .conf-decline { text-decoration: underline; }
            /* The rail: reference, not news. Smaller, quieter, and behind a hairline that separates
               it from the column without boxing it. */
            .conf-rail {
                border-left: 1px solid var(--border-color);
                padding-left: 1.25rem;
                font-size: 0.8125rem;
            }
            @media (max-width: 640px) {
                .conf-rail {
                    border-left: 0; border-top: 1px solid var(--border-color);
                    padding-left: 0; padding-top: 1.25rem;
                }
            }
            .conf-rail h2 {
                font-size: 0.625rem; font-weight: 700; text-transform: uppercase;
                letter-spacing: 0.08em; color: var(--muted-text); margin: 0 0 0.3rem;
            }
            .conf-rail-block { margin: 0 0 1.25rem; }
            .conf-rail-block:last-child { margin-bottom: 0; }
            .conf-rail-lead { font-size: 0.875rem; font-weight: 600; margin: 0; }
            .conf-rail-line { color: var(--muted-text); margin: 0; }
            /* Every link on this page is underlined at rest: these surfaces are exactly where a
               colour-only affordance would disappear, and the iPad has no pointer to reveal one
               (CLAUDE.md, "never have an affordance that relies on :hover").
               One per line, and only as wide as its own words — `width: fit-content` is what keeps
               the underline under the text rather than stretched across the column. */
            .conf-panel-link {
                display: block; width: fit-content; margin-top: 0.4rem;
                color: var(--accent-color); text-decoration: underline;
            }
            .conf-speaker {
                font-size: 0.6875rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;
                padding: 1px 5px; border-radius: 4px; white-space: nowrap;
                border: 1px solid var(--accent-color); color: var(--accent-color);
            }
            """;

    /**
     * @param now captured at the boundary from the injected {@link java.time.Clock}, and used for
     *            exactly one thing: how many days are left on an open CFP. Nothing else on this
     *            page depends on when it is read.
     */
    public static String render(ConferenceDetailView conference, Instant now) {
        return "<!DOCTYPE html>\n" + html(
                Page.head(conference.name(), CSS + ConferenceActions.CSS),
                body(
                        // This page's own path, not "/conferences": marking the list active would
                        // render it as a non-link span, and the nav is how Ted gets back to it.
                        Page.viewNav(Page.NavAudience.OWNER,
                                     "/conferences/" + conference.conferenceId().id()),
                        div().withClass("page").with(
                                header(conference),
                                div().withClass("conf-detail-grid").with(
                                        liveColumn(conference, now),
                                        factRail(conference)))
                )
        ).withLang("en").render();
    }

    /**
     * The name, what kind of thing it is, and the speaking badge when it applies. The commitment
     * chip that used to sit here is gone: the attendance track says the same word two lines below,
     * and the badge is the only mark on this page that is not restated in the column.
     * <p>
     * The kind line stays even though the CFP track largely implies it, because
     * {@code ACCEPTANCE_REQUIRED} is a fact nothing else on the page states until a rejection makes
     * it visible — and by then it is too late to be useful.
     */
    private static DomContent header(ConferenceDetailView conference) {
        DivTag head = div().withClass("conf-detail-head").with(h1(conference.name()));
        if (conference.speaking()) {
            head.with(span("Speaker").withClass("conf-speaker")
                                     .withTitle("Ted is speaking at this one"));
        }
        return each(head,
                p("Conference · " + conference.format().label()).withClass("conf-detail-kind"));
    }

    /**
     * The three tracks that can change while Ted is not looking, in a fixed order so a row is never
     * hunted for. Each carries its own moves, split out of {@link ConferenceActions} by the command
     * they post so the two surfaces still cannot disagree about which are legal.
     */
    private static DomContent liveColumn(ConferenceDetailView conference, Instant now) {
        ConferenceActions.Moves moves = ConferenceActions.movesByAxis(
                conference.conferenceId(), conference.commitment(),
                conference.speakingStatus(), conference.format());
        return div().with(
                attendanceTrack(conference, moves.attendance()),
                talkTrack(conference, moves.talk()),
                cfpTrack(conference, now));
    }

    /**
     * Whether Ted is going, and <strong>why</strong> — the one thing on this page that exists
     * nowhere else in the app. The reason is {@code AttendanceBasis}, which CLAUDE.md keeps off
     * every calendar because it re-states the submission outcome; it is showable here only because
     * the route is OWNER-only.
     */
    private static DomContent attendanceTrack(ConferenceDetailView conference,
                                              List<DomContent> moves) {
        DivTag track = track("Attendance", edgeFor(conference.commitment()),
                             commitmentWord(conference.commitment()));
        String reason = attendanceReason(conference);
        if (reason != null) {
            track.with(p(reason).withClass("conf-track-note"));
        }
        if (conference.commitment() == AttendanceCommitment.NOT_GOING) {
            // Why the whole page offers nothing: the domain refuses every command against a
            // conference Ted is not going to, so the absence is a rule rather than an oversight.
            track.with(p("Going after all means planning it again.").withClass("conf-track-note"));
        }
        return withActions(track, moves);
    }

    private static String edgeFor(AttendanceCommitment commitment) {
        return switch (commitment) {
            case GOING -> "conf-track--settled";
            // Undecided: this is the track waiting on Ted himself.
            case WATCHING -> "conf-track--waiting";
            // Over. Nothing is pending, so nothing is marked.
            case NOT_GOING -> "";
        };
    }

    /**
     * Why Ted is going, or {@code null} where there is nothing to say.
     * <p>
     * <strong>The recorded basis wins, and the acceptance is the fallback</strong> — which is the
     * <em>opposite</em> precedence to {@code ConferenceProgress.speaking()}, deliberately, because
     * the question is different. There the stream outranks the basis because a stale annotation
     * must not contradict what the organizers did; here the question is why Ted is going, and a
     * confirmation is the reason he gave rather than a claim about anyone else.
     * <p>
     * The fallback exists because an acceptance <strong>commits attendance on its own</strong>,
     * writing no confirmation at all — so a conference can be GOING with no basis, and this is the
     * only sentence that explains it. Reading the acceptance first was wrong and shipped that way
     * for about an hour: on a ticket bought <em>before</em> a talk was accepted it said the
     * acceptance "is what committed you", which is false — he was already going, and the talk track
     * beside it already says the talk was accepted.
     */
    private static String attendanceReason(ConferenceDetailView conference) {
        return switch (conference.commitment()) {
            case WATCHING -> undecidedBecause(conference);
            case GOING -> goingBecause(conference);
            case NOT_GOING -> wasGoingBecause(conference);
        };
    }

    /**
     * Nothing is decided yet, and the row says so rather than standing empty — an unexplained state
     * word was what the old Attendance panel offered a watched conference, and it read as missing
     * data. An open invitation is named here because saying yes to it is an <em>attendance</em>
     * move, and this is the row it lands on.
     */
    private static String undecidedBecause(ConferenceDetailView conference) {
        return conference.speakingStatus() == SpeakingStatus.INVITED
                ? "You have been invited to speak. Saying yes is what commits you."
                : "Not decided yet.";
    }

    private static String goingBecause(ConferenceDetailView conference) {
        if (conference.basis() == null) {
            return conference.speakingStatus() == SpeakingStatus.ACCEPTED
                    ? "A submitted talk was accepted, which is what committed you."
                    : null;
        }
        return switch (conference.basis()) {
            case SPEAKING_ACCEPTED -> "You recorded that a talk was accepted.";
            case SPEAKING_INVITED -> "You accepted an invitation to speak.";
            case TICKET_PURCHASED -> "You bought a ticket.";
        };
    }

    /**
     * Why he <em>was</em> going, on a conference he has since declined — the whole reason
     * {@code ConferenceProjector.Tracked} keeps the basis through a decline rather than clearing
     * it, and the reason this page exists for a dropped conference at all
     * ({@code docs/ConferenceDetailAndChangePlan.md} Q3). Past tense, because the fact is: a
     * decline is a later fact about the same conference, not an erasure of the earlier one.
     * <p>
     * This is <strong>not</strong> "why did it drop out" — a rejection that took an
     * {@code ACCEPTANCE_REQUIRED} conference with it is the talk track's sentence, and repeating it
     * here would be the page saying one thing twice. A conference declined while merely watched has
     * no basis and says nothing.
     */
    private static String wasGoingBecause(ConferenceDetailView conference) {
        if (conference.basis() == null) {
            return conference.speakingStatus() == SpeakingStatus.ACCEPTED
                    ? "A submitted talk was accepted, which is what had committed you."
                    : null;
        }
        return switch (conference.basis()) {
            case SPEAKING_ACCEPTED -> "You were going because a talk was accepted.";
            case SPEAKING_INVITED -> "You were going on an invitation to speak.";
            case TICKET_PURCHASED -> "You had bought a ticket.";
        };
    }

    /**
     * Where the talk stands, in the words the moves record it with, plus what that means for
     * attending — which is the coupling between the two axes and the thing a bare status word
     * leaves Ted to work out.
     */
    private static DomContent talkTrack(ConferenceDetailView conference, List<DomContent> moves) {
        DivTag track = track("Talk", talkEdge(conference.speakingStatus()), talkState(conference))
                .with(p(talkMeaning(conference)).withClass("conf-track-note"));
        return withActions(track, moves);
    }

    /**
     * Waiting means waiting on <em>someone</em>: the organizers hold a submitted talk, and an
     * invitation is an offer holding for an answer. Everything the stream has already answered is
     * settled, and a conference with nothing out has nothing happening on this track at all.
     */
    private static String talkEdge(SpeakingStatus status) {
        return switch (status) {
            case SUBMITTED, INVITED -> "conf-track--waiting";
            case ACCEPTED, REJECTED, WITHDRAWN -> "conf-track--settled";
            case NOT_SPEAKING -> "";
        };
    }

    /**
     * Exhaustive, so a new {@link SpeakingStatus} cannot be added without deciding what this page
     * says about it.
     */
    private static String talkState(ConferenceDetailView conference) {
        return switch (conference.speakingStatus()) {
            case NOT_SPEAKING -> "Nothing submitted";
            case SUBMITTED -> "Submitted";
            case ACCEPTED -> "Accepted";
            case REJECTED -> "Rejected";
            case WITHDRAWN -> "Withdrawn";
            case INVITED -> "Invited to speak";
        };
    }

    private static String talkMeaning(ConferenceDetailView conference) {
        return switch (conference.speakingStatus()) {
            case NOT_SPEAKING -> conference.format() == ConferenceFormat.OPEN_SPACE
                    ? "There is nothing to submit to."
                    : "No talk submitted, and no invitation.";
            case SUBMITTED -> "Waiting to hear. The organizers hold this one.";
            case ACCEPTED -> "You are speaking, and that is what commits you to going.";
            // The one place the format changes what an outcome means, so it is said rather than
            // left to be inferred from whether the conference is still on the calendar.
            case REJECTED -> conference.format() == ConferenceFormat.ACCEPTANCE_REQUIRED
                    ? "Acceptance was the way in, so this one dropped off the calendar."
                    : "Going is still a decision to make.";
            case WITHDRAWN -> "You pulled it. Whether you still attend is a separate question.";
            // Answering it is an attendance move, so it is offered on the track above rather than
            // here — this sentence deliberately does not say "say yes below".
            case INVITED -> conference.commitment() == AttendanceCommitment.GOING
                    ? "They asked, and you said yes."
                    : "An open offer, still unanswered.";
        };
    }

    /**
     * <strong>Four different absences, four different sentences</strong> (D3). {@code null} means
     * only "no CFP recorded" — never "this conference has no CFP", which is what the format says —
     * and telling those two apart is most of this track's job: one is a task for Ted, the other is
     * nothing at all.
     * <p>
     * <strong>The countdown leads and the timestamp follows</strong> (Ted, 2026-09-06): "closes in
     * 8 days" is the fact he acts on, and "Mon, Sep 14, 2026 at 5:00 PM" is what he checks once he
     * has decided to. Shipped the other way round, which put the reference value in the emphasised
     * slot and the answer in the muted one — the same fault as the panel layout this page replaced,
     * one track down. A closed CFP has no countdown to promote, so it leads with "Closed" and keeps
     * the timestamp underneath in the same slot, which is what keeps the column scannable: every
     * state line is a short phrase.
     * <p>
     * A dropped conference gets no links: the domain refuses to record a CFP against a conference
     * Ted declined, so a link there would lead somewhere that says no. The deadline itself stays,
     * as a record — and where there is no deadline either, the track drops the task sentence with
     * the link, because a prompt to go and find a date is only a prompt if the page it points at
     * would accept the answer.
     */
    private static DomContent cfpTrack(ConferenceDetailView conference, Instant now) {
        if (conference.format() == ConferenceFormat.OPEN_SPACE) {
            return openSpaceCfpTrack(conference);
        }
        boolean dropped = conference.commitment() == AttendanceCommitment.NOT_GOING;
        String cfpPath = "/conferences/" + conference.conferenceId().id() + "/cfp";
        if (conference.cfpClosesOn() == null) {
            // The same two absences one level down. On a live conference this is a task; on a
            // dropped one it is nothing at all.
            if (dropped) {
                return track("Call for papers", "", "Not recorded")
                        .with(p("A CFP cannot be recorded for a conference you are not going to.")
                                      .withClass("conf-track-note"));
            }
            return track("Call for papers", "conf-track--waiting", "Not recorded")
                    .with(p("Find the closing date and record it, so a reminder can be set.")
                                  .withClass("conf-track-note"))
                    .with(a("Record when this CFP closes")
                                  .withClass("conf-panel-link").withHref(cfpPath));
        }
        boolean open = conference.cfpClosesOn().utc().isAfter(now);
        if (dropped) {
            // A dropped conference's deadline is a record, not a countdown: he is not going, so
            // how long is left is not a fact about anything he could do with the time. It leads
            // with the date, and wears no colour, because nothing here is waiting on anyone.
            return track("Call for papers", "",
                         span(open ? "Open until " : "Closed "),
                         ZonedTimeTag.render(conference.cfpClosesOn(), DEADLINE_WITH_ZONE));
        }
        DivTag track = open
                ? track("Call for papers", "conf-track--waiting", daysLeft(conference, now))
                        .with(deadlineNote("Open until ", conference))
                : track("Call for papers", "", "Closed")
                        .with(deadlineNote("Was open until ", conference));
        // The way out to wherever the talk is submitted, on the same track as the deadline because
        // it is the same fact: this CFP is open, it closes then, you submit there. Only while
        // submitting is still on the table — offering it after the window shut, or after they said
        // no, would be the page arguing with itself.
        if (open && !conference.cfpSubmissionUrl().isBlank() && stillSubmittable(conference)) {
            track.with(a("Submit a talk").withClass("conf-panel-link")
                                         .withTitle("Open the CFP's submission page")
                                         .withTarget("_blank")
                                         .withRel("noopener")
                                         .withHref(conference.cfpSubmissionUrl()));
        }
        return track.with(a("Change the recorded deadline").withClass("conf-panel-link")
                                                           .withHref(cfpPath));
    }

    /**
     * An open space chooses its sessions on the day, so there is nothing to submit to — <em>unless</em>
     * a deadline was recorded before the conference was marked open-space, which is the pending
     * SoCraTes/PLoP re-marking in {@code docs/Backlog.md}.
     * <p>
     * <strong>A leftover deadline is said out loud rather than covered over.</strong> Printing
     * "None" on top of a stored {@code CfpOpened} would be the page hiding a row that is still
     * live: {@code CfpDeadlineSource} does not filter by format, so that deadline is still putting
     * three alarms on Ted's phone for a CFP this conference does not have. No link goes with it,
     * because {@code OpenCfpCommand} refuses a CFP on an open space and the link would lead
     * somewhere that says no — clearing one is in {@code docs/Cleanup_Tasks.md}.
     */
    private static DomContent openSpaceCfpTrack(ConferenceDetailView conference) {
        if (conference.cfpClosesOn() == null) {
            return track("Call for papers", "", "None")
                    .with(p("Sessions are chosen on the day.").withClass("conf-track-note"));
        }
        return track("Call for papers", "conf-track--waiting", "None")
                .with(p().withClass("conf-track-note").with(
                        span("Sessions are chosen on the day, but a deadline is still recorded for "),
                        ZonedTimeTag.render(conference.cfpClosesOn(), DEADLINE_WITH_ZONE)))
                .with(p("It is still setting a reminder, and there is no way to clear it yet.")
                              .withClass("conf-track-note"));
    }

    /** Nothing is out, or what was out has been pulled — which puts submitting back on the table. */
    private static boolean stillSubmittable(ConferenceDetailView conference) {
        return conference.speakingStatus() == SpeakingStatus.NOT_SPEAKING
                || conference.speakingStatus() == SpeakingStatus.WITHDRAWN;
    }

    /** The exact deadline, demoted under the countdown — see {@link #cfpTrack}. */
    private static DomContent deadlineNote(String preamble, ConferenceDetailView conference) {
        return p().withClass("conf-track-note").with(
                span(preamble),
                ZonedTimeTag.render(conference.cfpClosesOn(), DEADLINE_WITH_ZONE),
                text("."));
    }

    /**
     * How long is left, counted in venue-local days rather than from the raw instants: "closes
     * tomorrow" is a claim about the calendar Ted reads, and a deadline 20 hours away can fall
     * either side of that line depending on the time of day.
     * <p>
     * No trailing full stop, unlike every other sentence on the page: this is the track's state
     * line rather than a note about it, and it is read down the column beside "Going" and
     * "Submitted".
     */
    private static String daysLeft(ConferenceDetailView conference, Instant now) {
        LocalDate today = now.atZone(conference.cfpClosesOn().zone()).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, conference.cfpClosesOn().atEntryZone().toLocalDate());
        if (days <= 0) {
            return "Closes today";
        }
        return days == 1 ? "Closes tomorrow" : "Closes in " + days + " days";
    }

    /**
     * When and where, set small and behind a hairline. It is reference rather than news — a venue
     * address has not changed since Ted typed it — so it is deliberately the quietest thing on the
     * page. It still carries the exact clock times, because this is the only surface that does: the
     * calendar and the dashboard both show days.
     */
    private static DomContent factRail(ConferenceDetailView conference) {
        long days = ChronoUnit.DAYS.between(
                conference.startDate().localDateTime().toLocalDate(),
                conference.endDate().localDateTime().toLocalDate()) + 1;
        DivTag rail = div().withClass("conf-rail").with(
                div().withClass("conf-rail-block").with(
                        h2("When"),
                        div().withClass("conf-rail-lead").with(
                                ZonedTimeTag.render(conference.startDate(), DAY_AND_TIME)),
                        p().withClass("conf-rail-line").with(
                                span("through "),
                                ZonedTimeTag.render(conference.endDate(), DAY_AND_TIME_SHORT)),
                        p(dayCount(days) + " · times in " + conference.startDate().zone().getId())
                                .withClass("conf-rail-line")),
                wherePanel(conference));
        if (!conference.infoUrl().isBlank()) {
            rail.with(div().withClass("conf-rail-block").with(
                    h2("Elsewhere"),
                    a("Conference website").withClass("conf-panel-link")
                                           .withTitle("Open the conference's own page")
                                           .withTarget("_blank")
                                           .withRel("noopener")
                                           .withHref(conference.infoUrl())));
        }
        return rail;
    }

    private static String dayCount(long days) {
        return days == 1 ? "1 day" : days + " days";
    }

    /**
     * The venue and its street address. The way out to the conference's own site is its own rail
     * block rather than a line here: it is the only outbound link on the page, and burying it under
     * an address is how it went unfound on the dashboard.
     */
    private static DomContent wherePanel(ConferenceDetailView conference) {
        Address address = conference.venueAddress();
        DivTag block = div().withClass("conf-rail-block").with(h2("Where"));
        if (!conference.venueName().isBlank()) {
            block.with(div(conference.venueName()).withClass("conf-rail-lead"));
        }
        if (!address.street().isBlank()) {
            block.with(p(address.street()).withClass("conf-rail-line"));
        }
        return block.with(p(cityLine(conference)).withClass("conf-rail-line"));
    }

    /**
     * A country is {@code ""} when absent, so the city stands alone rather than trailing a comma.
     * {@code isBlank}, not {@code isEmpty}, per CLAUDE.md: optional text is guarded on blankness so
     * a value of {@code " "} reads as absent. {@code Address}'s compact constructor trims, so the
     * two agree today — which is exactly why the weaker one would go unnoticed.
     */
    private static String cityLine(ConferenceDetailView conference) {
        return conference.country().isBlank()
                ? conference.city()
                : conference.city() + ", " + conference.country();
    }

    /**
     * A track's own moves. An empty set adds nothing at all rather than an empty container — where
     * a state machine decides what a surface offers, an inapplicable move is absent rather than
     * greyed (CLAUDE.md), and that goes for the box it would have sat in.
     */
    private static DivTag withActions(DivTag track, List<DomContent> moves) {
        return moves.isEmpty()
                ? track
                : track.with(div().withClass("conf-track-actions").with(moves));
    }

    private static DivTag track(String heading, String edgeClass, String state) {
        return track(heading, edgeClass, text(state));
    }

    private static DivTag track(String heading, String edgeClass, DomContent... state) {
        return div().withClass(("conf-track " + edgeClass).trim())
                    .with(h2(heading))
                    .with(div().withClass("conf-track-state").with(state));
    }

    /** The chip's word as the track's own state line. */
    private static String commitmentWord(AttendanceCommitment commitment) {
        return switch (commitment) {
            case WATCHING -> "Maybe";
            case GOING -> "Going";
            case NOT_GOING -> "Not going";
        };
    }
}

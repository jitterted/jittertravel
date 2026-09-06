package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.SpeakingStatus;
import j2html.tags.DomContent;

import java.util.ArrayList;
import java.util.List;

import static j2html.TagCreator.a;

/**
 * The moves one conference offers right now, in one place because two surfaces offer them: the
 * {@code /conferences} dashboard row and the conference's own detail page. It lived on
 * {@code ConferencesRenderer} until the detail page arrived and made it a second copy waiting to
 * drift — and drifting here would mean two pages disagreeing about which transitions are legal.
 * <p>
 * <strong>The state machine decides what is offered, and an inapplicable move is absent rather than
 * greyed.</strong> That is the deliberate exception to CLAUDE.md's "an unavailable action is shown
 * disabled, with the reason", taken with Ted on 2026-08-22: that rule is about an action that has
 * been or will be available to this viewer, while most moves here are not merely unavailable-for-now
 * but meaningless — "Accepted" on a conference nothing was submitted to names an event that could
 * never be true. Recording the CFP deadline is deliberately <em>not</em> a move: a closing date is a
 * property of the conference, and keeping it out is what lets every state fit in three actions.
 * <p>
 * <strong>Two axes, and committing does not close the other one.</strong> A conference moves on an
 * attendance axis and a talk axis independently — {@code ConferenceProgress.submitted()} and
 * {@code invited()} both carry the commitment through untouched — so {@code GOING} is not a terminal
 * state. Ted buys an early-bird ticket, then submits; the organizers ask him to keynote something he
 * was already attending. The talk-side moves are therefore chosen by {@code speakingStatus} alone,
 * and {@code commitment} only decides whether the attendance-side ones (Ticket Bought, Invitation
 * Accepted, Decline) are still worth offering. Reading it the other way round — an early return on
 * {@code GOING} that offered nothing but Decline — is how this shipped on 2026-09-06 and it stranded
 * every conference Ted had a ticket for and a talk out to: no surface could record the acceptance,
 * and the badge for a talk he actually gave could never be earned. See
 * {@code docs/ConferenceStateMachine.md}.
 * <p>
 * <strong>No state offers more than three</strong>, which is what keeps these links rather than a
 * menu (CLAUDE.md's dropdown rule) and what lets the dashboard's Actions column be a fixed 240px.
 * {@code ConferenceActionsTest} pins the count; adding a fourth breaks both. The one place that
 * budget actually costs a move is {@code SUBMITTED}, which drops Decline — see the comment there.
 * <p>
 * Every action is a link to a page that hosts the actual POST form, never a POST from here — this
 * is j2html, and the project keeps POST forms in Thymeleaf so renderers stay clear of Spring's CSRF
 * plumbing. The link carries the choice, so the page opens with it already selected and the second
 * click is a confirmation rather than a decision.
 * <p>
 * The labels are past tense, because that is what this app does: it records what has already
 * happened in the world. "Ticket Bought", not "Buy ticket" (Ted, 2026-08-22).
 */
public final class ConferenceActions {

    /**
     * The two link styles, shared by both callers so a move looks the same wherever it is offered.
     * Layout is <em>not</em> here: the dashboard packs these into a fixed nowrap column and the
     * detail page gives them a row of their own, which is each page's business.
     */
    public static final String CSS = """
            /* Action labels are nowrap units, and the dashboard's 240px column is budgeted for
               three of them — the reason they are one or two short words. */
            .conf-action { color: var(--accent-color); text-decoration: none; white-space: nowrap; font-size: 0.8125rem; }
            .conf-action:hover { text-decoration: underline; }
            .conf-decline { color: #b00; text-decoration: none; white-space: nowrap; font-size: 0.8125rem; }
            .conf-decline:hover { text-decoration: underline; }
            """;

    private ConferenceActions() {
    }

    /**
     * The legal moves split by the axis each one moves — the talk's, and attendance's. The detail
     * page lays its state out as one track per axis and puts each move beside the state it changes,
     * so it needs to know which is which; the dashboard flattens them again through {@link #links}.
     * <p>
     * <strong>Classified by the command a move posts, not by what it is about.</strong> The
     * {@code /talk} moves are the talk's; {@code /confirm} and {@code /decline} are attendance's.
     * That is why "Invitation Accepted" is an <em>attendance</em> move despite being talk-shaped: it
     * writes {@code ConferenceAttendanceConfirmed}, so the row it changes is the row it belongs on,
     * and it stays beside the Decline that is the other answer to the same offer.
     */
    public record Moves(List<DomContent> talk, List<DomContent> attendance) {

        /** In the order both surfaces offer them: what happened to the talk, then what Ted decides. */
        List<DomContent> flattened() {
            List<DomContent> all = new ArrayList<>(talk);
            all.addAll(attendance);
            return List.copyOf(all);
        }
    }

    /**
     * The legal moves from where this conference stands, in the order they are offered. The caller
     * wraps them: the two surfaces lay them out differently and neither layout belongs here.
     * <p>
     * Composed from {@link #movesByAxis} rather than written out again, so the flat list a dashboard
     * row shows and the split list the detail page shows cannot disagree about what is legal.
     */
    public static List<DomContent> links(ConferenceId conferenceId,
                                         AttendanceCommitment commitment,
                                         SpeakingStatus speakingStatus,
                                         ConferenceFormat format) {
        return movesByAxis(conferenceId, commitment, speakingStatus, format).flattened();
    }

    public static Moves movesByAxis(ConferenceId conferenceId,
                                    AttendanceCommitment commitment,
                                    SpeakingStatus speakingStatus,
                                    ConferenceFormat format) {
        String base = "/conferences/" + conferenceId.id();
        List<DomContent> talk = new ArrayList<>();
        List<DomContent> attendance = new ArrayList<>();
        // A dropped conference has no live action: the domain refuses every command against a
        // declined conference, so there is nothing here that could be triggered — not even in a
        // disabled form, which would promise a capability that does not exist. Going after all
        // means planning it again.
        if (commitment == AttendanceCommitment.NOT_GOING) {
            return new Moves(List.of(), List.of());
        }
        boolean committed = commitment == AttendanceCommitment.GOING;
        switch (speakingStatus) {
            // Submitted and waiting: the only moves are what the organizers say, and pulling it.
            // Decline is deliberately absent — three is the budget, and while a talk is out with
            // the organizers, not going means pulling it first. That is true whether or not a
            // ticket is already bought.
            case SUBMITTED -> {
                talk.add(talkLink(base, TalkOutcome.ACCEPTED, "Accepted",
                        committed ? "They said yes."
                                  : "They said yes. This also records that you are going."));
                talk.add(talkLink(base, TalkOutcome.REJECTED, "Rejected", "They said no."));
                talk.add(talkLink(base, TalkOutcome.WITHDRAWN, "Withdrawn", "You pulled it."));
            }
            // An open offer. Saying yes is a confirmation naming the invitation as the reason,
            // which is what separates speaking there from merely attending — so it is still the
            // move on a conference Ted was already attending on a bought ticket, where it is what
            // turns an attendee into a speaker.
            case INVITED -> {
                attendance.add(confirmLink(base, AttendanceBasis.SPEAKING_INVITED, "Invitation Accepted",
                        committed ? "Say yes. You were already going; now you are speaking."
                                  : "Say yes: you are going, and you are speaking."));
                attendance.add(declineLink(base));
            }
            // Turned down. Buying a ticket anyway is the go-anyway case, and only where he is not
            // already going: on an ACCEPTANCE_REQUIRED conference a rejection drops it outright,
            // so this state is a CALL_FOR_PAPERS one.
            case REJECTED -> {
                if (!committed) {
                    attendance.add(confirmLink(base, AttendanceBasis.TICKET_PURCHASED, "Ticket Bought",
                            "Going as an attendee after all."));
                }
                attendance.add(declineLink(base));
            }
            // Nothing outstanding: submitting is on the table again wherever there is a CFP, and
            // that is independent of attending — a ticket bought early does not stop him
            // submitting, and the CFP panel offers the submission page in exactly these two states.
            case NOT_SPEAKING, WITHDRAWN -> {
                if (format != ConferenceFormat.OPEN_SPACE) {
                    talk.add(talkLink(base, TalkOutcome.SUBMITTED, "Submitted",
                            "Record that you submitted a talk."));
                }
                if (!committed) {
                    attendance.add(confirmLink(base, AttendanceBasis.TICKET_PURCHASED, "Ticket Bought",
                            "Going as an attendee."));
                }
                attendance.add(declineLink(base));
            }
            // A talk in the program. Accepting commits attendance on its own, so this state is
            // always GOING; pulling the talk is the one talk-side move left, and it changes
            // nothing about attending, which is why Decline is still beside it.
            case ACCEPTED -> {
                talk.add(talkLink(base, TalkOutcome.WITHDRAWN, "Withdrawn",
                        "Record that you pulled your talk. You are still going."));
                attendance.add(declineLink(base));
            }
        }
        return new Moves(List.copyOf(talk), List.copyOf(attendance));
    }

    private static DomContent talkLink(String base, TalkOutcome outcome, String label, String title) {
        return a(label).withClass("conf-action")
                       .withTitle(title)
                       .withHref(base + "/talk?outcome=" + outcome.name());
    }

    private static DomContent confirmLink(String base, AttendanceBasis basis, String label, String title) {
        return a(label).withClass("conf-action")
                       .withTitle(title)
                       .withHref(base + "/confirm?basis=" + basis.name());
    }

    private static DomContent declineLink(String base) {
        return a("Decline").withClass("conf-decline")
                           .withTitle("Record that you are not going.")
                           .withHref(base + "/decline");
    }
}

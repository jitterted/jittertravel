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
 * <strong>It is a reference surface, and that decides what is on it</strong>
 * ({@code docs/ConferenceDetailAndChangePlan.md} D2). CLAUDE.md splits <em>recording</em> surfaces
 * (report an act already done) from <em>decision-support</em> ones (carry what makes a choice
 * answerable); this is neither — it is where Ted re-reads what he already recorded. So it shows what
 * identifies the conference and what state it is in, and carries nothing whose job is to help him
 * decide: the deciding happens on the action pages these panels link to.
 * <p>
 * <strong>OWNER-only, and one panel depends on it.</strong> {@code /conferences/*} is
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
     * the page that answers "what time does it actually start?"
     */
    private static final String DAY_AND_TIME = "EEE, MMM d, yyyy 'at' h:mm a";

    private static final String CSS = """
            .page { max-width: 900px; }
            /* The name, and beside it the two chips that answer "am I going, and am I speaking?"
               — the same question the dashboard's Going? column answers, in the same words. They
               wrap under the name on a narrow viewport rather than squeezing it. */
            .conf-detail-head {
                display: flex; flex-wrap: wrap; align-items: baseline; gap: 0.5rem 0.75rem;
                margin: 0 0 0.25rem;
            }
            .conf-detail-head h1 { margin: 0; }
            .conf-detail-kind {
                color: var(--muted-text); font-size: 0.875rem; margin: 0 0 1.25rem;
            }
            /* Panels in a grid that becomes one column when there is no room for two. auto-fit
               with a 260px floor rather than a media query: the page is read on an iPad in both
               orientations and the breakpoint is wherever a panel stops fitting. */
            .conf-panels {
                display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
                gap: 1rem;
            }
            .conf-panel {
                background: var(--surface, #fff); border: 1px solid var(--border-color);
                border-radius: 8px; padding: 0.875rem 1rem;
            }
            .conf-panel h2 {
                font-size: 0.6875rem; font-weight: 700; text-transform: uppercase;
                letter-spacing: 0.06em; color: var(--muted-text); margin: 0 0 0.5rem;
            }
            .conf-panel-line { margin: 0 0 0.2rem; }
            /* The one value each panel exists to say, so the eye lands on it before the
               qualifications under it. */
            .conf-panel-lead { font-weight: 600; }
            .conf-panel-note { color: var(--muted-text); font-size: 0.8125rem; margin: 0.35rem 0 0; }
            /* Every link on this page is underlined at rest: the muted panels are exactly where a
               colour-only affordance would disappear, and the iPad has no pointer to reveal one
               (CLAUDE.md, "never have an affordance that relies on :hover").
               One per line, and only as wide as its own words: the CFP panel can carry two, and
               inline-block ran them together into "Submit a talkChange the recorded deadline" with
               no gap at all. `width: fit-content` is what keeps the underline under the text
               rather than stretched across the panel. */
            .conf-panel-link {
                display: block; width: fit-content; margin-top: 0.4rem;
                color: var(--accent-color); text-decoration: underline;
            }
            /* The actions sit in a band of their own under the panels rather than inside one:
               they are moves on the conference as a whole, not facts about one of its parts. */
            .conf-detail-actions {
                display: flex; flex-wrap: wrap; gap: 0.75rem 1.25rem; align-items: center;
                margin-top: 1.25rem; padding: 0.75rem 1rem;
                border: 1px solid var(--border-color); border-radius: 8px;
                background: var(--header-bg);
            }
            .conf-detail-actions h2 {
                font-size: 0.6875rem; font-weight: 700; text-transform: uppercase;
                letter-spacing: 0.06em; color: var(--muted-text); margin: 0;
            }
            /* The same two words the dashboard and the public calendar use, so all three agree. */
            .conf-commitment {
                font-size: 0.6875rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;
                padding: 2px 6px; border-radius: 4px; white-space: nowrap;
            }
            .conf-commitment--watching { background: #b45309; color: #ffffff; }
            .conf-commitment--going { background: #166534; color: #ffffff; }
            .conf-commitment--dropped {
                background: var(--header-bg); color: var(--muted-text);
                border: 1px solid var(--border-color); padding: 1px 5px;
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
                                div().withClass("conf-panels").with(
                                        whenPanel(conference),
                                        wherePanel(conference),
                                        attendancePanel(conference),
                                        cfpPanel(conference, now),
                                        talkPanel(conference)),
                                actions(conference))
                )
        ).withLang("en").render();
    }

    /**
     * The name, the two chips, and one line saying what kind of thing this is. The chips are up
     * here rather than in the attendance panel because they are what the page is opened to check;
     * the panel below says the same thing in a sentence and adds the reason.
     */
    private static DomContent header(ConferenceDetailView conference) {
        DivTag head = div().withClass("conf-detail-head").with(
                h1(conference.name()),
                commitmentChip(conference.commitment()));
        if (conference.speaking()) {
            head.with(span("Speaker").withClass("conf-speaker")
                                     .withTitle("Ted is speaking at this one"));
        }
        return each(head,
                p("Conference · " + conference.format().label()).withClass("conf-detail-kind"));
    }

    /**
     * Both ends in the venue's own zone, and the zone said out loud. A conference is entered in one
     * zone and read in another often enough that "9:00 AM" alone is a question rather than an
     * answer, and this page is where the exact times live — the calendar and the dashboard both
     * show days only.
     */
    private static DomContent whenPanel(ConferenceDetailView conference) {
        long days = ChronoUnit.DAYS.between(
                conference.startDate().localDateTime().toLocalDate(),
                conference.endDate().localDateTime().toLocalDate()) + 1;
        return panel("When",
                div().withClass("conf-panel-line conf-panel-lead").with(
                        ZonedTimeTag.render(conference.startDate(), DAY_AND_TIME)),
                div().withClass("conf-panel-line").with(
                        span("through "),
                        ZonedTimeTag.render(conference.endDate(), DAY_AND_TIME)),
                p(dayCount(days) + " · times in " + conference.startDate().zone().getId())
                        .withClass("conf-panel-note"));
    }

    private static String dayCount(long days) {
        return days == 1 ? "1 day" : days + " days";
    }

    /**
     * The venue, its street address, and the way out to the conference's own site — which is the
     * only place that link now lives for the owner, the dashboard's name having been given to this
     * page (Ted, 2026-09-04). External, so it opens in a new tab, like every outbound link in the
     * app.
     */
    private static DomContent wherePanel(ConferenceDetailView conference) {
        Address address = conference.venueAddress();
        DivTag panel = panel("Where");
        if (!conference.venueName().isBlank()) {
            panel.with(div(conference.venueName()).withClass("conf-panel-line conf-panel-lead"));
        }
        if (!address.street().isBlank()) {
            panel.with(div(address.street()).withClass("conf-panel-line"));
        }
        panel.with(div(cityLine(conference)).withClass("conf-panel-line"));
        if (!conference.infoUrl().isBlank()) {
            panel.with(a("Conference website").withClass("conf-panel-link")
                                              .withTitle("Open the conference's own page")
                                              .withTarget("_blank")
                                              .withRel("noopener")
                                              .withHref(conference.infoUrl()));
        }
        return panel;
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
     * Whether Ted is going, and <strong>why</strong> — the one thing on this page that exists
     * nowhere else in the app. The reason is {@code AttendanceBasis}, which CLAUDE.md keeps off
     * every calendar because it re-states the submission outcome; it is showable here only because
     * the route is OWNER-only.
     */
    private static DomContent attendancePanel(ConferenceDetailView conference) {
        DivTag panel = panel("Attendance",
                div(commitmentWord(conference.commitment()))
                        .withClass("conf-panel-line conf-panel-lead"));
        String reason = attendanceReason(conference);
        if (reason != null) {
            panel.with(p(reason).withClass("conf-panel-note"));
        }
        return panel;
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
     * acceptance "is what committed you", which is false — he was already going, and the Talk panel
     * beside it already says the talk was accepted.
     * <p>
     * A conference committed before confirmations carried a basis has neither, and says nothing
     * rather than guessing.
     */
    private static String attendanceReason(ConferenceDetailView conference) {
        return switch (conference.commitment()) {
            // Nothing has been decided, so there is no reason to give yet.
            case WATCHING -> null;
            case GOING -> goingBecause(conference);
            case NOT_GOING -> wasGoingBecause(conference);
        };
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
     * {@code ACCEPTANCE_REQUIRED} conference with it is the Talk panel's sentence, and repeating it
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
     * <strong>Four different absences, four different sentences</strong> (D3). {@code null} means
     * only "no CFP recorded" — never "this conference has no CFP", which is what the format says —
     * and telling those two apart is most of this panel's job: one is a task for Ted, the other is
     * nothing at all.
     * <p>
     * A dropped conference gets no links: the domain refuses to record a CFP against a conference
     * Ted declined, so a link there would lead somewhere that says no. The deadline itself stays,
     * as a record — and where there is no deadline either, the panel drops the task sentence with
     * the link, because a prompt to go and find a date is only a prompt if the page it points at
     * would accept the answer.
     */
    private static DomContent cfpPanel(ConferenceDetailView conference, Instant now) {
        if (conference.format() == ConferenceFormat.OPEN_SPACE) {
            return openSpaceCfpPanel(conference);
        }
        boolean dropped = conference.commitment() == AttendanceCommitment.NOT_GOING;
        String cfpPath = "/conferences/" + conference.conferenceId().id() + "/cfp";
        if (conference.cfpClosesOn() == null) {
            // The same two absences one level down. On a live conference this is a task; on a
            // dropped one it is nothing at all, because the domain refuses to record a CFP against
            // a conference Ted is not going to — so asking him to go and find the date would be
            // asking for work the next page would then refuse.
            if (dropped) {
                return panel("Call for papers",
                        div("Not recorded").withClass("conf-panel-line conf-panel-lead"),
                        p("A CFP cannot be recorded for a conference you are not going to.")
                                .withClass("conf-panel-note"));
            }
            return panel("Call for papers",
                    div("Not recorded").withClass("conf-panel-line conf-panel-lead"),
                    p("Find the closing date and record it, so a reminder can be set.")
                            .withClass("conf-panel-note"))
                    .with(a("Record when this CFP closes")
                                  .withClass("conf-panel-link").withHref(cfpPath));
        }
        boolean open = conference.cfpClosesOn().utc().isAfter(now);
        DivTag panel = panel("Call for papers",
                div().withClass("conf-panel-line conf-panel-lead").with(
                        span(open ? "Open until " : "Closed "),
                        ZonedTimeTag.render(conference.cfpClosesOn(), DAY_AND_TIME)));
        if (open) {
            panel.with(p(daysLeft(conference, now)).withClass("conf-panel-note"));
        }
        if (dropped) {
            return panel;
        }
        // The way out to wherever the talk is submitted, on the same panel as the deadline because
        // it is the same fact: this CFP is open, it closes then, you submit there. Only while
        // submitting is still on the table — offering it after the window shut, or after they said
        // no, would be the page arguing with itself.
        if (open && !conference.cfpSubmissionUrl().isBlank() && stillSubmittable(conference)) {
            panel.with(a("Submit a talk").withClass("conf-panel-link")
                                         .withTitle("Open the CFP's submission page")
                                         .withTarget("_blank")
                                         .withRel("noopener")
                                         .withHref(conference.cfpSubmissionUrl()));
        }
        return panel.with(a("Change the recorded deadline").withClass("conf-panel-link")
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
    private static DomContent openSpaceCfpPanel(ConferenceDetailView conference) {
        if (conference.cfpClosesOn() == null) {
            return panel("Call for papers",
                    div("None").withClass("conf-panel-line conf-panel-lead"),
                    p("Sessions are chosen on the day.").withClass("conf-panel-note"));
        }
        return panel("Call for papers",
                div("None").withClass("conf-panel-line conf-panel-lead"),
                div().withClass("conf-panel-line").with(
                        span("Sessions are chosen on the day, but a deadline is still recorded for "),
                        ZonedTimeTag.render(conference.cfpClosesOn(), DAY_AND_TIME)),
                p("It is still setting a reminder, and there is no way to clear it yet.")
                        .withClass("conf-panel-note"));
    }

    /** Nothing is out, or what was out has been pulled — which puts submitting back on the table. */
    private static boolean stillSubmittable(ConferenceDetailView conference) {
        return conference.speakingStatus() == SpeakingStatus.NOT_SPEAKING
                || conference.speakingStatus() == SpeakingStatus.WITHDRAWN;
    }

    /**
     * How long is left, counted in venue-local days rather than from the raw instants: "closes
     * tomorrow" is a claim about the calendar Ted reads, and a deadline 20 hours away can fall
     * either side of that line depending on the time of day.
     */
    private static String daysLeft(ConferenceDetailView conference, Instant now) {
        LocalDate today = now.atZone(conference.cfpClosesOn().zone()).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, conference.cfpClosesOn().atEntryZone().toLocalDate());
        if (days <= 0) {
            return "Closes today.";
        }
        return days == 1 ? "Closes tomorrow." : "Closes in " + days + " days.";
    }

    /**
     * Where the talk stands, in the words the actions record it with, plus what that means for
     * attending — which is the coupling between the two axes and the thing a bare status word
     * leaves Ted to work out.
     */
    private static DomContent talkPanel(ConferenceDetailView conference) {
        return panel("Talk",
                div(talkState(conference)).withClass("conf-panel-line conf-panel-lead"),
                p(talkMeaning(conference)).withClass("conf-panel-note"));
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
            case INVITED -> conference.commitment() == AttendanceCommitment.GOING
                    ? "They asked, and you said yes."
                    : "An open offer. Say yes, or decline.";
        };
    }

    /**
     * The same moves the dashboard row offers, from {@link ConferenceActions} so the two surfaces
     * cannot disagree about which are legal. A dropped conference offers none, and the band says so
     * rather than rendering an empty box.
     */
    private static DomContent actions(ConferenceDetailView conference) {
        List<DomContent> links = ConferenceActions.links(
                conference.conferenceId(), conference.commitment(),
                conference.speakingStatus(), conference.format());
        DivTag band = div().withClass("conf-detail-actions").with(h2("Record"));
        if (links.isEmpty()) {
            return band.with(span("Nothing to record — you are not going to this one.")
                                     .withClass("conf-panel-note"));
        }
        return band.with(links);
    }

    private static DivTag panel(String heading, DomContent... content) {
        return div().withClass("conf-panel").with(h2(heading)).with(content);
    }

    private static DomContent commitmentChip(AttendanceCommitment commitment) {
        return switch (commitment) {
            case WATCHING -> span("Maybe").withClass("conf-commitment conf-commitment--watching");
            case GOING -> span("Going").withClass("conf-commitment conf-commitment--going");
            case NOT_GOING -> span("Not going").withClass("conf-commitment conf-commitment--dropped");
        };
    }

    /** The chip's word as a sentence's subject, for the panel that explains it. */
    private static String commitmentWord(AttendanceCommitment commitment) {
        return switch (commitment) {
            case WATCHING -> "Maybe";
            case GOING -> "Going";
            case NOT_GOING -> "Not going";
        };
    }
}

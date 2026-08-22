package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.DashboardGroup;
import dev.ted.jittertravel.application.DashboardSection;
import dev.ted.jittertravel.application.DroppedView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.SpeakingStatus;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.TrTag;

import java.util.List;
import java.util.Locale;

import static j2html.TagCreator.*;

public class ConferencesRenderer {

    private static final String DATE_PATTERN = "EEE, MMM d";
    private static final String TIME_PATTERN = "h:mm a";

    // No container max-width and no overflow-x scroller: the table fills the available space and
    // is never wider than it. The two date columns break between date and time (each a .nowrap
    // unit), and City/Country are single-value columns that wrap on their own, so a narrow viewport
    // — e.g. iPad portrait — stacks the content onto more lines rather than forcing a horizontal
    // scrollbar. The table is never scrolled.
    //
    // The container gives up its horizontal margin and padding (measured 2026-08-19: the seven
    // columns started scrolling at ~860px with them, and fit at ~820px without) — 96px of gutter
    // is worth more spent on the table than on whitespace. Vertical margin stays, so the page
    // keeps its rhythm. The sibling list pages still have their gutters; this one has one column
    // more than they do.
    private static final String CSS = """
            /* No top margin: the filter row below sets the gap under the heading itself, so the
               two cannot stack. They did — the flex row stopped .time-toggle's own 1rem top margin
               from collapsing through the container the way it does on the sibling list pages,
               leaving 3rem of nothing under the title. */
            .conference-container { margin: 0 0 2rem; padding: 0; }
            .conference-table {
                width: 100%; border-collapse: collapse; text-align: left;
                margin-top: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                border-radius: 8px; overflow: hidden;
            }
            .conference-table th, .conference-table td {
                padding: 10px 16px; border-bottom: 1px solid var(--border-color);
                vertical-align: top;
            }
            .conference-table th {
                background-color: var(--header-bg); color: var(--muted-text);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
            }
            .conference-table tbody tr:last-child td { border-bottom: none; }
            .conference-table tbody tr:hover { background-color: var(--hover-bg); }
            .conf-name { font-weight: 500; color: var(--accent-color); }
            /* The actions a row carries are decided by the state machine, so their number and
               their words change between rows — the fixed Confirm/Decline slots this replaced no
               longer make sense, because most of these moves are meaningless in most states rather
               than unavailable for now. Left-justified, wrapping onto a second line where the
               column is narrow. */
            .conf-actions { display: flex; flex-wrap: wrap; gap: 0.25rem 0.9rem; }
            .conf-decline { color: #b00; text-decoration: none; white-space: nowrap; font-size: 0.9rem; }
            .conf-decline:hover { text-decoration: underline; }
            /* The CFP line under the conference name is itself the link that records or changes
               the deadline — it is a property of the conference, not a move in the submission
               state machine, so it is not in the actions cell. It inherits the muted deadline
               styling rather than the accent colour: it sits under the name, and a second coloured
               link there would compete with the name for the eye. */
            .conf-cfp { color: inherit; text-decoration: none; }
            .conf-cfp:hover { text-decoration: underline; }
            /* Action labels are nowrap units, so each extra character widens the table's minimum
               width — the reason they are one or two short words. They wrap onto further lines
               rather than pushing a narrow viewport into a horizontal scroll. */
            .conf-action { color: var(--accent-color); text-decoration: none; white-space: nowrap; font-size: 0.9rem; }
            .conf-action:hover { text-decoration: underline; }
            /* Same two words the public calendar uses, so the list and the calendar agree. */
            .conf-commitment {
                font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;
                padding: 2px 6px; border-radius: 4px; white-space: nowrap;
            }
            .conf-commitment--watching { background: #b45309; color: #ffffff; }
            .conf-commitment--going { background: #166534; color: #ffffff; }
            .conf-commitment--dropped { background: var(--header-bg); color: var(--muted-text); }
            /* SPEAKER sits beside the commitment chip in the same column, not in one of its own:
               it is a second fact about the same question, and a "Maybe" conference Ted has been
               invited to reads "Maybe SPEAKER". One word, and nowrap, for the reason the Actions
               column is one word — this table only just fits at ~820px and every extra character
               in a nowrap unit widens its minimum.
               Outlined rather than filled, so it reads as an annotation on the chip rather than
               competing with it: two solid blocks in one cell would look like two chips of equal
               weight, and the commitment is the answer to the column's question. */
            .conf-speaker {
                font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;
                padding: 1px 5px; border-radius: 4px; white-space: nowrap;
                border: 1px solid var(--accent-color); color: var(--accent-color);
            }
            /* The pair wraps together when the column is narrow rather than overflowing it. */
            .conf-going-cell { display: flex; flex-wrap: wrap; gap: 0.25rem; align-items: center; }
            /* Each group gets a heading and a line of guidance over its own table. No colour on the
               headings: this page is an action list, not a problem report, and the rule that every
               problem wears the same amber is about problems sitting among non-problems. The
               commitment chips already carry what colour this page needs. */
            .dashboard-section { margin-top: 2rem; }
            .dashboard-section:first-child { margin-top: 1rem; }
            .dashboard-heading {
                font-size: 0.8rem; font-weight: 700; text-transform: uppercase;
                letter-spacing: 0.06em; color: var(--muted-text); margin: 0;
            }
            .dashboard-guidance { font-size: 0.9rem; color: var(--muted-text); margin: 0.15rem 0 0; }
            /* The two filters sit on one line and wrap together on a narrow viewport. */
            /* The two filters sit on one line and wrap together on a narrow viewport. The row owns
               the gap under the heading; .time-toggle's own top margin is cancelled here, because
               a flex item's margin does not collapse and the two would add up. */
            .conference-filters {
                display: flex; flex-wrap: wrap; gap: 1rem; align-items: center; margin-top: 1rem;
            }
            .conference-filters .time-toggle { margin-top: 0; }
            /* A control, not a sentence. It was plain muted text and read as words left dangling
               beside the toggle — nothing said it could be clicked. It borrows .time-toggle's
               padding, font-size and radius so the two line up as a pair of controls, but stays
               outlined rather than filled: this one is a switch with an off state, and the segment
               beside it is the answer to the page's main question. */
            .dropped-toggle {
                display: inline-flex; align-items: center;
                padding: 6px 16px; font-size: 0.9rem;
                border: 1px solid var(--border-color); border-radius: 6px;
                background-color: var(--surface, #fff); color: var(--muted-text);
                text-decoration: none; white-space: nowrap;
            }
            .dropped-toggle:hover { background-color: var(--header-bg); color: var(--text-color); }
            /* The deadline under the name, not in a column of its own — see nameCell. */
            .conf-cfp-deadline {
                font-size: 0.8rem; font-weight: 400; color: var(--muted-text); margin-top: 0.15rem;
            }
            """;

    public static String render(List<DashboardSection> sections, TimeView activeFilter) {
        return render(sections, activeFilter, DroppedView.HIDE);
    }

    /**
     * The page under both of its filters. They are independent parameters and each toggle carries
     * the other's value through, so changing one never silently resets the other — see
     * {@link DroppedView} for why they are not one parameter.
     * <p>
     * The two-argument overload above is what {@code TimeFilterToggleConventionTest} discovers and
     * exercises; it is the default view, dropped conferences hidden.
     */
    public static String render(List<DashboardSection> sections, TimeView activeFilter,
                                DroppedView activeDropped) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Conferences", CSS),
                body(
                        Page.viewNav(Page.NavAudience.OWNER, "/conferences"),
                        h1("Conferences"),
                        div().withClass("conference-container").with(
                                div().withClass("conference-filters").with(
                                        TimeFilterToggle.render("/conferences", activeFilter,
                                                activeDropped == DroppedView.SHOW ? "&dropped=show" : ""),
                                        droppedToggle(activeFilter, activeDropped)),
                                sections.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : div().with(sections.stream()
                                                             .map(ConferencesRenderer::renderSection)
                                                             .toList()),
                                br(),
                                a("Plan another conference").withHref("/plan-conference")
                        )
                )
        ).withLang("en").render();
    }

    /**
     * One link, not a two-segment control like the FUTURE/ALL toggle: this filter answers a yes/no
     * question, so a second segment would only ever say what the reader is already looking at.
     * It sits beside the time toggle and carries the active time filter through.
     */
    private static DomContent droppedToggle(TimeView activeFilter, DroppedView activeDropped) {
        String filterQuery = "?filter=" + activeFilter.name().toLowerCase(Locale.ENGLISH);
        return activeDropped == DroppedView.SHOW
                ? a("Hide dropped").withClass("dropped-toggle")
                                   .withHref("/conferences" + filterQuery)
                : a("Show dropped").withClass("dropped-toggle")
                                   .withHref("/conferences" + filterQuery + "&dropped=show");
    }

    /**
     * A heading, one line saying what to do about the group, and the group's own table. The wording
     * lives here rather than on {@link DashboardGroup}: the enum is the derived fact, and how it is
     * worded is presentation (CLAUDE.md).
     * <p>
     * Exhaustive, so a new group cannot be added without deciding what it tells Ted to do.
     */
    private static DomContent renderSection(DashboardSection section) {
        return div().withClass("dashboard-section").with(
                h2(heading(section.group())).withClass("dashboard-heading"),
                p(guidance(section.group())).withClass("dashboard-guidance"),
                renderTable(section.conferences())
        );
    }

    private static String heading(DashboardGroup group) {
        return switch (group) {
            case CFP_CLOSES_SOON -> "CFP closes soon";
            case INVITED -> "Invited";
            case CFP_DATE_UNKNOWN -> "CFP date unknown";
            case DECIDE -> "Decide";
            case NOTHING_TO_SUBMIT -> "Nothing to submit";
            case WAITING_TO_HEAR -> "Waiting to hear";
            case GOING -> "Going";
            case DROPPED -> "Dropped";
        };
    }

    private static String guidance(DashboardGroup group) {
        return switch (group) {
            case CFP_CLOSES_SOON -> "Submit, or decide not to.";
            case INVITED -> "They asked you to speak. Say yes, or decline.";
            case CFP_DATE_UNKNOWN -> "Find the deadline and record it, so a reminder can be set.";
            case DECIDE -> "No talk this time. Go as an attendee, or decline.";
            case NOTHING_TO_SUBMIT -> "Sessions are chosen on the day — just decide whether to go.";
            case WAITING_TO_HEAR -> "Submitted. Record what they say when they say it.";
            case GOING -> "Committed — nothing to do.";
            case DROPPED -> "Said no to these. Kept as a record for next year.";
        };
    }

    private static DomContent renderEmptyState(TimeView activeFilter) {
        String message = activeFilter == TimeView.FUTURE
                ? "No upcoming conferences."
                : "No conferences yet.";
        return p(message).withClass("empty-state");
    }

    private static DomContent renderTable(List<ConferenceView> conferences) {
        return table().withClass("conference-table").with(
                thead(tr(
                        th("Name"),
                        th("Going?"),
                        th("Start Date"),
                        th("End Date"),
                        th("City"),
                        th("Country"),
                        th("Actions")
                )),
                tbody().with(
                        conferences.stream()
                                   .map(ConferencesRenderer::renderRow)
                                   .toList()
                )
        );
    }

    private static TrTag renderRow(ConferenceView conf) {
        return tr(
                td(nameCell(conf)).withClass("conf-name"),
                td(goingCell(conf)),
                td(dateTime(conf.startDate())),
                td(dateTime(conf.endDate())),
                td(conf.city()),
                td(conf.country()),
                td(actions(conf))
        );
    }

    /**
     * The name, and under it the CFP deadline when one is recorded.
     * <p>
     * A second line rather than an eighth column: this table already only just fits at ~820px, and a
     * new column would push it into the horizontal scroll that is ruled out everywhere. It renders in
     * every group that has a deadline, not only the closing one — a passed deadline is exactly what
     * "Decide" means, so hiding the date there would remove the reason for the row's group.
     */
    private static DomContent nameCell(ConferenceView conf) {
        return div().with(
                div(conf.name()),
                cfpLine(conf)
        );
    }

    /**
     * The CFP deadline under the name, and the deadline <em>is</em> the affordance for recording or
     * changing it — which is why this is not an action in the actions cell (Ted, 2026-08-22). A
     * closing date is a property of the conference, like its venue, not a move in the submission
     * state machine, and keeping it out is what lets every state fit in three actions.
     * <p>
     * A conference with no deadline recorded still shows the line, because "not recorded" is the
     * state the CFP-date-unknown group exists to prompt about — an absent line would hide the very
     * job that group is asking for. An open-space conference shows nothing at all: it has no call
     * for papers, and the command refuses to record one.
     */
    private static DomContent cfpLine(ConferenceView conf) {
        if (conf.format() == ConferenceFormat.OPEN_SPACE) {
            return span();
        }
        // A dropped conference keeps its deadline as a record but not as a link: recording a CFP
        // for a conference Ted declined is refused by the domain, so a link there would lead
        // nowhere. Nothing to show at all if no deadline was ever recorded.
        if (conf.commitment() == AttendanceCommitment.NOT_GOING) {
            return conf.cfpClosesOn() == null
                    ? span()
                    : div().withClass("conf-cfp-deadline").with(
                            span("CFP "),
                            ZonedTimeTag.renderDateTimeStacking(
                                    conf.cfpClosesOn(), DATE_PATTERN, TIME_PATTERN));
        }
        String href = "/conferences/" + conf.conferenceId().id() + "/cfp";
        if (conf.cfpClosesOn() == null) {
            return div().withClass("conf-cfp-deadline").with(
                    a("CFP date unknown").withClass("conf-cfp")
                                         .withTitle("Record when this conference's CFP closes")
                                         .withHref(href));
        }
        return div().withClass("conf-cfp-deadline").with(
                a().withClass("conf-cfp")
                   .withTitle("Change the recorded CFP deadline")
                   .withHref(href)
                   .with(span("CFP "),
                         ZonedTimeTag.renderDateTimeStacking(
                                 conf.cfpClosesOn(), DATE_PATTERN, TIME_PATTERN)));
    }

    /**
     * The {@code Going?} column answers one question with up to two marks: the commitment chip,
     * always, and {@code SPEAKER} beside it when Ted is speaking. They are not alternatives — a
     * speculative conference he has been invited to reads {@code Maybe SPEAKER}.
     * <p>
     * <strong>{@code SPEAKER}, not the calendar's "A Ted Talk".</strong> Same fact, two surfaces,
     * two lengths: a calendar entry owns a whole row of a day column and can afford the playful
     * wording, while this cell is a nowrap unit in a seven-column table that only just fits.
     */
    private static DomContent goingCell(ConferenceView conf) {
        DivTag cell = div().withClass("conf-going-cell").with(commitmentChip(conf.commitment()));
        if (conf.speaking()) {
            cell.with(span("Speaker").withClass("conf-speaker")
                                     .withTitle("Ted is speaking at this one"));
        }
        return cell;
    }

    /**
     * "Maybe" reads the same here as on the public calendar, deliberately: one vocabulary for one
     * fact. "Going" is spelled out on this list even though the calendar marks it only by the
     * absence of a chip — a table column has to say something in every row.
     */
    private static DomContent commitmentChip(AttendanceCommitment commitment) {
        return switch (commitment) {
            case WATCHING -> span("Maybe").withClass("conf-commitment conf-commitment--watching");
            case GOING -> span("Going").withClass("conf-commitment conf-commitment--going");
            // Muted rather than red: dropping a conference is a decision, not a problem, and the
            // rule that every problem wears the same amber is about problems among non-problems.
            case NOT_GOING -> span("Not going").withClass("conf-commitment conf-commitment--dropped");
        };
    }

    /**
     * <strong>The state machine decides what a row offers.</strong> Each state has at most three
     * moves, so they are links and never a menu — and, unlike the fixed Confirm/CFP/Decline slots
     * this replaced, an action that does not apply is <em>absent</em> rather than greyed.
     * <p>
     * That is a deliberate exception to "an unavailable action is shown disabled, with the reason"
     * (CLAUDE.md), taken with Ted on 2026-08-22: that rule is about an action that has been or
     * will be available to this viewer, and most moves here are not merely unavailable-for-now but
     * meaningless — "Accepted" on a conference nothing was submitted to names an event that could
     * never be true. Carrying nine greyed labels on every row to keep positions fixed would say
     * less, not more. Recording the CFP deadline is <em>not</em> in this cell at all: it is a
     * property of the conference rather than a move, and it lives under the name.
     * <p>
     * Every action is a link to a page that hosts the actual POST form, never a POST from here —
     * this renderer is j2html, and the project keeps POST forms in Thymeleaf so renderers stay
     * clear of Spring's CSRF plumbing. The link carries the choice, so the page opens with it
     * already selected and the second click is a confirmation rather than a decision.
     * <p>
     * The labels are past tense, because that is what this app does: it records what has already
     * happened in the world. "Ticket Bought", not "Buy ticket" (Ted, 2026-08-22).
     */
    private static DomContent actions(ConferenceView conf) {
        String base = "/conferences/" + conf.conferenceId().id();
        // A dropped conference has no live action: the domain refuses every command against a
        // declined conference, so there is nothing here that could be triggered — not even in a
        // disabled form, which would promise a capability that does not exist. Going after all
        // means planning it again.
        if (conf.commitment() == AttendanceCommitment.NOT_GOING) {
            return div().withClass("conf-actions");
        }
        DivTag cell = div().withClass("conf-actions");
        if (conf.commitment() == AttendanceCommitment.GOING) {
            // Committed. The only talk-side move left is pulling a talk that is in the program —
            // which changes nothing about attending.
            if (conf.speakingStatus() == SpeakingStatus.ACCEPTED) {
                cell.with(talkLink(base, TalkOutcome.WITHDRAWN, "Withdrawn",
                        "Record that you pulled your talk. You are still going."));
            }
            return cell.with(declineLink(base));
        }
        return switch (conf.speakingStatus()) {
            // Submitted and waiting: the only moves are what the organizers say, and pulling it.
            case SUBMITTED -> cell
                    .with(talkLink(base, TalkOutcome.ACCEPTED, "Accepted",
                            "They said yes. This also records that you are going."))
                    .with(talkLink(base, TalkOutcome.REJECTED, "Rejected", "They said no."))
                    .with(talkLink(base, TalkOutcome.WITHDRAWN, "Withdrawn", "You pulled it."));
            // An open offer. Saying yes is a confirmation naming the invitation as the reason,
            // which is what separates speaking there from merely attending.
            case INVITED -> cell
                    .with(confirmLink(base, AttendanceBasis.SPEAKING_INVITED, "Invitation Accepted",
                            "Say yes: you are going, and you are speaking."))
                    .with(declineLink(base));
            // Turned down. On an ACCEPTANCE_REQUIRED conference this row is not here at all — it
            // was dropped — so this is the go-anyway case.
            case REJECTED -> cell
                    .with(confirmLink(base, AttendanceBasis.TICKET_PURCHASED, "Ticket Bought",
                            "Going as an attendee after all."))
                    .with(declineLink(base));
            // Nothing outstanding: submitting is on the table again wherever there is a CFP.
            case NOT_SPEAKING, WITHDRAWN -> {
                if (conf.format() != ConferenceFormat.OPEN_SPACE) {
                    cell.with(talkLink(base, TalkOutcome.SUBMITTED, "Submitted",
                            "Record that you submitted a talk."));
                }
                yield cell
                        .with(confirmLink(base, AttendanceBasis.TICKET_PURCHASED, "Ticket Bought",
                                "Going as an attendee."))
                        .with(declineLink(base));
            }
            // Accepted while still merely watching cannot happen: accepting commits attendance,
            // so the GOING branch above has already returned.
            case ACCEPTED -> cell.with(declineLink(base));
        };
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

    private static DomContent dateTime(ZonedTimestamp when) {
        return ZonedTimeTag.renderDateTimeStacking(when, DATE_PATTERN, TIME_PATTERN);
    }
}
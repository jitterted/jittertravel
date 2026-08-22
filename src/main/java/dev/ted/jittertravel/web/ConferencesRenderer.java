package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.AttendanceCommitment;
import dev.ted.jittertravel.application.ConferenceView;
import dev.ted.jittertravel.application.DashboardGroup;
import dev.ted.jittertravel.application.DashboardSection;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;
import j2html.tags.specialized.TrTag;

import java.util.List;

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
            .conference-container { margin: 2rem 0; padding: 0; }
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
            /* Action affordances never move, and one that is merely unavailable is shown disabled
               rather than removed. The cell holds two virtual slots — Confirm, then Decline — and
               every row fills both: on a GOING row Confirm is greyed, non-interactive text with a
               title saying why. So Decline is in the second slot in every row, and both stay
               left-justified within their own slot.
               Slots rather than right-aligning the cell (reads oddly, and drags the header flush
               right) or a real second column (another cell costs 32px of padding, and this table
               only just fits at ~820px). The disabled text sizes the slot exactly, so no guessed
               width in rem/ch can drift when the label or font changes — and it replaced an
               invisible placeholder, which left a blank line wherever the cell wrapped. */
            .conf-actions { display: flex; flex-wrap: wrap; gap: 0.25rem 0.9rem; }
            /* Its own rule rather than `.conf-confirm` plus a modifier: sharing the link class
               would inherit the accent colour and the hover underline, and a single-class modifier
               defined earlier in the sheet loses to them. */
            .conf-confirm-disabled {
                color: var(--muted-text); cursor: default;
                white-space: nowrap; font-size: 0.9rem;
            }
            /* Header centred across both slots — it labels the pair, not either one. */
            .conference-table th:last-child { text-align: center; }
            .conf-decline { color: #b00; text-decoration: none; white-space: nowrap; font-size: 0.9rem; }
            .conf-decline:hover { text-decoration: underline; }
            /* Three letters, because this is the third nowrap unit in the narrowest column of a
               table that only just fits. The tick says a deadline is already recorded — the link
               does the same job either way, so the word does not change and neither does its slot. */
            .conf-cfp { color: var(--accent-color); text-decoration: none; white-space: nowrap; font-size: 0.9rem; }
            .conf-cfp:hover { text-decoration: underline; }
            /* One word on purpose: this cell's links are nowrap units, so a longer label
               ("Confirm attendance") widens the table's minimum width and is what would push a
               narrow viewport into the horizontal scroll the comment above rules out. */
            .conf-confirm { color: var(--accent-color); text-decoration: none; white-space: nowrap; font-size: 0.9rem; }
            .conf-confirm:hover { text-decoration: underline; }
            /* Same two words the public calendar uses, so the list and the calendar agree. */
            .conf-commitment {
                font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em;
                padding: 2px 6px; border-radius: 4px; white-space: nowrap;
            }
            .conf-commitment--watching { background: #b45309; color: #ffffff; }
            .conf-commitment--going { background: #166534; color: #ffffff; }
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
            /* The deadline under the name, not in a column of its own — see nameCell. */
            .conf-cfp-deadline {
                font-size: 0.8rem; font-weight: 400; color: var(--muted-text); margin-top: 0.15rem;
            }
            """;

    public static String render(List<DashboardSection> sections, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Conferences", CSS),
                body(
                        Page.viewNav(Page.NavAudience.OWNER, "/conferences"),
                        h1("Conferences"),
                        div().withClass("conference-container").with(
                                TimeFilterToggle.render("/conferences", activeFilter),
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
            case CFP_DATE_UNKNOWN -> "CFP date unknown";
            case DECIDE -> "Decide";
            case NOTHING_TO_SUBMIT -> "Nothing to submit";
            case GOING -> "Going";
        };
    }

    private static String guidance(DashboardGroup group) {
        return switch (group) {
            case CFP_CLOSES_SOON -> "Submit, or decide not to.";
            case CFP_DATE_UNKNOWN -> "Find the deadline and record it, so a reminder can be set.";
            case DECIDE -> "The CFP has closed. Go as an attendee, or decline.";
            case NOTHING_TO_SUBMIT -> "Sessions are chosen on the day — just decide whether to go.";
            case GOING -> "Committed — nothing to do.";
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
        if (conf.cfpClosesOn() == null) {
            return span(conf.name());
        }
        return div().with(
                div(conf.name()),
                div().withClass("conf-cfp-deadline").with(
                        span("CFP "),
                        ZonedTimeTag.renderDateTimeStacking(conf.cfpClosesOn(), DATE_PATTERN, TIME_PATTERN))
        );
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
        };
    }

    /**
     * Confirming is offered only while a conference is still speculative: once it is GOING the
     * action has nothing left to say, and re-confirming to correct the basis is a slice-4 concern
     * (nothing renders the basis yet). Declining stays available either way — changing your mind
     * about a conference you committed to is exactly what it is for.
     */
    private static DomContent actions(ConferenceView conf) {
        String base = "/conferences/" + conf.conferenceId().id();
        // Three actions, so three links: a menu is only worth its extra click above three
        // (CLAUDE.md), and this cell has the width for them because each is one short word.
        return div().withClass("conf-actions")
                    .with(confirmSlot(conf, base))
                    .with(a(conf.cfpClosesOn() == null ? "CFP" : "CFP ✓")
                            .withClass("conf-cfp")
                            .withTitle(conf.cfpClosesOn() == null
                                    ? "Record when this conference's CFP closes"
                                    : "Change the recorded CFP deadline")
                            .withHref(base + "/cfp"))
                    .with(a("Decline").withClass("conf-decline")
                            .withHref(base + "/decline"));
    }

    /**
     * The first slot always exists, so the second one — Decline — cannot move. On a GOING
     * conference Confirm is greyed rather than removed: an action that is unavailable *for now*
     * stays visible with the reason attached, so the row's vocabulary does not change under the
     * reader.
     * <p>
     * The reason names a <em>presentation</em> limit, not a rule: the domain deliberately allows
     * re-confirming with a different basis (ticket bought, then talk accepted — see
     * {@code ConfirmConferenceAttendanceCommand}), and this becomes a live link again in slice 4,
     * when the basis finally has somewhere to render. A tooltip claiming "not allowed" would be
     * a lie about the model.
     * <p>
     * A {@code span}, never a disabled {@code <a>}: it is not focusable and cannot be activated,
     * which is exactly the intent.
     */
    private static DomContent confirmSlot(ConferenceView conf, String base) {
        if (conf.commitment() == AttendanceCommitment.WATCHING) {
            return a("Confirm").withClass("conf-confirm").withHref(base + "/confirm");
        }
        return span("Confirm").withClass("conf-confirm-disabled")
                              .withTitle("Already confirmed. Changing why you're going "
                                       + "arrives with submission tracking.")
                              .attr("aria-disabled", "true");
    }

    private static DomContent dateTime(ZonedTimestamp when) {
        return ZonedTimeTag.renderDateTimeStacking(when, DATE_PATTERN, TIME_PATTERN);
    }
}
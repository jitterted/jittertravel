package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;
import j2html.tags.specialized.TrTag;

import java.util.List;

import static j2html.TagCreator.*;

public class TentativeConferencesRenderer {

    private static final String DATE_PATTERN = "EEE, MMM d";
    private static final String TIME_PATTERN = "h:mm a";

    // No container max-width and no overflow-x scroller: the table fills the available space and
    // is never wider than it. The two date columns break between date and time (each a .nowrap
    // unit), and City/Country are single-value columns that wrap on their own, so a narrow viewport
    // — e.g. iPad portrait — stacks the content onto more lines rather than forcing a horizontal
    // scrollbar. The table is never scrolled.
    private static final String CSS = """
            .conference-container { margin: 2rem; padding: 0 1rem; }
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
            .conf-decline { color: #b00; text-decoration: none; white-space: nowrap; font-size: 0.9rem; }
            .conf-decline:hover { text-decoration: underline; }
            """;

    public static String render(List<TentativeConferenceView> conferences, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Tentative Conferences", CSS),
                body(
                        nav(a("JitterTravel").withHref("/")),
                        h1("Tentative Conferences"),
                        div().withClass("conference-container").with(
                                TimeFilterToggle.render("/tentative-conferences", activeFilter),
                                conferences.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : renderTable(conferences),
                                br(),
                                a("Plan another conference").withHref("/plan-conference")
                        )
                )
        ).withLang("en").render();
    }

    private static DomContent renderEmptyState(TimeView activeFilter) {
        String message = activeFilter == TimeView.FUTURE
                ? "No upcoming conferences."
                : "No tentative conferences yet.";
        return p(message).withClass("empty-state");
    }

    private static DomContent renderTable(List<TentativeConferenceView> conferences) {
        return table().withClass("conference-table").with(
                thead(tr(
                        th("Name"),
                        th("Start Date"),
                        th("End Date"),
                        th("City"),
                        th("Country"),
                        th("Actions")
                )),
                tbody().with(
                        conferences.stream()
                                   .map(TentativeConferencesRenderer::renderRow)
                                   .toList()
                )
        );
    }

    private static TrTag renderRow(TentativeConferenceView conf) {
        return tr(
                td(conf.name()).withClass("conf-name"),
                td(dateTime(conf.startDate())),
                td(dateTime(conf.endDate())),
                td(conf.city()),
                td(conf.country()),
                td(a("Decline").withClass("conf-decline")
                        .withHref("/tentative-conferences/" + conf.conferenceId().id() + "/decline"))
        );
    }

    private static DomContent dateTime(ZonedTimestamp when) {
        return ZonedTimeTag.renderDateTimeStacking(when, DATE_PATTERN, TIME_PATTERN);
    }
}
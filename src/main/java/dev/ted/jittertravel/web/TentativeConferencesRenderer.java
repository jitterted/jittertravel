package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.TentativeConferenceView;
import dev.ted.jittertravel.application.TimeView;
import j2html.tags.DomContent;
import j2html.tags.specialized.TrTag;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static j2html.TagCreator.*;

public class TentativeConferencesRenderer {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH);

    private static final String CSS = """
            .conference-container { max-width: 100ch; margin: 2rem; padding: 0 1rem; }
            .conference-table {
                width: 100%; border-collapse: collapse; text-align: left;
                margin-top: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1);
                border-radius: 8px; overflow: hidden;
            }
            .conference-table th, .conference-table td {
                padding: 10px 16px; border-bottom: 1px solid var(--border-color);
            }
            .conference-table th {
                background-color: var(--header-bg); color: var(--muted-text);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
            }
            .conference-table tbody tr:last-child td { border-bottom: none; }
            .conference-table tbody tr:hover { background-color: var(--hover-bg); }
            .conf-name { font-weight: 500; color: var(--accent-color); }
            .table-responsive { overflow-x: auto; -webkit-overflow-scrolling: touch; }
            .empty-state { margin-top: 1rem; color: var(--muted-text); }
            """;

    public static String render(List<TentativeConferenceView> conferences, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                head(
                        meta().withCharset("UTF-8"),
                        title("Tentative Conferences"),
                        link().withRel("stylesheet").withHref("/site.css"),
                        rawHtml("<style>" + CSS + "</style>")
                ),
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
        return div().withClass("table-responsive").with(
                table().withClass("conference-table").with(
                        thead(tr(
                                th("Name"),
                                th("Start Date"),
                                th("End Date"),
                                th("City"),
                                th("Country")
                        )),
                        tbody().with(
                                conferences.stream()
                                           .map(TentativeConferencesRenderer::renderRow)
                                           .toList()
                        )
                )
        );
    }

    private static TrTag renderRow(TentativeConferenceView conf) {
        return tr(
                td(conf.name()).withClass("conf-name"),
                td(conf.startDate().format(DATE_TIME_FORMAT)),
                td(conf.endDate().format(DATE_TIME_FORMAT)),
                td(conf.city()),
                td(conf.country())
        );
    }
}
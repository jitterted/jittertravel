package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringView;
import j2html.tags.specialized.DivTag;

import java.time.format.DateTimeFormatter;
import java.util.List;

import static j2html.TagCreator.*;

public class PlannedGatheringsRenderer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private static final String CSS = """
                .page { max-width: 800px; margin: 0 auto; padding: 1rem; }
                nav { margin-bottom: 1rem; font-size: 0.9rem; }
                nav a { color: var(--accent-color); text-decoration: none; }
                nav a:hover { text-decoration: underline; }
                nav .sep { color: var(--muted-text); margin: 0 0.4em; }
                h1 { margin: 0 0 1rem; font-size: 1.4rem; }
                .gathering-list { display: flex; flex-direction: column; gap: 0.75rem; }
                .gathering-card {
                    border-left: 4px solid #7c3aed;
                    border-radius: 0 8px 8px 0;
                    background: #f5f3ff;
                    padding: 0.75rem 1rem;
                    display: grid;
                    grid-template-columns: 10rem 1fr;
                    gap: 0 1rem;
                    align-items: start;
                }
                .gathering-date { font-size: 0.8rem; font-weight: 700; color: #5b21b6; }
                .gathering-time { font-size: 0.8rem; color: #6d28d9; margin-top: 0.1rem; }
                .gathering-title { font-weight: 700; font-size: 1rem; color: var(--text-color); margin-bottom: 0.15rem; }
                .gathering-venue { font-size: 0.85rem; color: var(--muted-text); margin-bottom: 0.3rem; }
                .gathering-footer { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-top: 0.25rem; }
                .badge-speaking {
                    font-size: 0.68rem; font-weight: 700; text-transform: uppercase;
                    letter-spacing: 0.06em; background: #7c3aed; color: #fff;
                    border-radius: 4px; padding: 0.15rem 0.45rem;
                }
                .info-link { font-size: 0.82rem; color: #6d28d9; text-decoration: underline; }
                .empty-state { color: var(--muted-text); font-style: italic; font-size: 0.9rem; }
            """;

    public static String render(List<PlannedGatheringView> gatherings) {
        return "<!DOCTYPE html>\n" + html(
                head(
                        meta().withCharset("UTF-8"),
                        title("Planned Gatherings"),
                        link().withRel("stylesheet").withHref("/site.css"),
                        rawHtml("<style>" + CSS + "</style>")
                ),
                body(
                        div().withClass("page").with(
                                nav(
                                        a("JitterTravel").withHref("/"),
                                        rawHtml("<span class=\"sep\">&middot;</span>"),
                                        a("Calendar").withHref("/calendar")
                                ),
                                h1("Planned Gatherings"),
                                gatherings.isEmpty()
                                        ? div("No gatherings planned yet.").withClass("empty-state")
                                        : div().withClass("gathering-list").with(
                                                gatherings.stream().map(PlannedGatheringsRenderer::renderCard).toList()
                                        )
                        )
                )
        ).withLang("en").render();
    }

    private static DivTag renderCard(PlannedGatheringView g) {
        String timeRange = g.startTime().format(TIME_FORMAT) + " – " + g.endTime().format(TIME_FORMAT);
        String venueLocation = buildVenueLocation(g);

        DivTag dateCol = div(
                div(g.date().format(DATE_FORMAT)).withClass("gathering-date"),
                div(timeRange).withClass("gathering-time")
        );

        DivTag footer = div().withClass("gathering-footer");
        if (g.speaking()) {
            footer.with(span("Speaking").withClass("badge-speaking"));
        }
        if (!g.infoUrl().isBlank()) {
            footer.with(a("Event page →").withHref(g.infoUrl())
                    .withClass("info-link")
                    .withTarget("_blank")
                    .withRel("noopener"));
        }

        DivTag contentCol = div(
                div(g.title()).withClass("gathering-title"),
                div(venueLocation).withClass("gathering-venue"),
                footer
        );

        return div().withClass("gathering-card").with(dateCol, contentCol);
    }

    private static String buildVenueLocation(PlannedGatheringView g) {
        StringBuilder sb = new StringBuilder();
        if (!g.venueName().isBlank()) {
            sb.append(g.venueName()).append(" · ");
        }
        sb.append(g.city());
        if (!g.country().isBlank()) {
            sb.append(", ").append(g.country());
        }
        return sb.toString();
    }
}

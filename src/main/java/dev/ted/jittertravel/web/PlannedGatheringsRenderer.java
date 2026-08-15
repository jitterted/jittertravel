package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringView;
import dev.ted.jittertravel.application.TimeView;
import j2html.tags.specialized.DivTag;

import java.util.List;

import static j2html.TagCreator.*;

public class PlannedGatheringsRenderer {

    private static final String DATE_FORMAT = "EEE, MMM d, yyyy";
    private static final String TIME_FORMAT = "h:mm a";

    // No .page max-width: the list fills the centered page width like the other list views. Each
    // card is a two-column grid (a fixed date column beside the details); below 640px that would
    // squeeze the details into a sliver, so the card collapses to a single column with the date
    // stacked above the details. Nothing is capped and nothing scrolls sideways.
    private static final String CSS = """
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
                @media (max-width: 640px) {
                    .gathering-card { grid-template-columns: 1fr; gap: 0.35rem 0; }
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
                .gathering-edit-link { font-size: 0.82rem; color: #6d28d9; text-decoration: underline; }
                .empty-state { font-style: italic; font-size: 0.9rem; }
            """;

    public static String render(List<PlannedGatheringView> gatherings, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Planned Gatherings", CSS),
                body(
                        div().withClass("page").with(
                                Page.navHomeAndCalendar(),
                                h1("Planned Gatherings"),
                                TimeFilterToggle.render("/planned-gatherings", activeFilter),
                                gatherings.isEmpty()
                                        ? div(emptyStateMessage(activeFilter)).withClass("empty-state")
                                        : div().withClass("gathering-list").with(
                                                gatherings.stream().map(PlannedGatheringsRenderer::renderCard).toList()
                                        )
                        )
                )
        ).withLang("en").render();
    }

    private static String emptyStateMessage(TimeView activeFilter) {
        return activeFilter == TimeView.FUTURE
                ? "No upcoming gatherings."
                : "No gatherings planned yet.";
    }

    private static DivTag renderCard(PlannedGatheringView g) {
        String venueLocation = buildVenueLocation(g);

        // Venue-local wall-clock as the element text; the UTC instant rides along in the
        // datetime attribute for the browser-zone upgrade.
        DivTag dateCol = div(
                div().withClass("gathering-date").with(ZonedTimeTag.render(g.startsAt(), DATE_FORMAT)),
                div().withClass("gathering-time").with(
                        ZonedTimeTag.render(g.startsAt(), TIME_FORMAT),
                        rawHtml(" &ndash; "),
                        ZonedTimeTag.render(g.endsAt(), TIME_FORMAT)
                )
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
        footer.with(a("Edit").withClass("gathering-edit-link")
                .withHref("/planned-gatherings/" + g.gatheringId().id()));

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
        if (!g.street().isBlank()) {
            sb.append(g.street()).append(", ");
        }
        sb.append(g.city());
        if (!g.region().isBlank()) {
            sb.append(", ").append(g.region());
        }
        if (!g.postalCode().isBlank()) {
            sb.append(" ").append(g.postalCode());
        }
        if (!g.country().isBlank()) {
            sb.append(", ").append(g.country());
        }
        return sb.toString();
    }
}

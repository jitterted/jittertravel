package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedGatheringView;
import dev.ted.jittertravel.application.TimeView;
import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;

import java.util.List;

import static j2html.TagCreator.*;

public class PlannedGatheringsRenderer {

    private static final String DATE_FORMAT = "EEE, MMM d, yyyy";
    private static final String TIME_FORMAT = "h:mm a";

    // A columned table like the other list views: When / Speaking / Gathering / Venue / actions.
    // ONE grid owns the columns: .gathering-list defines the five tracks and the header and every
    // row inherit them via grid-template-columns: subgrid, so the columns line up across rows with
    // min-content floors (aligned, no overflow). No .page max-width — it fills the centered page
    // width. Below 640px the grid collapses to a single stacked column, the header hides, and each
    // cell shows its own leg label. Nothing is capped and nothing scrolls sideways.
    private static final String CSS = """
                .gathering-list {
                    display: grid;
                    grid-template-columns: auto auto 2fr 2fr auto;
                    column-gap: 0.75rem;
                    margin-top: 1rem;
                    background: var(--surface, #fff);
                    border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;
                }
                .gathering-header, .gathering-row {
                    grid-column: 1 / -1;
                    display: grid;
                    grid-template-columns: subgrid;
                    align-items: start; padding: 10px 16px;
                }
                .gathering-header {
                    background: var(--header-bg); color: var(--muted-text);
                    font-weight: 600; text-transform: uppercase;
                    font-size: 0.75rem; letter-spacing: 0.5px;
                    border-bottom: 1px solid var(--border-color);
                }
                .gathering-row { border-bottom: 1px solid var(--border-color); font-size: 0.9rem; }
                .gathering-row:last-child { border-bottom: none; }
                .gathering-row:hover { background: var(--hover-bg); }
                .gathering-when-date { font-weight: 700; color: #5b21b6; white-space: nowrap; }
                .gathering-when-time { color: #6d28d9; font-size: 0.85rem; white-space: nowrap; margin-top: 0.1rem; }
                .gathering-title { font-weight: 700; }
                .gathering-venue-address { font-size: 0.82rem; color: var(--muted-text); margin-top: 0.1rem; }
                .badge-speaking {
                    display: inline-block;
                    font-size: 0.68rem; font-weight: 700; text-transform: uppercase;
                    letter-spacing: 0.06em; background: #7c3aed; color: #fff;
                    border-radius: 4px; padding: 0.15rem 0.45rem;
                }
                .gathering-actions { display: flex; flex-direction: column; gap: 0.3rem; align-items: start; }
                .info-link, .gathering-edit-link { font-size: 0.85rem; color: #6d28d9; text-decoration: underline; }
                .empty-state { font-style: italic; font-size: 0.9rem; }
                /* Per-column labels: hidden while the header row is visible, shown once the grid stacks. */
                .leg-label {
                    display: none;
                    font-size: 0.7rem; font-weight: 600; text-transform: uppercase;
                    letter-spacing: 0.5px; color: var(--muted-text);
                }
                @media (max-width: 640px) {
                    .gathering-list { grid-template-columns: 1fr; }
                    .gathering-header { display: none; }
                    .gathering-row { grid-template-columns: 1fr; gap: 0.3rem 0; }
                    .leg-label { display: block; margin-top: 0.5rem; }
                }
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
                                        : renderList(gatherings)
                        )
                )
        ).withLang("en").render();
    }

    private static String emptyStateMessage(TimeView activeFilter) {
        return activeFilter == TimeView.FUTURE
                ? "No upcoming gatherings."
                : "No gatherings planned yet.";
    }

    private static DomContent renderList(List<PlannedGatheringView> gatherings) {
        return div().withClass("gathering-list").with(
                div().withClass("gathering-header").with(
                        span("When"), span("Speaking"), span("Gathering"), span("Venue"), span()
                ),
                each(gatherings, PlannedGatheringsRenderer::renderRow)
        );
    }

    private static DomContent renderRow(PlannedGatheringView g) {
        return div().withClass("gathering-row").with(
                whenCell(g),
                speakingCell(g),
                titleCell(g),
                venueCell(g),
                actionsCell(g)
        );
    }

    // Venue-local wall-clock as the element text; the UTC instant rides along in the datetime
    // attribute for the browser-zone upgrade.
    private static DomContent whenCell(PlannedGatheringView g) {
        return div().with(
                legLabel("When"),
                div().withClass("gathering-when-date").with(ZonedTimeTag.render(g.startsAt(), DATE_FORMAT)),
                div().withClass("gathering-when-time").with(
                        ZonedTimeTag.render(g.startsAt(), TIME_FORMAT),
                        rawHtml(" &ndash; "),
                        ZonedTimeTag.render(g.endsAt(), TIME_FORMAT)
                )
        );
    }

    // Its own column; empty when Ted is only attending, so the badge alone reads as "Speaking".
    private static DomContent speakingCell(PlannedGatheringView g) {
        DivTag cell = div();
        if (g.speaking()) {
            cell.with(span("Speaking").withClass("badge-speaking"));
        }
        return cell;
    }

    private static DomContent titleCell(PlannedGatheringView g) {
        return div().with(
                legLabel("Gathering"),
                div(g.title()).withClass("gathering-title")
        );
    }

    private static DomContent venueCell(PlannedGatheringView g) {
        DivTag cell = div().with(legLabel("Venue"));
        if (!g.venueName().isBlank()) {
            cell.with(div(g.venueName()).withClass("gathering-venue-name"));
        }
        String address = buildAddress(g);
        if (!address.isBlank()) {
            cell.with(div(address).withClass("gathering-venue-address"));
        }
        return cell;
    }

    private static DomContent actionsCell(PlannedGatheringView g) {
        DivTag cell = div().withClass("gathering-actions");
        if (!g.infoUrl().isBlank()) {
            cell.with(a("Event page →").withHref(g.infoUrl())
                    .withClass("info-link")
                    .withTarget("_blank")
                    .withRel("noopener"));
        }
        cell.with(a("Edit").withClass("gathering-edit-link")
                .withHref("/planned-gatherings/" + g.gatheringId().id()));
        return cell;
    }

    // Everything below the venue name: street, city, region, postal, country — blanks skipped.
    private static String buildAddress(PlannedGatheringView g) {
        StringBuilder sb = new StringBuilder();
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

    // Shown only once the grid stacks (see the media query); on a wide viewport the column header
    // carries these labels instead.
    private static DomContent legLabel(String text) {
        return span(text).withClass("leg-label");
    }
}

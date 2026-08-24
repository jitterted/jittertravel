package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.PlannedPrivateEventView;
import dev.ted.jittertravel.application.TimeView;
import j2html.tags.DomContent;
import j2html.tags.specialized.DivTag;

import java.util.List;

import static j2html.TagCreator.*;

public class PlannedPrivateEventsRenderer {

    private static final String DATE_FORMAT = "EEE, MMM d, yyyy";
    private static final String TIME_FORMAT = "h:mm a";

    // The gathering list's table minus its Speaking column, which a private event has no concept
    // of: When / Private Event / Venue / actions. ONE grid owns the columns — .private-event-list
    // defines the four tracks and the header and every row inherit them via
    // grid-template-columns: subgrid, so the columns line up across rows with min-content floors
    // (aligned, no overflow). No .page max-width — it fills the centered page width. Below 640px
    // the grid collapses to a single stacked column, the header hides, and each cell shows its own
    // leg label. Nothing is capped and nothing scrolls sideways.
    //
    // Slate (#475569), not the gathering's purple: it is the colour this kind already wears on the
    // itinerary (ItineraryRenderer's .entry-card--private-event).
    private static final String CSS = """
                .private-event-list {
                    display: grid;
                    grid-template-columns: auto 2fr 2fr auto;
                    column-gap: 0.75rem;
                    margin-top: 1rem;
                    background: var(--surface, #fff);
                    border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;
                }
                .private-event-header, .private-event-row {
                    grid-column: 1 / -1;
                    display: grid;
                    grid-template-columns: subgrid;
                    align-items: start; padding: 10px 16px;
                }
                .private-event-header {
                    background: var(--header-bg); color: var(--muted-text);
                    font-weight: 600; text-transform: uppercase;
                    font-size: 0.75rem; letter-spacing: 0.5px;
                    border-bottom: 1px solid var(--border-color);
                }
                .private-event-row { border-bottom: 1px solid var(--border-color); font-size: 0.9rem; }
                .private-event-row:last-child { border-bottom: none; }
                .private-event-row:hover { background: var(--hover-bg); }
                .private-event-when-date { font-weight: 700; color: #475569; white-space: nowrap; }
                .private-event-when-time { color: #64748b; font-size: 0.85rem; white-space: nowrap; margin-top: 0.1rem; }
                .private-event-title { font-weight: 700; }
                .private-event-venue-address { font-size: 0.82rem; color: var(--muted-text); margin-top: 0.1rem; }
                .private-event-actions { display: flex; flex-direction: column; gap: 0.3rem; align-items: start; }
                .private-event-cancel-link { font-size: 0.85rem; color: #475569; text-decoration: underline; }
                .empty-state { font-style: italic; font-size: 0.9rem; }
                /* Per-column labels: hidden while the header row is visible, shown once the grid stacks. */
                .leg-label {
                    display: none;
                    font-size: 0.7rem; font-weight: 600; text-transform: uppercase;
                    letter-spacing: 0.5px; color: var(--muted-text);
                }
                @media (max-width: 640px) {
                    .private-event-list { grid-template-columns: 1fr; }
                    .private-event-header { display: none; }
                    .private-event-row { grid-template-columns: 1fr; gap: 0.3rem 0; }
                    .leg-label { display: block; margin-top: 0.5rem; }
                }
            """;

    public static String render(List<PlannedPrivateEventView> privateEvents, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Planned Private Events", CSS),
                body(
                        div().withClass("page").with(
                                Page.viewNav(Page.NavAudience.OWNER, "/planned-private-events"),
                                h1("Planned Private Events"),
                                TimeFilterToggle.render("/planned-private-events", activeFilter),
                                privateEvents.isEmpty()
                                        ? div(emptyStateMessage(activeFilter)).withClass("empty-state")
                                        : renderList(privateEvents)
                        )
                )
        ).withLang("en").render();
    }

    private static String emptyStateMessage(TimeView activeFilter) {
        return activeFilter == TimeView.FUTURE
                ? "No upcoming private events."
                : "No private events planned yet.";
    }

    private static DomContent renderList(List<PlannedPrivateEventView> privateEvents) {
        return div().withClass("private-event-list").with(
                div().withClass("private-event-header").with(
                        span("When"), span("Private Event"), span("Venue"), span()
                ),
                each(privateEvents, PlannedPrivateEventsRenderer::renderRow)
        );
    }

    private static DomContent renderRow(PlannedPrivateEventView e) {
        return div().withClass("private-event-row").with(
                whenCell(e),
                titleCell(e),
                venueCell(e),
                actionsCell(e)
        );
    }

    // Venue-local wall-clock as the element text; the UTC instant rides along in the datetime
    // attribute for the browser-zone upgrade.
    private static DomContent whenCell(PlannedPrivateEventView e) {
        return div().with(
                legLabel("When"),
                div().withClass("private-event-when-date").with(ZonedTimeTag.render(e.startsAt(), DATE_FORMAT)),
                div().withClass("private-event-when-time").with(
                        ZonedTimeTag.render(e.startsAt(), TIME_FORMAT),
                        rawHtml(" &ndash; "),
                        ZonedTimeTag.render(e.endsAt(), TIME_FORMAT)
                )
        );
    }

    // No link on the title: a private event has no infoUrl, and there is no detail page to point
    // at until the edit flow ships (docs/ChangePrivateEventPlan.md slice 2).
    private static DomContent titleCell(PlannedPrivateEventView e) {
        return div().with(
                legLabel("Private Event"),
                div(e.title()).withClass("private-event-title")
        );
    }

    private static DomContent venueCell(PlannedPrivateEventView e) {
        DivTag cell = div().with(legLabel("Venue"));
        if (!e.venueName().isBlank()) {
            cell.with(div(e.venueName()).withClass("private-event-venue-name"));
        }
        String address = buildAddress(e);
        if (!address.isBlank()) {
            cell.with(div(address).withClass("private-event-venue-address"));
        }
        return cell;
    }

    /**
     * Cancel goes <em>first</em> in the cell, and that ordering is load-bearing: the cell is a
     * column flex, so the edit flow's future "Edit" link is appended <em>below</em> this one rather
     * than above it, and no control that is here today moves when it arrives (CLAUDE.md, "action
     * affordances never move").
     */
    private static DomContent actionsCell(PlannedPrivateEventView e) {
        return div().withClass("private-event-actions").with(
                a("Cancel").withClass("private-event-cancel-link")
                        .withHref("/planned-private-events/" + e.privateEventId().id() + "/cancel")
        );
    }

    // Everything below the venue name: street, city, region, postal, country — blanks skipped.
    // This is the only place a private event's street, region and postal code are ever read.
    private static String buildAddress(PlannedPrivateEventView e) {
        StringBuilder sb = new StringBuilder();
        if (!e.street().isBlank()) {
            sb.append(e.street()).append(", ");
        }
        sb.append(e.city());
        if (!e.region().isBlank()) {
            sb.append(", ").append(e.region());
        }
        if (!e.postalCode().isBlank()) {
            sb.append(" ").append(e.postalCode());
        }
        if (!e.country().isBlank()) {
            sb.append(", ").append(e.country());
        }
        return sb.toString();
    }

    // Shown only once the grid stacks (see the media query); on a wide viewport the column header
    // carries these labels instead.
    private static DomContent legLabel(String text) {
        return span(text).withClass("leg-label");
    }
}

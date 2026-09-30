package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.CancelledView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static j2html.TagCreator.*;

public class BookedFlightsRenderer {

    private static final String DATE_PATTERN = "EEE, MMM d";
    private static final String TIME_PATTERN = "h:mm a";
    private static final DateTimeFormatter CANCELLED_ON =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    // No container max-width, and the grid collapses instead of scrolling. Wide, the seven columns
    // sit side by side; at 640px and below they stack into one column, the column header hides, and each
    // info cell shows its own leg label so Departure/Arrival/Route/Airline/Flight Number stay
    // unambiguous. The empty chevron slot is dropped from plain rows when stacked, but kept on
    // history rows as their expand affordance. The time always sits on its own line under the date
    // (see the time > .nowrap rule). No page ever scrolls sideways.
    //
    // ONE grid owns the columns. .flight-cards defines the seven tracks; the header and every row
    // (and a history flight's <details> and its <summary>) inherit them with
    // grid-template-columns: subgrid, so all columns are sized once from every row's content
    // together and line up across rows — with min-content floors, so no cell overflows into the
    // next. (Separate per-row grids drift out of alignment; fixed floors instead let wide content
    // overflow and overlap.) A history flight's change list lives INSIDE its <summary> (the grid
    // row that already spans all seven columns), so the list — grid-column: 1 / -1 within that same
    // subgrid row — spans every column too. As a sibling of the summary it stayed in the first
    // column instead.
    private static final String CSS = """
            /* No side margin or padding: the list lines up with the heading and nav above it rather
               than sitting 48px in from them on each side, which was width taken from the table. */
            .conference-container { margin: 1rem 0 0; }
            .flight-cards {
                display: grid;
                /* Nothing wraps while another column has room, and this falls out of the grid's
                   own track sizing (CSS Grid, "Maximize Tracks" before "Expand Flexible Tracks"):
                   free space first grows every auto track toward its max-content, and only what
                   is left goes to the one fr track. So Departure, Arrival, Airline and Flight
                   Number reach one line before Route gets anything extra, and they wrap only when
                   the row genuinely cannot hold them. Route is the absorber.
                   Route's own claim is made by content, not here: a cancelled flight's box keeps
                   its "Cancelled <date>" line unbroken, which raises Route's min-content (its base
                   size) to that line only while such a row is on the page. Rows share these tracks
                   through subgrid, so the default list is unaffected. Tried and rejected:
                   2fr 2fr 3fr auto auto, where auto tracks eat the free space before any fr,
                   leaving Route too narrow for the box at 820px; min-content for Airline, which
                   wrapped it even with room to spare; and a 12rem min-width on the box, a guess
                   that claimed more than the line needs and wrapped the dates at 1024px.
                   inline-size containment is for the container query on the box below; this
                   element's width comes from its parent anyway, so containment changes nothing
                   about it. */
                grid-template-columns: auto auto 1fr auto auto 28px auto;
                container-type: inline-size;
                column-gap: 0.75rem;
                margin-top: 1rem;
                background-color: var(--surface, #fff);
                border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;
            }
            .flight-card-header, .flight-card-row {
                grid-column: 1 / -1;
                display: grid;
                grid-template-columns: subgrid;
                align-items: center; padding: 10px 16px;
            }
            .flight-edit-link { font-size: 0.85rem; color: var(--accent-color); text-decoration: none; }
            .flight-edit-link:hover { text-decoration: underline; }
            /* Never wraps: letting Cancel drop under Edit made the pair change shape at 820px only
               while cancelled flights were shown, so toggling the view moved the actions
               (affordances never move). The cost is a wider floor, paid by stacking the whole
               table earlier — see the media query below. */
            .flight-actions { display: flex; gap: 0.75rem; align-items: baseline; }
            /* Red, matching the confirmation page it leads to: there is no undo from inside the app. */
            .flight-cancel-link { font-size: 0.85rem; color: #b00; text-decoration: none; }
            .flight-cancel-link:hover { text-decoration: underline; }
            .flight-action-disabled { font-size: 0.85rem; color: var(--muted-text); opacity: 0.6; cursor: default; }
            /* A cancelled flight is a record, muted so it reads as inactive. */
            .flight-card--cancelled .flight-card-cell { color: var(--muted-text); }
            /* The headline line is what claims Route's width for the box (see .flight-cards): kept
               unbroken, it is the box's min-content, so the claim is exactly as wide as the text.
               Only on a table wide enough to afford it — a container query on the table itself,
               not the viewport. 41rem is measured: the narrowest table on which the seven columns'
               floors (645px with an unbroken headline) still fit; below it the headline may break
               after "Cancelled" again, or the grid would overflow. The reason always wraps inside
               the box. */
            .flight-cancelled-badge {
                display: block; width: fit-content; margin-top: 0.2rem;
                padding: 1px 7px; border-radius: 4px; font-size: 0.75rem;
                background: #f1f1f1; color: #6b7280;
            }
            @container (width >= 41rem) {
                .flight-cancelled-headline { white-space: nowrap; }
            }
            .flight-cancelled-reason { display: block; margin-top: 0.15rem; color: var(--text-color); }
            /* Its own row under the toolbar, as the conferences' jump bar, so the toolbar keeps the
               time toggle and the create link on one row. The switch copies the conferences' "Show
               dropped": the label never changes, only the box, so it says what the page shows. */
            .flight-cancelled-bar { margin-top: 0.75rem; }
            .flight-cancelled-toggle {
                display: inline-flex; align-items: center; gap: 7px;
                padding: 3px 9px; border-radius: 5px; font-size: 0.875rem;
                color: var(--text-color); text-decoration: none;
                border: 1px solid var(--border-color); background: var(--surface, #fff);
            }
            .flight-cancelled-toggle:hover { border-color: var(--accent-color); }
            .flight-cancelled-toggle[aria-pressed="true"] {
                border-color: var(--accent-color); background: #eef2ff;
            }
            .flight-cancelled-box {
                width: 14px; height: 14px; border-radius: 3px;
                display: inline-flex; align-items: center; justify-content: center;
                font-size: 10px; line-height: 1; color: #fff;
                border: 1px solid #adb5bd; background: var(--surface, #fff);
            }
            .flight-cancelled-toggle[aria-pressed="true"] .flight-cancelled-box {
                border-color: var(--accent-color); background: var(--accent-color);
            }
            .flight-card-header {
                background-color: var(--header-bg); color: var(--muted-text);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
                border-bottom: 1px solid var(--border-color);
            }
            .flight-card { grid-column: 1 / -1; border-bottom: 1px solid var(--border-color); }
            .flight-card:last-child { border-bottom: none; }
            .flight-card-has-history {
                grid-column: 1 / -1;
                display: grid; grid-template-columns: subgrid;
                background-color: rgb(255 200 200 / 0.05);
            }
            div.flight-card-row:hover { background-color: var(--hover-bg); }
            .flight-card-has-history > summary { cursor: pointer; list-style: none; }
            .flight-card-has-history > summary::-webkit-details-marker { display: none; }
            .flight-card-has-history > summary:hover { background-color: var(--hover-bg); }
            .flight-card-chevron::before {
                content: "⚡️"; color: var(--muted-text);
                transition: transform 0.15s ease; display: inline-block;
            }
            .flight-card-has-history[open] > summary .flight-card-chevron::before { transform: rotate(90deg); }
            div.flight-card-row > .flight-card-chevron::before,
            .flight-card-header > .flight-card-chevron::before { content: ""; }
            .flight-departure { font-weight: 500; }
            /* The time always on its own line under the date, at every width (Ted, 2026-09-30):
               run into the date on one line, the time is hard to pick out. The two are already
               separate .nowrap spans inside the <time>, so making each a block is the whole change;
               the vertical space is the price, paid knowingly. */
            .flight-card-row time > .nowrap { display: block; }
            /* Inside the summary's subgrid row; grid-column: 1 / -1 spans all seven columns, with
               the entries stacking in its single implicit column. It lives in the summary (not as a
               sibling) so it spans, but is hidden while the <details> is closed so the chevron still
               shows/hides it — the summary click toggles [open] as usual. */
            .flight-history-list {
                grid-column: 1 / -1;
                display: grid;
                margin: 0; padding: 4px 16px 12px 3rem; color: var(--muted-text); font-size: 0.9rem;
            }
            .flight-card-has-history:not([open]) .flight-history-list { display: none; }
            .flight-history-list li { list-style: none; margin: 0.15rem 0; }
            .flight-history-list li::before { content: "\\2022"; margin-right: 0.6rem; color: var(--muted-text); }
            .empty-state p { margin: 0.5rem 0; }
            /* Per-leg labels: hidden while the header row is visible, shown once the grid stacks. */
            .leg-label {
                display: none;
                font-size: 0.7rem; font-weight: 600; text-transform: uppercase;
                letter-spacing: 0.5px; color: var(--muted-text);
            }
            /* Measured: the columns' floors (the DEPARTURE header, an unbreakable route, the
               unwrapping Edit/Cancel, a cancelled row's box) need 586px of table, which the page
               gives from 618px. Watch this when widening a floor or the container's margins: with
               the old 48px side insets the same floors overflowed from 641 to ~715px — clipped by
               overflow: hidden rather than scrolling, so the right edge simply went missing. */
            @media (max-width: 640px) {
                .flight-cards { grid-template-columns: 1fr; }
                .flight-card-header { display: none; }
                .flight-card-row {
                    grid-template-columns: 1fr;
                    align-items: start; gap: 0.15rem;
                }
                .leg-label { display: block; margin-top: 0.5rem; }
                div.flight-card-row > .flight-card-chevron { display: none; }
                .flight-card-row > .flight-actions { justify-self: start; margin-top: 0.6rem; }
            }
            """;

    /** The default view, cancelled flights hidden and none to count. */
    public static String render(List<BookedFlightView> flights, TimeView activeFilter) {
        return render(flights, activeFilter, CancelledView.HIDE, 0);
    }

    /**
     * The page under both of its filters, each control carrying the other's value through so
     * changing one never resets the other (see {@link CancelledView}).
     *
     * @param cancelledCount how many cancelled flights the time filter admits, which the switch
     *                       reports even while they are hidden.
     */
    public static String render(List<BookedFlightView> flights, TimeView activeFilter,
                                CancelledView activeCancelled, int cancelledCount) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Booked Flights", CSS + ListToolbar.CSS),
                body(
                        Page.viewNav(Page.NavAudience.OWNER, "/booked-flights"),
                        h1("Booked Flights"),
                        div().withClass("conference-container").with(
                                ListToolbar.render("Book another flight", "/book-flight",
                                        TimeFilterToggle.render("/booked-flights", activeFilter,
                                                activeCancelled == CancelledView.SHOW ? "&cancelled=show" : "")),
                                cancelledSwitch(activeFilter, activeCancelled, cancelledCount),
                                flights.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : renderFlightList(flights)
                        )
                )
        ).withLang("en").render();
    }

    /**
     * The same control as the conferences' "Show dropped": the label never changes, only the box
     * does, so it reports what the page is showing rather than what a click would do. A link, so
     * the view stays server-rendered and shareable; {@code aria-pressed} carries the state for both
     * the screen reader and the CSS.
     */
    private static DomContent cancelledSwitch(TimeView activeFilter, CancelledView activeCancelled,
                                              int cancelledCount) {
        boolean shown = activeCancelled == CancelledView.SHOW;
        String filterQuery = "?filter=" + activeFilter.name().toLowerCase(Locale.ENGLISH);
        return div().withClass("flight-cancelled-bar").with(
                a().withClass("flight-cancelled-toggle")
                   .attr("role", "button")
                   .attr("aria-pressed", String.valueOf(shown))
                   .withTitle(shown
                           ? "Cancelled flights are shown. Click to hide them."
                           : "Cancelled flights are hidden. Click to show them.")
                   .withHref("/booked-flights" + filterQuery + (shown ? "" : "&cancelled=show"))
                   .with(shown ? span(rawHtml("&#10003;")).withClass("flight-cancelled-box")
                               : span().withClass("flight-cancelled-box"),
                         text("Show cancelled"),
                         b(String.valueOf(cancelledCount))));
    }

    private static DomContent renderEmptyState(TimeView activeFilter) {
        if (activeFilter == TimeView.FUTURE) {
            return div().withClass("empty-state").with(
                    p("No upcoming flights.")
            );
        }
        return div().withClass("empty-state").with(
                p("No flights booked yet."),
                p(a("Book a flight").withHref("/book-flight"))
        );
    }

    private static DomContent renderFlightList(List<BookedFlightView> flights) {
        return div().withClass("flight-cards").with(
                header().withClass("flight-card-header").with(
                        div("Departure"),
                        div("Arrival"),
                        div("Route"),
                        div("Airline"),
                        // Short, so the header is not the column's widest line; stacked, each cell's
                        // own leg label has the room and keeps the full "Flight Number".
                        div("Flight #"),
                        div().withClass("flight-card-chevron").attr("aria-hidden", "true"),
                        div()
                ),
                each(flights, BookedFlightsRenderer::renderFlightCard)
        );
    }

    private static DomContent renderFlightCard(BookedFlightView flight) {
        String changeUrl = "/booked-flights/" + flight.flightId().id();
        String cancelledClass = flight.cancelled() ? " flight-card--cancelled" : "";
        if (flight.hasChanges()) {
            // The change list lives inside the <summary> — the grid row that already spans and
            // aligns the seven columns — so the list (grid-column: 1 / -1 within that same subgrid
            // row) spans every column too. As a sibling of the summary it stayed stuck in the first
            // column.
            return details().withClass("flight-card flight-card-has-history" + cancelledClass).with(
                    summary().withClass("flight-card-row")
                            .with(rowCells(flight, changeUrl))
                            .with(ul().withClass("flight-history-list").with(
                                    each(flight.history(), entry -> li(entry.displayText()))
                            ))
            );
        }
        return div().withClass("flight-card flight-card-row" + cancelledClass)
                    .with(rowCells(flight, changeUrl));
    }

    // The plain row and the history summary row share the same seven grid cells, so both pick up the
    // stacking times, the leg labels, and the collapse behaviour from one place.
    private static DomContent[] rowCells(BookedFlightView flight, String changeUrl) {
        return new DomContent[]{
                div().withClass("flight-card-cell flight-departure").with(
                        legLabel("Departure"), dateTime(flight.departureDateTime())),
                div().withClass("flight-card-cell").with(
                        legLabel("Arrival"), dateTime(flight.arrivalDateTime())),
                div().withClass("flight-card-cell").with(
                        legLabel("Route"), text(flight.route()),
                        // A ternary, not iff(): iff evaluates its argument eagerly, and a live
                        // flight has no cancelledOn to format.
                        flight.cancelled() ? cancelledBadge(flight) : text("")),
                div().withClass("flight-card-cell").with(
                        legLabel("Airline"), text(flight.airline())),
                div().withClass("flight-card-cell").with(
                        legLabel("Flight Number"), text(flight.flightNumber())),
                div().withClass("flight-card-cell flight-card-chevron").attr("aria-hidden", "true"),
                flight.cancelled() ? disabledActions() : actions(changeUrl)
        };
    }

    // Cancel renders *after* Edit so Edit keeps its position (affordances never move).
    private static DomContent actions(String changeUrl) {
        return div().withClass("flight-actions").with(
                a("Edit").withClass("flight-edit-link").withHref(changeUrl),
                a("Cancel").withClass("flight-cancel-link").withHref(changeUrl + "/cancel"));
    }

    /**
     * Greyed, not removed, and in the same slots: "already cancelled" is state, not permission, so
     * the actions stay visible with the reason (CLAUDE.md, action affordances). Spans, never
     * disabled links — there is nothing to follow. Unlike {@code /booked-hotels}, whose cancelled
     * rows leave the cell empty; that predates the rule.
     */
    private static DomContent disabledActions() {
        String why = "This flight was cancelled";
        return div().withClass("flight-actions").with(
                span("Edit").withClass("flight-action-disabled").withTitle(why),
                span("Cancel").withClass("flight-action-disabled").withTitle(why));
    }

    /**
     * One box saying when and why the flight was cancelled (Ted, 2026-09-29). The date is shown in
     * the departure airport's zone: {@code cancelledOn} is an instant, and the flight's own zone is
     * the one on the rest of the row. The reason, when one was given, is written out inside the box
     * rather than hidden in a tooltip: the iPad has no hover, and the extra height is fine on a list
     * you opted into.
     */
    private static DomContent cancelledBadge(BookedFlightView flight) {
        String on = CANCELLED_ON.format(flight.cancelledOn().atZone(flight.departureDateTime().zone()));
        // The date is one no-break unit, so a narrow Route column breaks only after "Cancelled";
        // the headline span is what the container query keeps whole when the table has room.
        var badge = span(span(text("Cancelled "), span(on).withClass("nowrap"))
                                 .withClass("flight-cancelled-headline"))
                .withClass("flight-cancelled-badge");
        return flight.cancellationReason().isBlank()
                ? badge
                : badge.with(span(flight.cancellationReason()).withClass("flight-cancelled-reason"));
    }

    // Shown only once the grid stacks (see the media query); on a wide viewport the column header
    // carries these labels instead.
    private static DomContent legLabel(String text) {
        return span(text).withClass("leg-label");
    }

    private static DomContent dateTime(ZonedTimestamp when) {
        return ZonedTimeTag.renderDateTimeStacking(when, DATE_PATTERN, TIME_PATTERN);
    }
}

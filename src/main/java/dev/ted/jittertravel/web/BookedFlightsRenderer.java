package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;

import java.util.List;

import static j2html.TagCreator.*;

public class BookedFlightsRenderer {

    private static final String DATE_PATTERN = "EEE, MMM d";
    private static final String TIME_PATTERN = "h:mm a";

    // No container max-width, and the grid collapses instead of scrolling. Wide, the seven columns
    // sit side by side; below 640px they stack into one column, the column header hides, and each
    // info cell shows its own leg label so Departure/Arrival/Route/Airline/Flight Number stay
    // unambiguous. The empty chevron slot is dropped from plain rows when stacked, but kept on
    // history rows as their expand affordance. Times break between date and time (each a .nowrap
    // unit). No page ever scrolls sideways.
    //
    // Each row (and the header) is its own grid, but the column template is content-INDEPENDENT:
    // minmax(<fixed>, <fr>) gives every track a fixed floor plus a flexible share, so all rows
    // resolve to identical tracks and the columns line up across rows. (Plain fr tracks take an
    // auto/min-content floor that differs per row, so their columns drift out of alignment — worst
    // when the window narrows and the slack that hides the difference is gone.) The fixed floors are
    // chosen to hold each column's widest value, so the unbreakable Route/Airline/Flight Number
    // tokens never shrink below their text and overlap the next column; the floors total ~580px, so
    // nothing overflows above the 640px collapse point either. .flight-cards stays a flex column, so
    // a history flight's change list is just an ordinary full-width block under its summary.
    private static final String CSS = """
            .conference-container { margin: 2rem; padding: 0 1rem; }
            .flight-cards {
                display: flex; flex-direction: column; margin-top: 1rem;
                background-color: var(--surface, #fff);
                border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;
            }
            .flight-card-header, .flight-card-row {
                display: grid;
                grid-template-columns:
                    minmax(5rem, 2fr) minmax(5rem, 2fr) minmax(4.5rem, 1fr)
                    minmax(6rem, 2fr) minmax(4rem, 1fr) 28px 3.5rem;
                align-items: center; gap: 0.75rem; padding: 10px 16px;
            }
            .flight-edit-link { font-size: 0.85rem; color: var(--accent-color); text-decoration: none; }
            .flight-edit-link:hover { text-decoration: underline; }
            .flight-card-header {
                background-color: var(--header-bg); color: var(--muted-text);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
                border-bottom: 1px solid var(--border-color);
            }
            .flight-card { border-bottom: 1px solid var(--border-color); }
            .flight-card:last-child { border-bottom: none; }
            .flight-card-has-history { background-color: rgb(255 200 200 / 0.05); }
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
            .flight-history-list { margin: 0; padding: 0 16px 12px 3rem; list-style: disc; color: var(--muted-text); font-size: 0.9rem; }
            .flight-history-list li { margin: 0.15rem 0; }
            .empty-state p { margin: 0.5rem 0; }
            /* Per-leg labels: hidden while the header row is visible, shown once the grid stacks. */
            .leg-label {
                display: none;
                font-size: 0.7rem; font-weight: 600; text-transform: uppercase;
                letter-spacing: 0.5px; color: var(--muted-text);
            }
            @media (max-width: 640px) {
                .flight-card-header { display: none; }
                .flight-card-row {
                    grid-template-columns: 1fr;
                    align-items: start; gap: 0.15rem;
                }
                .leg-label { display: block; margin-top: 0.5rem; }
                div.flight-card-row > .flight-card-chevron { display: none; }
                .flight-card-row > .flight-edit-link { justify-self: start; margin-top: 0.6rem; }
            }
            """;

    public static String render(List<BookedFlightView> flights, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Booked Flights", CSS),
                body(
                        nav(a("JitterTravel").withHref("/")),
                        h1("Booked Flights"),
                        div().withClass("conference-container").with(
                                TimeFilterToggle.render("/booked-flights", activeFilter),
                                flights.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : renderFlightList(flights),
                                br(),
                                a("Book another flight").withHref("/book-flight")
                        )
                )
        ).withLang("en").render();
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
                        div("Flight Number"),
                        div().withClass("flight-card-chevron").attr("aria-hidden", "true"),
                        div()
                ),
                each(flights, BookedFlightsRenderer::renderFlightCard)
        );
    }

    private static DomContent renderFlightCard(BookedFlightView flight) {
        String changeUrl = "/booked-flights/" + flight.flightId().id();
        if (flight.hasChanges()) {
            return details().withClass("flight-card flight-card-has-history").with(
                    summary().withClass("flight-card-row").with(rowCells(flight, changeUrl)),
                    ul().withClass("flight-history-list").with(
                            each(flight.history(), entry -> li(entry.displayText()))
                    )
            );
        }
        return div().withClass("flight-card flight-card-row").with(rowCells(flight, changeUrl));
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
                        legLabel("Route"), text(flight.route())),
                div().withClass("flight-card-cell").with(
                        legLabel("Airline"), text(flight.airline())),
                div().withClass("flight-card-cell").with(
                        legLabel("Flight Number"), text(flight.flightNumber())),
                div().withClass("flight-card-cell flight-card-chevron").attr("aria-hidden", "true"),
                a("Edit").withClass("flight-edit-link").withHref(changeUrl)
        };
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

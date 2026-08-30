package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedTrainView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import j2html.tags.DomContent;

import java.util.List;

import static j2html.TagCreator.*;

public class BookedTrainsRenderer {

    private static final String DATE_PATTERN = "EEE, MMM d";
    private static final String TIME_PATTERN = "h:mm a";

    // No container max-width, and the grid collapses instead of scrolling. On a wide viewport the
    // five columns sit side by side; below 640px they stack into one column, the column header is
    // hidden, and each stacked cell shows its own leg label so Departure/Departs/Arrival/Arrives
    // stay unambiguous. The times break between date and time (each a .nowrap unit). No page ever
    // scrolls sideways.
    //
    // ONE grid owns the columns: .train-cards defines the five tracks and the header and every row
    // inherit them with grid-template-columns: subgrid, so all columns are sized once from every
    // row's content together and line up across rows — with min-content floors, so a long station
    // name never overflows into the next column. (Separate per-row grids size their tracks from
    // their own content and drift out of alignment.)
    private static final String CSS = """
            .trains-container { margin: 2rem; padding: 0 1rem; }
            .train-cards {
                display: grid;
                grid-template-columns: 2fr 1fr 2fr 1fr auto;
                column-gap: 0.75rem;
                margin-top: 1rem;
                background-color: var(--surface, #fff);
                border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;
            }
            .train-card-header, .train-card-row {
                grid-column: 1 / -1;
                display: grid;
                grid-template-columns: subgrid;
                align-items: center; padding: 10px 16px;
            }
            .train-edit-link { font-size: 0.85rem; color: var(--accent-color, #0a58ca); text-decoration: none; }
            .train-edit-link:hover { text-decoration: underline; }
            .train-card-header {
                background-color: var(--header-bg, #f8f9fa); color: var(--muted-text, #6c757d);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
                border-bottom: 1px solid var(--border-color, #dee2e6);
            }
            .train-card { grid-column: 1 / -1; border-bottom: 1px solid var(--border-color, #dee2e6); }
            .train-card:last-child { border-bottom: none; }
            .train-card-row:hover { background-color: var(--hover-bg, #f8f9fa); }
            .station-name { font-weight: 500; }
            .station-city { font-size: 0.85rem; color: var(--muted-text, #6c757d); }
            /* Per-leg labels: hidden while the header row is visible, shown once the grid stacks. */
            .leg-label {
                display: none;
                font-size: 0.7rem; font-weight: 600; text-transform: uppercase;
                letter-spacing: 0.5px; color: var(--muted-text, #6c757d);
            }
            @media (max-width: 640px) {
                .train-cards { grid-template-columns: 1fr; }
                .train-card-header { display: none; }
                .train-card-row {
                    grid-template-columns: 1fr;
                    align-items: start; gap: 0.15rem;
                }
                .leg-label { display: block; margin-top: 0.5rem; }
                .train-card-row > .train-edit-link { justify-self: start; margin-top: 0.6rem; }
            }
            """;

    public static String render(List<BookedTrainView> trains, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Booked Trains", CSS),
                body(
                        Page.viewNav(Page.NavAudience.OWNER, "/booked-trains"),
                        div().withClass("trains-container").with(
                                h1("Booked Trains"),
                                TimeFilterToggle.render("/booked-trains", activeFilter),
                                trains.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : renderTrainList(trains),
                                br(),
                                a("Book another train").withHref("/book-train")
                        )
                )
        ).withLang("en").render();
    }

    private static DomContent renderEmptyState(TimeView activeFilter) {
        String message = activeFilter == TimeView.FUTURE
                ? "No upcoming trains."
                : "No train trips booked yet.";
        return p(message).withClass("empty-state");
    }

    private static DomContent renderTrainList(List<BookedTrainView> trains) {
        return div().withClass("train-cards").with(
                div().withClass("train-card-header").with(
                        span("Departure"),
                        span("Departs"),
                        span("Arrival"),
                        span("Arrives"),
                        span()
                ),
                each(trains, BookedTrainsRenderer::renderTrainCard)
        );
    }

    // .train-card and .train-card-row are the same element: the row is a direct child of the
    // .train-cards grid so it can inherit the columns via subgrid, and .train-card gives it the
    // separating border.
    private static DomContent renderTrainCard(BookedTrainView train) {
        return div().withClass("train-card train-card-row").with(
                div().with(
                        legLabel("Departure"),
                        stationNameElement(train.departureStationName(), train.departureMapsUrl()),
                        div(train.departureCity()).withClass("station-city"),
                        train.serviceId().isBlank()
                                ? span()
                                : div(train.serviceId()).withClass("station-city")
                ),
                div().with(
                        legLabel("Departs"),
                        dateTime(train.departureDateTime())
                ),
                div().with(
                        legLabel("Arrival"),
                        stationNameElement(train.arrivalStationName(), train.arrivalMapsUrl()),
                        div(train.arrivalCity()).withClass("station-city")
                ),
                div().with(
                        legLabel("Arrives"),
                        dateTime(train.arrivalDateTime())
                ),
                a("Edit").withClass("train-edit-link")
                        .withHref("/booked-trains/" + train.tripId().id())
        );
    }

    // Shown only once the grid stacks (see the media query); on a wide viewport the column header
    // carries these labels instead.
    private static DomContent legLabel(String text) {
        return span(text).withClass("leg-label");
    }

    private static DomContent dateTime(ZonedTimestamp when) {
        return ZonedTimeTag.renderDateTimeStacking(when, DATE_PATTERN, TIME_PATTERN);
    }

    /**
     * A URL of nothing but whitespace is no URL: {@code isBlank}, not {@code isEmpty}, so it renders
     * as plain text rather than as a link to " ". Forms trim before this can happen
     * ({@code TrimTypedTextAdvice}); a restored payload written before they did would not.
     */
    private static DomContent stationNameElement(String name, String mapsUrl) {
        if (!mapsUrl.isBlank()) {
            return a(name).withHref(mapsUrl).withTarget("_blank").withRel("noopener")
                         .withClass("station-name");
        }
        return span(name).withClass("station-name");
    }
}

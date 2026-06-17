package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedTrainView;
import dev.ted.jittertravel.application.TimeView;
import j2html.tags.DomContent;

import java.util.List;

import static j2html.TagCreator.*;

public class BookedTrainsRenderer {

    private static final String CSS = """
            .trains-container { max-width: 140ch; margin: 2rem; }
            .train-cards {
                display: flex; flex-direction: column; margin-top: 1rem;
                background-color: var(--surface, #fff);
                border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;
            }
            .train-card-header, .train-card-row {
                display: grid;
                grid-template-columns: 2fr 1fr 2fr 1fr auto;
                align-items: center; gap: 0.75rem; padding: 10px 16px;
            }
            .train-edit-link { font-size: 0.85rem; color: var(--accent-color, #0a58ca); text-decoration: none; }
            .train-edit-link:hover { text-decoration: underline; }
            .train-card-header {
                background-color: var(--header-bg, #f8f9fa); color: var(--muted-text, #6c757d);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
                border-bottom: 1px solid var(--border-color, #dee2e6);
            }
            .train-card { border-bottom: 1px solid var(--border-color, #dee2e6); }
            .train-card:last-child { border-bottom: none; }
            .train-card-row:hover { background-color: var(--hover-bg, #f8f9fa); }
            .station-name { font-weight: 500; }
            .station-city { font-size: 0.85rem; color: var(--muted-text, #6c757d); }
            """;

    public static String render(List<BookedTrainView> trains, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Booked Trains", CSS),
                body(
                        nav(h3(a("JitterTravel").withHref("/"))),
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

    private static DomContent renderTrainCard(BookedTrainView train) {
        return div().withClass("train-card").with(
                div().withClass("train-card-row").with(
                        div().with(
                                stationNameElement(train.departureStationName(), train.departureMapsUrl()),
                                div(train.departureCity()).withClass("station-city"),
                                train.serviceId().isEmpty()
                                        ? span()
                                        : div(train.serviceId()).withClass("station-city")
                        ),
                        div(train.departureDateTimeDisplay()),
                        div().with(
                                stationNameElement(train.arrivalStationName(), train.arrivalMapsUrl()),
                                div(train.arrivalCity()).withClass("station-city")
                        ),
                        div(train.arrivalDateTimeDisplay()),
                        a("Edit").withClass("train-edit-link")
                                .withHref("/booked-trains/" + train.tripId().id())
                )
        );
    }

    private static DomContent stationNameElement(String name, String mapsUrl) {
        if (!mapsUrl.isEmpty()) {
            return a(name).withHref(mapsUrl).withTarget("_blank").withRel("noopener")
                         .withClass("station-name");
        }
        return span(name).withClass("station-name");
    }
}

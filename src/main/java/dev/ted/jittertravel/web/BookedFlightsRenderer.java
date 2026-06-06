package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedFlightView;
import j2html.tags.DomContent;

import java.util.List;

import static j2html.TagCreator.*;

public class BookedFlightsRenderer {

    private static final String CSS = """
            .conference-container { max-width: 100ch; margin: 2rem; padding: 0 1rem; }
            .flight-cards {
                display: flex; flex-direction: column; margin-top: 1rem;
                background-color: var(--surface, #fff);
                border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;
            }
            .flight-card-header, .flight-card-row {
                display: grid;
                grid-template-columns: 2fr 1fr 2fr 1fr 28px;
                align-items: center; gap: 0.75rem; padding: 10px 16px;
            }
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
            .conf-name { font-weight: 500; color: var(--accent-color); }
            .flight-history-list { margin: 0; padding: 0 16px 12px 3rem; list-style: disc; color: var(--muted-text); font-size: 0.9rem; }
            .flight-history-list li { margin: 0.15rem 0; }
            .empty-state { margin-top: 1rem; color: var(--muted-text); }
            .empty-state p { margin: 0.5rem 0; }
            """;

    public static String render(List<BookedFlightView> flights) {
        return "<!DOCTYPE html>\n" + html(
                head(
                        meta().withCharset("UTF-8"),
                        title("Booked Flights"),
                        link().withRel("stylesheet").withHref("/site.css"),
                        rawHtml("<style>" + CSS + "</style>")
                ),
                body(
                        nav(a("JitterTravel").withHref("/")),
                        h1("Booked Flights"),
                        div().withClass("conference-container").with(
                                flights.isEmpty() ? renderEmptyState() : renderFlightList(flights),
                                br(),
                                a("Book another flight").withHref("/book-flight")
                        )
                )
        ).withLang("en").render();
    }

    private static DomContent renderEmptyState() {
        return div().withClass("empty-state").with(
                p("No flights booked yet."),
                p(a("Book a flight").withHref("/book-flight"))
        );
    }

    private static DomContent renderFlightList(List<BookedFlightView> flights) {
        return div().withClass("flight-cards").with(
                header().withClass("flight-card-header").with(
                        div("Departure"),
                        div("Route"),
                        div("Airline"),
                        div("Flight Number"),
                        div().withClass("flight-card-chevron").attr("aria-hidden", "true")
                ),
                each(flights, BookedFlightsRenderer::renderFlightCard)
        );
    }

    private static DomContent renderFlightCard(BookedFlightView flight) {
        String changeUrl = "/booked-flights/" + flight.flightId().id();
        if (flight.hasChanges()) {
            return details().withClass("flight-card flight-card-has-history").with(
                    summary().withClass("flight-card-row").with(
                            div(a(flight.departureDateTimeDisplay()).withHref(changeUrl))
                                    .withClass("flight-card-cell conf-name"),
                            div(flight.route()).withClass("flight-card-cell"),
                            div(flight.airline()).withClass("flight-card-cell"),
                            div(flight.flightNumber()).withClass("flight-card-cell"),
                            div().withClass("flight-card-cell flight-card-chevron").attr("aria-hidden", "true")
                    ),
                    ul().withClass("flight-history-list").with(
                            each(flight.history(), entry -> li(entry.displayText()))
                    )
            );
        }
        return div().withClass("flight-card flight-card-row").with(
                div(a(flight.departureDateTimeDisplay()).withHref(changeUrl))
                        .withClass("flight-card-cell conf-name"),
                div(flight.route()).withClass("flight-card-cell"),
                div(flight.airline()).withClass("flight-card-cell"),
                div(flight.flightNumber()).withClass("flight-card-cell"),
                div().withClass("flight-card-cell flight-card-chevron").attr("aria-hidden", "true")
        );
    }
}

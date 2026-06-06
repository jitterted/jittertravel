package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.domain.BookingIntent;
import j2html.tags.DomContent;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static j2html.TagCreator.*;

public class BookedHotelsRenderer {

    private static final DateTimeFormatter DATE_DISPLAY =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH);

    private static final String CSS = """
            .page { max-width: 900px; margin: 0 auto; padding: 1rem; }
            nav { margin-bottom: 1rem; font-size: 0.9rem; }
            nav a { color: var(--accent-color); text-decoration: none; }
            nav a:hover { text-decoration: underline; }
            nav .sep { color: var(--muted-text); margin: 0 0.4em; }
            h1 { margin: 0 0 1rem; font-size: 1.4rem; }
            .hotel-table {
                width: 100%; border-collapse: collapse;
                background: var(--surface, #fff);
                border-radius: 8px; overflow: hidden;
                box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            }
            .hotel-table th {
                background: var(--header-bg); color: var(--muted-text);
                font-weight: 600; text-transform: uppercase;
                font-size: 0.75rem; letter-spacing: 0.5px;
                padding: 10px 16px; text-align: left;
                border-bottom: 1px solid var(--border-color);
            }
            .hotel-table td {
                padding: 10px 16px;
                border-bottom: 1px solid var(--border-color);
                font-size: 0.9rem;
            }
            .hotel-table tr:last-child td { border-bottom: none; }
            .hotel-table tr:hover td { background: var(--hover-bg); }
            .status-badge {
                display: inline-block; font-size: 0.72rem; font-weight: 600;
                text-transform: uppercase; letter-spacing: 0.05em;
                padding: 2px 8px; border-radius: 999px;
            }
            .status-tentative { background: #fef3c7; color: #92400e; }
            .status-final { background: #dcfce7; color: #166534; }
            .empty-state { margin-top: 1rem; color: var(--muted-text); }
            .action-row { margin-top: 1rem; }
            .action-row a { color: var(--accent-color); text-decoration: none; font-size: 0.9rem; }
            .action-row a:hover { text-decoration: underline; }
            """;

    public static String render(List<BookedHotelView> hotels) {
        return "<!DOCTYPE html>\n" + html(
                head(
                        meta().withCharset("UTF-8"),
                        title("Booked Hotels"),
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
                                h1("Booked Hotels"),
                                hotels.isEmpty() ? renderEmptyState() : renderTable(hotels),
                                div().withClass("action-row").with(
                                        a("Book another hotel").withHref("/book-hotel")
                                )
                        )
                )
        ).withLang("en").render();
    }

    private static DomContent renderEmptyState() {
        return p("No hotel bookings yet.").withClass("empty-state");
    }

    private static DomContent renderTable(List<BookedHotelView> hotels) {
        return table().withClass("hotel-table").with(
                thead(
                        tr(
                                th("Hotel"),
                                th("Location"),
                                th("Check-In"),
                                th("Check-Out"),
                                th("Status")
                        )
                ),
                tbody(
                        each(hotels, BookedHotelsRenderer::renderRow)
                )
        );
    }

    private static DomContent renderRow(BookedHotelView hotel) {
        return tr(
                td(a(hotel.hotelName()).withHref(hotel.mapsUrl())
                        .withTarget("_blank").withRel("noopener")),
                td(hotel.city() + ", " + hotel.country()),
                td(hotel.checkIn().format(DATE_DISPLAY)),
                td(hotel.checkOut().format(DATE_DISPLAY)),
                td(statusBadge(hotel.status()))
        );
    }

    private static DomContent statusBadge(BookingIntent status) {
        if (status == BookingIntent.TENTATIVE) {
            return span("Tentative").withClass("status-badge status-tentative");
        }
        return span("Final").withClass("status-badge status-final");
    }
}

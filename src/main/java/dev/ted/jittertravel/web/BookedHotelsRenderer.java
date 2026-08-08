package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.BookingIntent;
import j2html.tags.DomContent;

import java.util.List;

import static j2html.TagCreator.*;

public class BookedHotelsRenderer {

    private static final String DATE_DISPLAY_PATTERN = "EEE, MMM d, h:mm a";

    private static final String CSS = """
            .page { max-width: 900px; }
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
                text-wrap-mode: nowrap;
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
            .action-row { margin-top: 1rem; }
            .action-row a { color: var(--accent-color); text-decoration: none; font-size: 0.9rem; }
            .action-row a:hover { text-decoration: underline; }
            .hotel-edit-link { color: var(--accent-color); text-decoration: none; font-size: 0.85rem; }
            .hotel-edit-link:hover { text-decoration: underline; }
            .no-deadline { color: var(--muted-text); }
            /* Neutral, not alarming: a passed deadline costs nothing here, it just means free
               cancellation is over. The stay is still cancellable until check-in. */
            .deadline-passed { color: var(--muted-text); text-decoration: line-through; }
            """;

    public static String render(List<BookedHotelView> hotels, TimeView activeFilter) {
        return "<!DOCTYPE html>\n" + html(
                Page.head("Booked Hotels", CSS),
                body(
                        div().withClass("page").with(
                                Page.navHomeAndCalendar(),
                                h1("Booked Hotels"),
                                TimeFilterToggle.render("/booked-hotels", activeFilter),
                                hotels.isEmpty()
                                        ? renderEmptyState(activeFilter)
                                        : renderTable(hotels),
                                div().withClass("action-row").with(
                                        a("Book another hotel").withHref("/book-hotel")
                                )
                        )
                )
        ).withLang("en").render();
    }

    private static DomContent renderEmptyState(TimeView activeFilter) {
        String message = activeFilter == TimeView.FUTURE
                ? "No upcoming hotel stays."
                : "No hotel bookings yet.";
        return p(message).withClass("empty-state");
    }

    private static DomContent renderTable(List<BookedHotelView> hotels) {
        return table().withClass("hotel-table").with(
                thead(
                        tr(
                                th("Hotel"),
                                th("Location"),
                                th("Check-In"),
                                th("Check-Out"),
                                th("Cancel By"),
                                th("Status"),
                                th()
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
                td(ZonedTimeTag.render(hotel.checkIn(), DATE_DISPLAY_PATTERN)),
                td(ZonedTimeTag.render(hotel.checkOut(), DATE_DISPLAY_PATTERN)),
                cancelByCell(hotel),
                td(statusBadge(hotel.status())),
                td(a("Edit").withClass("hotel-edit-link")
                        .withHref("/booked-hotels/" + hotel.hotelBookingId().id()))
        );
    }

    /**
     * ZonedTimeTag is safe here: {@code /booked-hotels} is OWNER-only, so the UTC instant it writes
     * into the {@code datetime} attribute is not a redaction concern. The deadline must never reach
     * {@code CalendarEntry}, which anonymous visitors can see.
     */
    private static DomContent cancelByCell(BookedHotelView hotel) {
        if (hotel.cancelBy() == null) {
            return td(rawHtml("&mdash;")).withClass("no-deadline");
        }
        DomContent deadline = ZonedTimeTag.render(hotel.cancelBy(), DATE_DISPLAY_PATTERN);
        return hotel.cancelDeadlinePassed()
                ? td(deadline).withClass("deadline-passed").withTitle("Free cancellation has ended")
                : td(deadline);
    }

    private static DomContent statusBadge(BookingIntent status) {
        if (status == BookingIntent.TENTATIVE) {
            return span("Tentative").withClass("status-badge status-tentative");
        }
        return span("Final").withClass("status-badge status-final");
    }
}

package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookedHotelsRendererTest {

    // Berlin is CEST (+02:00) on these July dates, so the UTC instants are two hours earlier.
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final ZonedTimestamp CHECK_IN =
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 15, 0), ZONE);
    private static final ZonedTimestamp CHECK_OUT =
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 5, 11, 0), ZONE);
    private static final ZonedTimestamp CANCEL_BY =
            ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 24, 12, 0), ZONE);

    @Test
    void emptyAllListRendersBookedYetMessage() {
        String html = BookedHotelsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html).contains("No hotel bookings yet.");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = BookedHotelsRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html).contains("No upcoming hotel stays.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = BookedHotelsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/booked-hotels?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/booked-hotels?filter=future\">Upcoming</a>");
    }

    @Test
    void rowRendersEditLinkToBookingEditPage() {
        HotelBookingId id = HotelBookingId.random();
        BookedHotelView view = new BookedHotelView(
                id, "Grand Hotel", "Berlin", "Germany",
                CHECK_IN, CHECK_OUT, BookingIntent.FINAL, "https://maps.google.com/",
                null, false);

        String html = BookedHotelsRenderer.render(List.of(view), TimeView.FUTURE);

        assertThat(html)
                .contains("href=\"/booked-hotels/" + id.id() + "\"")
                .contains(">Edit</a>");
    }

    @Test
    void hotelNameRendersAsLinkToMapsUrl() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Grand Hotel",
                "https://maps.google.com/grand", BookingIntent.TENTATIVE)), TimeView.FUTURE);

        assertThat(html)
                .contains("<a href=\"https://maps.google.com/grand\"")
                .contains("Grand Hotel");
    }

    @Test
    void locationShowsCityAndCountry() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.FINAL)), TimeView.FUTURE);

        assertThat(html).contains("Berlin, Germany");
    }

    @Test
    void checkInAndCheckOutDatesAreFormatted() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.TENTATIVE)), TimeView.FUTURE);

        assertThat(html)
                .contains("Wed, Jul 1, 3:00 PM")
                .contains("Sun, Jul 5, 11:00 AM");
    }

    @Test
    void datesRenderAsTimeElementsCarryingTheUtcInstant() {
        // Baseline of the browser-zone display: visible text is entry-local, the datetime
        // attribute carries the UTC instant (Berlin CEST 15:00 -> 13:00Z), data-fmt records
        // the pattern a future viewer-zone script would reuse.
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.TENTATIVE)), TimeView.FUTURE);

        assertThat(html)
                .contains("<time datetime=\"2026-07-01T13:00:00Z\" data-fmt=\"EEE, MMM d, h:mm a\">"
                        + "Wed, Jul 1, 3:00 PM</time>")
                .contains("<time datetime=\"2026-07-05T09:00:00Z\" data-fmt=\"EEE, MMM d, h:mm a\">"
                        + "Sun, Jul 5, 11:00 AM</time>");
    }

    @Test
    void tentativeStatusRendersTentativeBadge() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.TENTATIVE)), TimeView.FUTURE);

        assertThat(html)
                .contains("status-tentative")
                .contains("Tentative");
    }

    @Test
    void finalStatusRendersFinalBadge() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.FINAL)), TimeView.FUTURE);

        assertThat(html)
                .contains("status-final")
                .contains("Final");
    }

    @Test
    void cancelByColumnRendersDeadlineAsTimeElement() {
        String html = BookedHotelsRenderer.render(
                List.of(hotelView("Any Hotel", "https://maps.google.com/", BookingIntent.FINAL,
                        CANCEL_BY, false)), TimeView.FUTURE);

        assertThat(html)
                .contains("<th>Cancel By</th>")
                .contains("<time datetime=\"2026-06-24T10:00:00Z\" data-fmt=\"EEE, MMM d, h:mm a\">"
                        + "Wed, Jun 24, 12:00 PM</time>");
    }

    @Test
    void absentDeadlineRendersEmDashInsteadOfATime() {
        String html = BookedHotelsRenderer.render(
                List.of(hotelView("Any Hotel", "https://maps.google.com/", BookingIntent.FINAL,
                        null, false)), TimeView.FUTURE);

        // Note the cell, not the bare class name — the stylesheet always defines .deadline-passed.
        assertThat(html)
                .contains("<td class=\"no-deadline\">&mdash;</td>")
                .doesNotContain("<td class=\"deadline-passed\"");
    }

    @Test
    void passedDeadlineIsStyledButStillShown() {
        String html = BookedHotelsRenderer.render(
                List.of(hotelView("Any Hotel", "https://maps.google.com/", BookingIntent.FINAL,
                        CANCEL_BY, true)), TimeView.FUTURE);

        assertThat(html)
                .contains("<td class=\"deadline-passed\"")
                .contains("Wed, Jun 24, 12:00 PM");
    }

    private static BookedHotelView hotelView(String name, String mapsUrl, BookingIntent status) {
        return hotelView(name, mapsUrl, status, null, false);
    }

    private static BookedHotelView hotelView(String name, String mapsUrl, BookingIntent status,
                                             ZonedTimestamp cancelBy, boolean deadlinePassed) {
        return new BookedHotelView(
                HotelBookingId.random(),
                name,
                "Berlin", "Germany",
                CHECK_IN, CHECK_OUT,
                status,
                mapsUrl,
                cancelBy,
                deadlinePassed
        );
    }
}

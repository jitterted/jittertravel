package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelView;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookedHotelsRendererTest {

    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 7, 1, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 7, 5, 11, 0);

    @Test
    void emptyListRendersEmptyStateMessage() {
        String html = BookedHotelsRenderer.render(List.of());

        assertThat(html).contains("No hotel bookings yet.");
    }

    @Test
    void hotelNameRendersAsLinkToMapsUrl() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Grand Hotel",
                "https://maps.google.com/grand", BookingIntent.TENTATIVE)));

        assertThat(html)
                .contains("<a href=\"https://maps.google.com/grand\"")
                .contains("Grand Hotel");
    }

    @Test
    void locationShowsCityAndCountry() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.FINAL)));

        assertThat(html).contains("Berlin, Germany");
    }

    @Test
    void checkInAndCheckOutDatesAreFormatted() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.TENTATIVE)));

        assertThat(html)
                .contains("Wed, Jul 1, 3:00 PM")
                .contains("Sun, Jul 5, 11:00 AM");
    }

    @Test
    void tentativeStatusRendersTentativeBadge() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.TENTATIVE)));

        assertThat(html)
                .contains("status-tentative")
                .contains("Tentative");
    }

    @Test
    void finalStatusRendersFinalBadge() {
        String html = BookedHotelsRenderer.render(List.of(hotelView("Any Hotel",
                "https://maps.google.com/", BookingIntent.FINAL)));

        assertThat(html)
                .contains("status-final")
                .contains("Final");
    }

    private static BookedHotelView hotelView(String name, String mapsUrl, BookingIntent status) {
        return new BookedHotelView(
                HotelBookingId.random(),
                name,
                "Berlin", "Germany",
                CHECK_IN, CHECK_OUT,
                status,
                mapsUrl
        );
    }
}

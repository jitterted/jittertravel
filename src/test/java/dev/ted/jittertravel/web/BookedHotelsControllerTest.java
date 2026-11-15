package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedHotelsProjector;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The controller reads {@code ?cancelled=} and hands the projector's answer to the renderer; a real
 * projector fed real events, so what the page lists is what the events say.
 */
class BookedHotelsControllerTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    private final BookedHotelsProjector projector = new BookedHotelsProjector();
    private final BookedHotelsController controller =
            new BookedHotelsController(projector, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void canceledStaysAreHiddenByDefaultAndTheSwitchCountsThem() {
        bookLiveAndCanceledStays();

        String body = controller.bookedHotels(null, null).getBody();

        assertThat(body)
                .contains(">Hotel Moxy</a>")
                .doesNotContain("Hotel Adlon")
                .contains("aria-pressed=\"false\"")
                .contains("Show Canceled Hotels<b>1</b>");
    }

    @Test
    void cancelledShowListsTheCanceledStayAndKeepsTheTimeFilter() {
        bookLiveAndCanceledStays();

        String body = controller.bookedHotels("all", "show").getBody();

        assertThat(body)
                .contains(">Hotel Moxy</a>")
                .contains("<td>Hotel Adlon</td>")
                .contains("aria-pressed=\"true\"")
                .contains("<a href=\"/booked-hotels?filter=all&amp;cancelled=show\" class=\"active\">All</a>");
    }

    private void bookLiveAndCanceledStays() {
        HotelBooked live = hotelBooked("Hotel Moxy", LocalDateTime.of(2026, 7, 1, 15, 0));
        HotelBooked canceled = hotelBooked("Hotel Adlon", LocalDateTime.of(2026, 7, 10, 15, 0));
        projector.handle(Stream.of(stored(live), stored(canceled),
                stored(new HotelBookingCancelled(canceled.hotelBookingId(), ""))));
    }

    private static HotelBooked hotelBooked(String name, LocalDateTime checkIn) {
        return new HotelBooked(
                HotelBookingId.random(),
                name,
                new Address("1 Hauptstraße", "Berlin", "", "10115", "DE", null),
                ZonedTimestamp.fromLocal(checkIn, ZONE),
                ZonedTimestamp.fromLocal(checkIn.plusDays(2), ZONE),
                BookingIntent.FINAL,
                null,
                null);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), NOW, event, UUID.randomUUID());
    }
}

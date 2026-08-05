package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.web.BookHotelRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary's zone contract for hotels: an explicit {@code CommonZone} pick wins, otherwise the
 * address must resolve, otherwise the command is rejected outright — no default zone, because in a
 * travel app a silent guess would be wrong more often than right. Check-in and check-out share the
 * hotel's single zone.
 */
class BookHotelHandlerTest {

    private final BookHotelHandler handler = new BookHotelHandler(new LocationZoneResolver());

    @Test
    void hotelZoneIsDerivedFromTheAddressWhenNoZoneIsPicked() {
        BookHotelCommand command = handler.handle(requestIn("Tokyo", "Japan", null));

        assertThat(command.checkIn().zone())
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(command.checkOut().zone())
                .as("both ends of a stay are at the one hotel, so they share its zone")
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void theFormsWallClockBecomesAnInstantInThatZone() {
        BookHotelCommand command = handler.handle(requestIn("Tokyo", "Japan", null));

        assertThat(command.checkIn().utc())
                .as("15:00 JST is 06:00Z")
                .isEqualTo(Instant.parse("2026-09-15T06:00:00Z"));
    }

    @Test
    void explicitZonePickWinsOverTheAddress() {
        BookHotelCommand command = handler.handle(requestIn("Tokyo", "Japan", "US_CENTRAL"));

        assertThat(command.checkIn().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void unresolvableAddressWithNoPickIsRejected() {
        assertThatThrownBy(() -> handler.handle(requestIn("Springfield", "Freedonia", null)))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void unresolvableAddressIsAcceptedOnceAZoneIsPicked() {
        BookHotelCommand command = handler.handle(requestIn("Springfield", "Freedonia", "US_CENTRAL"));

        assertThat(command.checkIn().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    private static BookHotelRequest requestIn(String city, String country, String zone) {
        BookHotelRequest request = new BookHotelRequest();
        request.setHotelBookingId(UUID.randomUUID().toString());
        request.setHotelName("Some Hotel");
        request.setStreet("1 Example St");
        request.setCity(city);
        request.setRegion("");
        request.setPostalCode("");
        request.setCountry(country);
        request.setLocationForMatching(city);
        request.setMapsUrl("");
        request.setZone(zone);
        request.setCheckIn(LocalDateTime.of(2026, 9, 15, 15, 0));
        request.setCheckOut(LocalDateTime.of(2026, 9, 17, 11, 0));
        request.setBookingIntent(BookingIntent.FINAL);
        return request;
    }
}

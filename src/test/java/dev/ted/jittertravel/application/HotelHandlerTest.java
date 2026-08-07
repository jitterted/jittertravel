package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ChangeHotelCommand;
import dev.ted.jittertravel.web.BookHotelRequest;
import dev.ted.jittertravel.web.ChangeHotelRequest;
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
 * <p>
 * Booking and changing run through the one handler, so the last two cases assert the change path
 * reads the zone and the deadline identically — that equivalence used to rest on two copies of the
 * same code staying in step.
 */
class HotelHandlerTest {

    private final HotelHandler handler = new HotelHandler(new LocationZoneResolver());

    @Test
    void hotelZoneIsDerivedFromTheAddressWhenNoZoneIsPicked() {
        BookHotelCommand command = handler.bookHotel(requestIn("Tokyo", "Japan", null));

        assertThat(command.checkIn().zone())
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(command.checkOut().zone())
                .as("both ends of a stay are at the one hotel, so they share its zone")
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void theFormsWallClockBecomesAnInstantInThatZone() {
        BookHotelCommand command = handler.bookHotel(requestIn("Tokyo", "Japan", null));

        assertThat(command.checkIn().utc())
                .as("15:00 JST is 06:00Z")
                .isEqualTo(Instant.parse("2026-09-15T06:00:00Z"));
    }

    @Test
    void explicitZonePickWinsOverTheAddress() {
        BookHotelCommand command = handler.bookHotel(requestIn("Tokyo", "Japan", "US_CENTRAL"));

        assertThat(command.checkIn().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void unresolvableAddressWithNoPickIsRejected() {
        assertThatThrownBy(() -> handler.bookHotel(requestIn("Springfield", "Freedonia", null)))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void unresolvableAddressIsAcceptedOnceAZoneIsPicked() {
        BookHotelCommand command = handler.bookHotel(requestIn("Springfield", "Freedonia", "US_CENTRAL"));

        assertThat(command.checkIn().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void theCancelByDeadlineIsReadInTheHotelsZoneToo() {
        BookHotelRequest request = requestIn("Tokyo", "Japan", null);
        request.setCancelBy(LocalDateTime.of(2026, 9, 13, 18, 0));

        BookHotelCommand command = handler.bookHotel(request);

        assertThat(command.cancelBy().zone())
                .as("a deadline read in the server's zone would shift by the offset")
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(command.cancelBy().utc())
                .as("18:00 JST is 09:00Z")
                .isEqualTo(Instant.parse("2026-09-13T09:00:00Z"));
    }

    @Test
    void anOmittedCancelByStaysNullRatherThanBecomingAnInstant() {
        BookHotelCommand command = handler.bookHotel(requestIn("Tokyo", "Japan", null));

        assertThat(command.cancelBy())
                .isNull();
    }

    @Test
    void changingAHotelReadsTheZoneAndTheDeadlineExactlyAsBookingDoes() {
        ChangeHotelRequest request = changeRequestIn("Tokyo", "Japan", null);
        request.setCancelBy(LocalDateTime.of(2026, 9, 13, 18, 0));

        ChangeHotelCommand command = handler.changeHotel(request);

        assertThat(command.checkIn().zone())
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(command.cancelBy().utc())
                .as("18:00 JST is 09:00Z on the change path too — both paths read one hotel zone")
                .isEqualTo(Instant.parse("2026-09-13T09:00:00Z"));
    }

    @Test
    void clearingTheDeadlineOnAChangeLeavesItNull() {
        ChangeHotelCommand command = handler.changeHotel(changeRequestIn("Tokyo", "Japan", null));

        assertThat(command.cancelBy())
                .as("HotelChanged is a full snapshot, so a cleared field must clear the deadline")
                .isNull();
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

    private static ChangeHotelRequest changeRequestIn(String city, String country, String zone) {
        ChangeHotelRequest request = new ChangeHotelRequest();
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

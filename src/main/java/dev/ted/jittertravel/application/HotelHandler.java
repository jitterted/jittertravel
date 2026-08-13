package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.ChangeHotelCommand;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.BookHotelRequest;
import dev.ted.jittertravel.web.ChangeHotelRequest;
import dev.ted.jittertravel.web.HotelStayRequest;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Turns a hotel form request into the matching domain command. Booking and changing stay separate
 * commands with separate rules, but they read the same stay — one address, one hotel zone, one
 * optional cancellation deadline — so that reading lives here once, in the private helpers below.
 * <p>
 * Previously {@code BookHotelHandler} and {@code ChangeHotelHandler} held byte-identical copies of
 * it, which is exactly how the cancellation deadline's null-preserving zone conversion came to
 * exist twice.
 */
public class HotelHandler {

    private final VenueZone venueZone;

    public HotelHandler(LocationZoneResolver zoneResolver) {
        this.venueZone = new VenueZone(zoneResolver);
    }

    public BookHotelCommand bookHotel(BookHotelRequest request) {
        ZoneId zone = resolveZone(request);
        return new BookHotelCommand(
                hotelBookingId(request),
                request.getHotelName(),
                address(request),
                ZonedTimestamp.fromLocal(request.getCheckIn(), zone),
                ZonedTimestamp.fromLocal(request.getCheckOut(), zone),
                request.getBookingIntent(),
                request.getMapsUrl(),
                ZonedTimestamp.fromNullableLocal(request.getCancelBy(), zone)
        );
    }

    public ChangeHotelCommand changeHotel(ChangeHotelRequest request) {
        ZoneId zone = resolveZone(request);
        return new ChangeHotelCommand(
                hotelBookingId(request),
                request.getHotelName(),
                address(request),
                ZonedTimestamp.fromLocal(request.getCheckIn(), zone),
                ZonedTimestamp.fromLocal(request.getCheckOut(), zone),
                request.getBookingIntent(),
                request.getMapsUrl(),
                // Absent stays null, which is how clearing the field on the edit form removes the
                // deadline from the full-snapshot HotelChanged event.
                ZonedTimestamp.fromNullableLocal(request.getCancelBy(), zone)
        );
    }

    private HotelBookingId hotelBookingId(HotelStayRequest request) {
        return HotelBookingId.of(UUID.fromString(request.getHotelBookingId()));
    }

    private Address address(HotelStayRequest request) {
        return new Address(request.getStreet(), request.getCity(), request.getRegion(),
                request.getPostalCode(), request.getCountry(),
                request.getLocationForMatching());
    }

    /**
     * An explicit zone pick wins; otherwise the address must resolve or the command is rejected
     * ({@link ZoneResolutionException}) — the form then requires a CommonZone pick. Check-in,
     * check-out and the cancellation deadline all share the hotel's single zone. Same contract as
     * the gathering and conference forms, so it reads through the shared {@link VenueZone}.
     */
    private ZoneId resolveZone(HotelStayRequest request) {
        return venueZone.resolve(request.getZone(), address(request));
    }
}

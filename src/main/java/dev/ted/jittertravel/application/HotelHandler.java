package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.ChangeHotelCommand;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.BookHotelRequest;
import dev.ted.jittertravel.web.ChangeHotelRequest;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Turns a hotel form request into the matching domain command. Booking and changing stay separate
 * commands with separate rules, but they read the same stay — one address, one hotel zone, one
 * optional cancellation deadline.
 * <p>
 * The two requests used to share a {@code HotelStayRequest} interface so the reading below could be
 * written once against it. The interface went with the records: what the two methods share is the
 * {@link Address} they build and the zone they derive from it, so each builds one and hands it to
 * {@link VenueZone} — the same shape {@link BookTrainHandler} has with {@code TrainStations}, and
 * two visible lines rather than an abstraction over two request types that differed in nothing.
 * <p>
 * Previously {@code BookHotelHandler} and {@code ChangeHotelHandler} held byte-identical copies of
 * the whole method, which is exactly how the cancellation deadline's null-preserving zone
 * conversion came to exist twice. That is the duplication worth removing; three lines of
 * constructor call is not.
 */
public class HotelHandler {

    /**
     * An explicit zone pick wins; otherwise the address must resolve or the command is rejected
     * ({@link ZoneResolutionException}) — the form then requires a CommonZone pick. Check-in,
     * check-out and the cancellation deadline all share the hotel's single zone. Same contract as
     * the gathering and conference forms, so both methods below read it through this one
     * collaborator.
     */
    private final VenueZone venueZone;

    public HotelHandler(LocationZoneResolver zoneResolver) {
        this.venueZone = new VenueZone(zoneResolver);
    }

    public BookHotelCommand bookHotel(BookHotelRequest request) {
        Address address = new Address(request.street(), request.city(), request.region(),
                request.postalCode(), request.country(), request.locationForMatching());
        ZoneId zone = venueZone.resolve(request.zone(), address);
        return new BookHotelCommand(
                HotelBookingId.of(UUID.fromString(request.hotelBookingId())),
                request.hotelName(),
                address,
                ZonedTimestamp.fromLocal(request.checkIn(), zone),
                ZonedTimestamp.fromLocal(request.checkOut(), zone),
                request.bookingIntent(),
                request.mapsUrl(),
                ZonedTimestamp.fromNullableLocal(request.cancelBy(), zone)
        );
    }

    /**
     * {@code hotelBookingId} arrives from the path rather than on the request: which booking is
     * being changed is not something the form submits, so there is nothing on the page for a
     * crafted POST to re-target.
     */
    public ChangeHotelCommand changeHotel(String hotelBookingId, ChangeHotelRequest request) {
        Address address = new Address(request.street(), request.city(), request.region(),
                request.postalCode(), request.country(), request.locationForMatching());
        ZoneId zone = venueZone.resolve(request.zone(), address);
        return new ChangeHotelCommand(
                HotelBookingId.of(UUID.fromString(hotelBookingId)),
                request.hotelName(),
                address,
                ZonedTimestamp.fromLocal(request.checkIn(), zone),
                ZonedTimestamp.fromLocal(request.checkOut(), zone),
                request.bookingIntent(),
                request.mapsUrl(),
                // Absent stays null, which is how clearing the field on the edit form removes the
                // deadline from the full-snapshot HotelChanged event.
                ZonedTimestamp.fromNullableLocal(request.cancelBy(), zone)
        );
    }
}

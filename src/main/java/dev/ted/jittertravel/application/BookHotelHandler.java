package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.CommonZone;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.BookHotelRequest;

import java.time.ZoneId;
import java.util.UUID;

public class BookHotelHandler {

    private final LocationZoneResolver zoneResolver;

    public BookHotelHandler(LocationZoneResolver zoneResolver) {
        this.zoneResolver = zoneResolver;
    }

    public BookHotelCommand handle(BookHotelRequest request) {
        Address address = new Address(request.getStreet(), request.getCity(), request.getRegion(),
                request.getPostalCode(), request.getCountry(),
                request.getLocationForMatching());
        // An explicit zone pick wins; otherwise the address must resolve or the command is rejected
        // (ZoneResolutionException) — the form then requires a CommonZone pick. Check-in and check-out
        // share the hotel's single zone.
        ZoneId zone = resolveZone(request.getZone(), address);
        return new BookHotelCommand(
                HotelBookingId.of(UUID.fromString(request.getHotelBookingId())),
                request.getHotelName(),
                address,
                ZonedTimestamp.fromLocal(request.getCheckIn(), zone),
                ZonedTimestamp.fromLocal(request.getCheckOut(), zone),
                request.getBookingIntent(),
                request.getMapsUrl()
        );
    }

    private ZoneId resolveZone(String explicitZone, Address address) {
        CommonZone picked = CommonZone.fromParam(explicitZone);
        if (picked != null) {
            return picked.zoneId();
        }
        return zoneResolver.resolve(address);
    }
}

package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ChangeHotelCommand;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.web.ChangeHotelRequest;

import java.util.UUID;

public class ChangeHotelHandler {

    public ChangeHotelCommand handle(ChangeHotelRequest request) {
        return new ChangeHotelCommand(
                HotelBookingId.of(UUID.fromString(request.getHotelBookingId())),
                request.getHotelName(),
                new Address(request.getStreet(), request.getCity(), request.getRegion(),
                        request.getPostalCode(), request.getCountry(),
                        request.getLocationForMatching()),
                request.getCheckIn(),
                request.getCheckOut(),
                request.getBookingIntent(),
                request.getMapsUrl()
        );
    }
}
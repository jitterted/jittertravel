package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookHotelCommand;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.web.BookHotelRequest;

import java.util.UUID;

public class BookHotelHandler {

    public BookHotelCommand handle(BookHotelRequest request) {
        return new BookHotelCommand(
                HotelBookingId.of(UUID.fromString(request.getHotelBookingId())),
                request.getHotelName(),
                new Address(request.getStreet(), request.getCity(), request.getState(),
                        request.getPostalCode(), request.getCountry()),
                request.getCheckIn(),
                request.getCheckOut(),
                request.getBookingIntent()
        );
    }
}

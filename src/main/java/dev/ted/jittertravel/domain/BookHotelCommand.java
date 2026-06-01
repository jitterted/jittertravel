package dev.ted.jittertravel.domain;

import dev.ted.jittertravel.web.BookHotelRequest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

public class BookHotelCommand {

    public Stream<HotelBooked> execute(BookHotelRequest request, LocalDateTime now) {
        if (request.getCheckIn() == null || !request.getCheckIn().isAfter(now)) {
            throw new CheckInNotInFuture("Check-in date/time must be in the future");
        }
        if (request.getCheckOut() == null ||
                !request.getCheckOut().toLocalDate()
                        .isAfter(request.getCheckIn().toLocalDate())) {
            throw new InvalidHotelDateRange(
                    "Check-out must be at least one calendar day after check-in");
        }

        return Stream.of(new HotelBooked(
                HotelBookingId.of(UUID.fromString(request.getHotelBookingId())),
                request.getHotelName(),
                new Address(request.getStreet(), request.getCity(), request.getState(),
                        request.getPostalCode(), request.getCountry()),
                request.getCheckIn(),
                request.getCheckOut(),
                request.getBookingIntent()
        ));
    }
}

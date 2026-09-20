package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.BookingIntent;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Form-backing record for changing a booked hotel stay.
 * <p>
 * <strong>The id is not here.</strong> Which booking is being changed is path data — nothing on the
 * page submits it, and a hidden field holding it would let a submit re-target another booking. The
 * controller reads it from the path and hands it to the application service alongside this request.
 * Compare {@link BookHotelRequest}, whose id is minted for a new booking and so genuinely is form
 * data.
 */
public record ChangeHotelRequest(
        String hotelName,
        String street,
        String city,
        String region,
        String country,
        String postalCode,
        String locationForMatching,
        String mapsUrl,
        // Optional explicit time-zone pick (a CommonZone enum name). Empty/absent means "derive from
        // the location"; a value wins over derivation. The form requires it only when derivation fails.
        String zone,
        // @DateTimeFormat required to match browser's <input type="datetime-local" /> format
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime checkIn,
        @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime checkOut,
        // Optional free-cancellation deadline, read in the hotel's zone. HotelChanged is a full
        // snapshot, so the form must submit the current value back or the edit clears it.
        @OptionalEntry @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime cancelBy,
        BookingIntent bookingIntent
) {
}

package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.BookingIntent;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Form-backing record for booking a hotel stay.
 * <p>
 * {@code hotelBookingId} stays a component: it is minted for a new booking and carried in a hidden
 * field, so it is form data rather than something the path already says. Compare
 * {@link ChangeHotelRequest}, whose id is path data and is therefore not on the request at all.
 * <p>
 * The two used to share a {@code HotelStayRequest} interface so {@code HotelHandler} could read
 * either. It went with the conversion: the interface abstracted over two classes that differed in
 * nothing but a comment, and both reasons its javadoc gave for existing had gone stale — neither
 * class ever carried a {@code commandId} (the controllers mint one), and the Jackson import path it
 * named was retired with event-oriented backup, which also made the {@code setState} export
 * compatibility setter dead. What the handler actually shared was the {@link
 * dev.ted.jittertravel.domain.Address} it built, so it builds one in each method now — the same
 * shape {@code BookTrainHandler} has with {@code TrainStations}.
 */
public record BookHotelRequest(
        String hotelBookingId,
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
        // Optional free-cancellation deadline, read in the hotel's zone. Absent means none recorded.
        @OptionalEntry @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime cancelBy,
        BookingIntent bookingIntent
) {
}

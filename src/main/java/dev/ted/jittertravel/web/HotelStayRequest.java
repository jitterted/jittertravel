package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.BookingIntent;

import java.time.LocalDateTime;

/**
 * The form fields describing a hotel stay, shared by {@link BookHotelRequest} and
 * {@link ChangeHotelRequest}. Booking and changing are different commands with different rules, but
 * they describe the same thing — one hotel, one address, one zone, one optional cancellation
 * deadline — so {@code HotelHandler} reads both through this interface and turns a request into a
 * command in one place.
 * <p>
 * Deliberately read-only: the setters stay on the concrete classes, because Spring's form binding
 * and Jackson's import deserialization both target those, and the two differ in how they derive
 * {@code commandId}.
 */
public interface HotelStayRequest {
    String getHotelBookingId();

    String getHotelName();

    String getStreet();

    String getCity();

    String getRegion();

    String getCountry();

    String getPostalCode();

    String getLocationForMatching();

    String getMapsUrl();

    /** A {@code CommonZone} name that wins over deriving the zone from the address; may be empty. */
    String getZone();

    LocalDateTime getCheckIn();

    LocalDateTime getCheckOut();

    /** Optional free-cancellation deadline; null means none was recorded. */
    LocalDateTime getCancelBy();

    BookingIntent getBookingIntent();
}

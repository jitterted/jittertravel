package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.HotelBookingId;

import java.time.LocalDateTime;

/**
 * Full details for a single hotel booking, used to hydrate the edit form.
 * <p>
 * Raw values (not pre-formatted) so the form-binding can populate input controls directly.
 * The list view ({@link BookedHotelView}) is what does the pre-formatting; this view is for
 * editing. Mirrors {@link TrainDetailsView}.
 * <p>
 * {@code cancelBy} is null when the booking has no recorded deadline. It is carried here purely so
 * the edit form can submit it back unchanged — {@code HotelChanged} is a full snapshot, so a form
 * that dropped the field would silently clear the deadline on every edit.
 */
public record HotelDetailsView(
        HotelBookingId hotelBookingId,
        String hotelName,
        Address address,
        LocalDateTime checkIn,
        LocalDateTime checkOut,
        BookingIntent bookingIntent,
        String mapsUrl,
        LocalDateTime cancelBy
) {
}

package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle guard: a cancelled hotel booking must disappear from <em>every</em> read model.
 * <p>
 * Cancellation is a hard removal — no tombstone, no "cancelled" row even under the ALL filter — so
 * a projector that handles {@code HotelBooked} but forgets {@code HotelBookingCancelled} keeps
 * showing a stay that no longer exists. That silent gap is exactly what this book-then-cancel
 * scenario catches, one case per projector.
 */
class HotelCancellationPropagationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Address BERLIN =
            new Address("123 Unter den Linden", "Berlin", "", "10117", "Germany", "Berlin");
    private static final ZonedTimestamp CHECK_IN = zt(LocalDateTime.of(2026, 7, 1, 15, 0));
    private static final ZonedTimestamp CHECK_OUT = zt(LocalDateTime.of(2026, 7, 5, 11, 0));
    private static final Instant BEFORE_THE_STAY = Instant.parse("2026-06-01T00:00:00Z");

    private final HotelBookingId bookingId = HotelBookingId.random();
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void bookedHotelsListDropsTheCancelledStay() {
        BookedHotelsProjector projector = new BookedHotelsProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.views(TimeView.ALL, BEFORE_THE_STAY))
                .as("ALL must not resurrect a cancelled booking — there is no tombstone row")
                .isEmpty();
    }

    @Test
    void hotelDetailsViewDropsTheCancelledStay() {
        HotelDetailsViewProjector projector = new HotelDetailsViewProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.findById(bookingId))
                .as("the edit page must 404 rather than edit a cancelled booking")
                .isEmpty();
    }

    @Test
    void tentativeHotelBookingDropsTheCancelledStay() {
        TentativeHotelBookingProjector projector = new TentativeHotelBookingProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.findById(bookingId))
                .isNull();
    }

    @Test
    void tentativeHotelBookingsListDropsTheCancelledStay() {
        TentativeHotelBookingsProjector projector = new TentativeHotelBookingsProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.views())
                .isEmpty();
    }

    @Test
    void hotelCalendarDropsTheCancelledStay() {
        HotelCalendarProjector projector = new HotelCalendarProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entries())
                .isEmpty();
    }

    @Test
    void itineraryDropsBothDaysOfTheCancelledStay() {
        ItineraryProjector projector = new ItineraryProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.entriesForDate(LocalDate.of(2026, 7, 1)))
                .as("check-in day")
                .isEmpty();
        assertThat(projector.entriesForDate(LocalDate.of(2026, 7, 5)))
                .as("check-out day")
                .isEmpty();
    }

    @Test
    void scheduleProblemsReportTheNightsTheCancelledStayUsedToCover() {
        // A conference in Berlin needs those nights covered. With the hotel booked there is no
        // problem; cancelling it must bring the MissingHotel back — proving the stay left the
        // projector's state rather than merely being hidden.
        ScheduleGapProjector withBooking = new ScheduleGapProjector(new StaticAirportCityResolver());
        withBooking.handle(Stream.concat(Stream.of(stored(berlinConference())),
                Stream.of(stored(hotelBooked()))));
        assertThat(withBooking.problems())
                .as("the booked hotel covers the conference nights")
                .isEmpty();

        ScheduleGapProjector afterCancelling = new ScheduleGapProjector(new StaticAirportCityResolver());
        afterCancelling.handle(Stream.of(stored(berlinConference()), stored(hotelBooked()),
                stored(new HotelBookingCancelled(bookingId, "Trip called off"))));

        assertThat(afterCancelling.problems())
                .as("with the booking gone, the nights are uncovered again")
                .hasSize(1);
    }

    @Test
    void locationAuditStillReportsTheCancelledStaysLocation() {
        // Deliberately NOT a removal. HotelBooked stays in the log forever and the read-time
        // upcaster resolves its zone on every replay, so the audit must keep reporting Berlin —
        // dropping it would hide the unresolvable location that breaks startup.
        LocationAuditProjector projector = new LocationAuditProjector();

        projector.handle(bookThenCancel());

        assertThat(projector.cities())
                .extracting(location -> location.location().city())
                .contains("Berlin");
    }

    private Stream<StoredEvent> bookThenCancel() {
        return Stream.of(
                stored(hotelBooked()),
                stored(new HotelBookingCancelled(bookingId, "Trip called off")));
    }

    private HotelBooked hotelBooked() {
        return new HotelBooked(bookingId, "Grand Hotel", BERLIN, CHECK_IN, CHECK_OUT,
                BookingIntent.TENTATIVE, "https://maps.example/grand", null);
    }

    private static ConferenceTentativelyPlanned berlinConference() {
        return new ConferenceTentativelyPlanned(
                ConferenceId.random(), "BerlinConf",
                zt(LocalDateTime.of(2026, 7, 1, 9, 0)),
                zt(LocalDateTime.of(2026, 7, 5, 17, 0)),
                "Congress Center", BERLIN);
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ZONE);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                Instant.now(), event, UUID.randomUUID());
    }
}

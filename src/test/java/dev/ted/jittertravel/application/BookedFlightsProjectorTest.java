package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightCancellationCause;
import dev.ted.jittertravel.domain.FlightCancelled;
import dev.ted.jittertravel.domain.FlightChanged;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.FlightItineraryChanged;
import dev.ted.jittertravel.domain.FlightItineraryId;
import dev.ted.jittertravel.domain.FlightItineraryRebooked;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BookedFlightsProjectorTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant NOW_INSTANT = Instant.parse("2026-06-15T12:00:00Z");

    @Test
    void flightBookedProducesViewWithRouteAndDeparture() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightId flightId = FlightId.random();
        ZonedTimestamp dep = zt(LocalDateTime.of(2026, 6, 6, 13, 55));
        ZonedTimestamp arr = zt(LocalDateTime.of(2026, 6, 7, 9, 45));
        FlightBooked event = new FlightBooked(
                flightId,
                "United Airlines",
                "UA59",
                AirportCode.of("SFO"),
                dep,
                AirportCode.of("FRA"),
                arr
        );

        projector.handle(Stream.of(stored(event, instant(2026, 5, 20, 12, 22))));

        List<BookedFlightView> views = projector.views(TimeView.ALL, NOW_INSTANT);
        assertThat(views).hasSize(1);
        BookedFlightView view = views.getFirst();
        assertThat(view.flightId()).isEqualTo(flightId);
        assertThat(view.airline()).isEqualTo("United Airlines");
        assertThat(view.flightNumber()).isEqualTo("UA59");
        assertThat(view.route()).isEqualTo("SFO→FRA");
        assertThat(view.departureDateTime()).isEqualTo(dep);
        assertThat(view.arrivalDateTime()).isEqualTo(arr);
        assertThat(view.hasChanges()).isFalse();
        assertThat(view.history())
                .extracting(ChangeEntry::displayText)
                .containsExactly("Booked on 2026-05-20 12:22PM");
    }

    @Test
    void viewsAreSortedByDepartureDateTimeAscending() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked later = sampleFlight("UA2", LocalDateTime.of(2026, 7, 1, 9, 0));
        FlightBooked earlier = sampleFlight("UA1", LocalDateTime.of(2026, 6, 1, 9, 0));

        projector.handle(Stream.of(stored(later, Instant.now()), stored(earlier, Instant.now())));

        assertThat(projector.views(TimeView.ALL, NOW_INSTANT))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA1", "UA2");
    }

    @Test
    void replayingTheSameEventTwiceProducesTwoHistoryEntriesForOneFlight() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked event = sampleFlight("UA1", LocalDateTime.of(2026, 6, 6, 13, 55));

        projector.handle(Stream.of(stored(event, Instant.now())));
        projector.handle(Stream.of(stored(event, Instant.now())));

        assertThat(projector.views(TimeView.ALL, NOW_INSTANT)).hasSize(1);
        assertThat(projector.views(TimeView.ALL, NOW_INSTANT).getFirst().history()).hasSize(2);
    }

    @Test
    void futureFilterExcludesFlightsDepartedBeforeNow() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked past = sampleFlight("UA1", LocalDateTime.of(2026, 6, 10, 12, 0));
        FlightBooked upcoming = sampleFlight("UA2", LocalDateTime.of(2026, 6, 20, 12, 0));

        projector.handle(Stream.of(stored(past, Instant.now()), stored(upcoming, Instant.now())));

        assertThat(projector.views(TimeView.FUTURE, NOW_INSTANT))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA2");
        assertThat(projector.views(TimeView.ALL, NOW_INSTANT))
                .extracting(BookedFlightView::flightNumber)
                .containsExactly("UA1", "UA2");
    }

    /** A leg a change books arrives in the change's own append, so it shares the change's command id. */
    @Test
    void aLegARebookingAddedIsMarkedAddedOnTheRebookingsDate() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked original = sampleFlight("UA1", LocalDateTime.of(2026, 11, 26, 10, 0));
        FlightBooked added = sampleFlight("UA59", LocalDateTime.of(2026, 11, 26, 13, 30));
        FlightBooked unrelated = sampleFlight("UA7", LocalDateTime.of(2026, 11, 27, 9, 0));
        FlightItineraryId trip = FlightItineraryId.of(UUID.randomUUID());
        UUID rebooking = UUID.randomUUID();
        Instant rebookedOn = Instant.parse("2026-11-14T15:00:00Z");

        projector.handle(Stream.of(
                stored(original, NOW_INSTANT),
                stored(unrelated, NOW_INSTANT),
                stored(added, rebooking),
                stored(new FlightItineraryRebooked(trip, List.of(original.flightId(), added.flightId()),
                        "Rebooked", rebookedOn), rebooking)));

        assertThat(projector.find(added.flightId()))
                .as("Leg the rebooking added")
                .isPresent()
                .map(BookedFlightView::addedOn)
                .as("added on the rebooking's own date")
                .hasValue(rebookedOn);
        assertThat(projector.find(original.flightId()))
                .as("Leg booked with the trip")
                .isPresent()
                .map(BookedFlightView::addedByAChange)
                .as("not added by a change")
                .hasValue(false);
        assertThat(projector.find(unrelated.flightId()))
                .as("Flight outside the change")
                .isPresent()
                .map(BookedFlightView::addedByAChange)
                .hasValue(false);
    }

    @Test
    void aLegAScheduleChangeAddedIsMarkedAddedAndStaysSoWhenLaterEdited() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked added = sampleFlight("UA59", LocalDateTime.of(2026, 11, 26, 13, 30));
        UUID change = UUID.randomUUID();
        Instant changedOn = Instant.parse("2026-11-14T15:00:00Z");

        projector.handle(Stream.of(
                stored(added, change),
                stored(new FlightItineraryChanged(FlightItineraryId.of(UUID.randomUUID()), List.of(added.flightId()),
                        "Airline schedule change", changedOn), change),
                stored(new FlightChanged(added.flightId(), "United", "UA59", AirportCode.of("SFO"),
                        zt(LocalDateTime.of(2026, 11, 26, 14, 0)), AirportCode.of("LAX"),
                        zt(LocalDateTime.of(2026, 11, 26, 16, 0)), "Edited"), UUID.randomUUID())));

        assertThat(projector.find(added.flightId()))
                .as("Leg the schedule change added, edited by hand since")
                .isPresent()
                .map(BookedFlightView::addedOn)
                .hasValue(changedOn);
    }

    @Test
    void aCancellationRecordsWhetherItWasADrop() {
        BookedFlightsProjector projector = new BookedFlightsProjector();
        FlightBooked dropped = sampleFlight("UA8786", LocalDateTime.of(2026, 11, 26, 10, 40));
        FlightBooked cancelled = sampleFlight("UA1", LocalDateTime.of(2026, 11, 27, 10, 40));

        projector.handle(Stream.of(
                stored(dropped, NOW_INSTANT),
                stored(cancelled, NOW_INSTANT),
                stored(new FlightCancelled(dropped.flightId(), "Rebooked", NOW_INSTANT,
                        FlightCancellationCause.REBOOKED), NOW_INSTANT),
                stored(new FlightCancelled(cancelled.flightId(), "", NOW_INSTANT), NOW_INSTANT)));

        assertThat(projector.find(dropped.flightId()))
                .as("Leg a rebooking dropped")
                .isPresent()
                .map(BookedFlightView::dropped)
                .hasValue(true);
        assertThat(projector.find(cancelled.flightId()))
                .as("Flight cancelled by hand")
                .isPresent()
                .map(BookedFlightView::dropped)
                .hasValue(false);
    }

    private static StoredEvent stored(Event event, UUID commandId) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), NOW_INSTANT, event, commandId);
    }

    private static FlightBooked sampleFlight(String number, LocalDateTime departure) {
        return new FlightBooked(
                FlightId.random(),
                "United",
                number,
                AirportCode.of("SFO"),
                zt(departure),
                AirportCode.of("LAX"),
                zt(departure.plusHours(2))
        );
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, UTC);
    }

    private static StoredEvent stored(Event event, Instant timestamp) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), timestamp, event, UUID.randomUUID());
    }

    private static Instant instant(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute)
                .atOffset(ZoneOffset.UTC)
                .toInstant();
    }
}

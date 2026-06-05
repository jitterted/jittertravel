package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.*;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class FlightCalendarProjectorTest {

    @Test
    void multiDayFlightProducesTwoEntriesOneForDepartureDayAndOneForArrivalDay() {
        FlightCalendarProjector projector = new FlightCalendarProjector();
        FlightBooked event = new FlightBooked(
                FlightId.random(),
                "United Airlines",
                "UA59",
                AirportCode.of("SFO"),
                LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"),
                LocalDateTime.of(2026, 6, 7, 9, 45)
        );

        projector.handle(Stream.of(stored(event)));

        List<CalendarEntry> entries = projector.entries();
        assertThat(entries).hasSize(2);

        CalendarEntry departureEntry = entries.get(0);
        assertThat(departureEntry.kind()).isEqualTo(EntryKind.FLIGHT);
        assertThat(departureEntry.mainTitle()).isEqualTo("✈️ SFO\u2192FRA");
        assertThat(departureEntry.subTitle()).isEqualTo(List.of("Departs 1:55 PM"));
        assertThat(departureEntry.start()).isEqualTo(LocalDateTime.of(2026, 6, 6, 13, 55));
        assertThat(departureEntry.end()).isEqualTo(LocalDateTime.of(2026, 6, 6, 13, 55));
        assertThat(departureEntry.continuationTitle()).isNull();
        assertThat(departureEntry.continuationSubTitle()).isNull();

        CalendarEntry arrivalEntry = entries.get(1);
        assertThat(arrivalEntry.kind()).isEqualTo(EntryKind.FLIGHT);
        assertThat(arrivalEntry.mainTitle()).isEqualTo("✈️ SFO\u2192FRA");
        assertThat(arrivalEntry.subTitle()).isEqualTo(List.of("Arrives 9:45 AM"));
        assertThat(arrivalEntry.start()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 45));
        assertThat(arrivalEntry.end()).isEqualTo(LocalDateTime.of(2026, 6, 7, 9, 45));
        assertThat(arrivalEntry.continuationTitle()).isNull();
        assertThat(arrivalEntry.continuationSubTitle()).isNull();
    }

    @Test
    void sameDayFlightProducesOneEntryWithTimeRange() {
        FlightCalendarProjector projector = new FlightCalendarProjector();
        FlightBooked event = new FlightBooked(
                FlightId.random(),
                "United",
                "UA100",
                AirportCode.of("SFO"),
                LocalDateTime.of(2026, 6, 6, 9, 0),
                AirportCode.of("LAX"),
                LocalDateTime.of(2026, 6, 6, 10, 30)
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("✈️ SFO\u2192LAX");
        assertThat(entry.subTitle()).isEqualTo(List.of("9:00 AM → 10:30 AM"));
        assertThat(entry.start()).isEqualTo(LocalDateTime.of(2026, 6, 6, 9, 0));
        assertThat(entry.end()).isEqualTo(LocalDateTime.of(2026, 6, 6, 10, 30));
    }

    @Test
    void replayingTheSameEventIsIdempotent() {
        FlightCalendarProjector projector = new FlightCalendarProjector();
        FlightBooked event = sampleFlight("UA1", LocalDateTime.of(2026, 6, 6, 13, 55));

        projector.handle(Stream.of(stored(event)));
        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);  // same-day flight => 1 entry
    }

    @Test
    void entriesAreSortedByStart() {
        FlightCalendarProjector projector = new FlightCalendarProjector();
        FlightBooked later = sampleFlight("UA2", LocalDateTime.of(2026, 7, 1, 9, 0));
        FlightBooked earlier = sampleFlight("UA1", LocalDateTime.of(2026, 6, 1, 9, 0));

        projector.handle(Stream.of(stored(later), stored(earlier)));

        assertThat(projector.entries())
                .extracting(CalendarEntry::start)
                .containsExactly(LocalDateTime.of(2026, 6, 1, 9, 0),
                                 LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    private static FlightBooked sampleFlight(String number, LocalDateTime departure) {
        return new FlightBooked(
                FlightId.random(),
                "United",
                number,
                AirportCode.of("SFO"),
                departure,
                AirportCode.of("LAX"),
                departure.plusHours(2)  // same-day arrival
        );
    }

    @Test
    void flightChangedOverwritesPreviousCalendarEntries() {
        FlightCalendarProjector projector = new FlightCalendarProjector();
        FlightId flightId = FlightId.random();
        FlightBooked booked = new FlightBooked(
                flightId, "United", "UA59",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 6, 6, 13, 55),
                AirportCode.of("FRA"), LocalDateTime.of(2026, 6, 7, 9, 45)
        );
        FlightChanged changed = new FlightChanged(
                flightId, "Lufthansa", "LH441",
                AirportCode.of("SFO"), LocalDateTime.of(2026, 7, 10, 16, 0),
                AirportCode.of("MUC"), LocalDateTime.of(2026, 7, 11, 11, 30),
                null
        );

        projector.handle(Stream.of(stored(booked), stored(changed)));

        // After the change, the calendar should reflect the new route, dates,
        // and times — not the original booked details.
        assertThat(projector.entries())
                .extracting(CalendarEntry::mainTitle)
                .containsOnly("✈️ SFO\u2192MUC");
        assertThat(projector.entries())
                .extracting(CalendarEntry::start)
                .containsExactly(
                        LocalDateTime.of(2026, 7, 10, 16, 0),
                        LocalDateTime.of(2026, 7, 11, 11, 30)
                );
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}

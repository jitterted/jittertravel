package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainChanged;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TrainCalendarProjectorTest {

    private static final TrainStationAddress LONDON =
            new TrainStationAddress("London Euston", "London", "UK", "");
    private static final TrainStationAddress MANCHESTER =
            new TrainStationAddress("Manchester Piccadilly", "Manchester", "UK", "");
    private static final TrainStationAddress PARIS =
            new TrainStationAddress("Paris Gare du Nord", "Paris", "FR", "");

    @Test
    void sameDayTripProducesOneEntryWithBothTimes() {
        TrainCalendarProjector projector = new TrainCalendarProjector();
        TrainBooked event = new TrainBooked(
                TrainTripId.random(), LONDON,
                LocalDateTime.of(2026, 6, 9, 9, 0),
                MANCHESTER,
                LocalDateTime.of(2026, 6, 9, 11, 15),
                ""
        );

        projector.handle(Stream.of(stored(event)));

        List<CalendarEntry> entries = projector.entries();
        assertThat(entries)
                .hasSize(1);
        CalendarEntry entry = entries.getFirst();
        assertThat(entry.kind())
                .isEqualTo(EntryKind.TRAIN);
        assertThat(entry.mainTitle())
                .isEqualTo("🚄 London → Manchester");
        assertThat(entry.subTitle())
                .isEqualTo(List.of("9:00 AM → 11:15 AM"));
        assertThat(entry.start())
                .isEqualTo(LocalDateTime.of(2026, 6, 9, 9, 0));
        assertThat(entry.end())
                .isEqualTo(LocalDateTime.of(2026, 6, 9, 11, 15));
    }

    @Test
    void serviceIdAppearsAsFirstSubtitleLineWhenNonEmpty() {
        TrainCalendarProjector projector = new TrainCalendarProjector();
        TrainBooked event = new TrainBooked(
                TrainTripId.random(), LONDON,
                LocalDateTime.of(2026, 6, 9, 9, 0),
                MANCHESTER,
                LocalDateTime.of(2026, 6, 9, 11, 15),
                "LNER - Azuma 1A34"
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries().getFirst().subTitle())
                .isEqualTo(List.of("LNER - Azuma 1A34", "9:00 AM → 11:15 AM"));
    }

    @Test
    void overnightTripProducesTwoEntriesOneDepartureDayOneArrivalDay() {
        TrainCalendarProjector projector = new TrainCalendarProjector();
        TrainBooked event = new TrainBooked(
                TrainTripId.random(), LONDON,
                LocalDateTime.of(2026, 6, 9, 22, 0),
                PARIS,
                LocalDateTime.of(2026, 6, 10, 6, 30),
                ""
        );

        projector.handle(Stream.of(stored(event)));

        List<CalendarEntry> entries = projector.entries();
        assertThat(entries)
                .hasSize(2);

        CalendarEntry departureEntry = entries.get(0);
        assertThat(departureEntry.mainTitle())
                .isEqualTo("🚄 London → Paris");
        assertThat(departureEntry.subTitle())
                .isEqualTo(List.of("Departs 10:00 PM"));
        assertThat(departureEntry.start())
                .isEqualTo(LocalDateTime.of(2026, 6, 9, 22, 0));

        CalendarEntry arrivalEntry = entries.get(1);
        assertThat(arrivalEntry.mainTitle())
                .isEqualTo("🚄 London → Paris");
        assertThat(arrivalEntry.subTitle())
                .isEqualTo(List.of("Arrives 6:30 AM"));
        assertThat(arrivalEntry.start())
                .isEqualTo(LocalDateTime.of(2026, 6, 10, 6, 30));
    }

    @Test
    void multipleTripsSameDayAreAllIncludedAsSeperateEntries() {
        TrainCalendarProjector projector = new TrainCalendarProjector();
        TrainBooked morning = new TrainBooked(
                TrainTripId.random(), LONDON,
                LocalDateTime.of(2026, 6, 10, 7, 0),
                MANCHESTER,
                LocalDateTime.of(2026, 6, 10, 9, 15),
                ""
        );
        TrainBooked afternoon = new TrainBooked(
                TrainTripId.random(), MANCHESTER,
                LocalDateTime.of(2026, 6, 10, 15, 0),
                LONDON,
                LocalDateTime.of(2026, 6, 10, 17, 15),
                ""
        );

        projector.handle(Stream.of(stored(morning), stored(afternoon)));

        assertThat(projector.entries())
                .hasSize(2)
                .extracting(CalendarEntry::mainTitle)
                .containsExactly("🚄 London → Manchester", "🚄 Manchester → London");
    }

    @Test
    void entriesAreSortedByStart() {
        TrainCalendarProjector projector = new TrainCalendarProjector();
        TrainBooked later = new TrainBooked(
                TrainTripId.random(), LONDON,
                LocalDateTime.of(2026, 7, 1, 9, 0),
                MANCHESTER,
                LocalDateTime.of(2026, 7, 1, 11, 0),
                ""
        );
        TrainBooked earlier = new TrainBooked(
                TrainTripId.random(), LONDON,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                MANCHESTER,
                LocalDateTime.of(2026, 6, 1, 11, 0),
                ""
        );

        projector.handle(Stream.of(stored(later), stored(earlier)));

        assertThat(projector.entries())
                .extracting(CalendarEntry::start)
                .containsExactly(
                        LocalDateTime.of(2026, 6, 1, 9, 0),
                        LocalDateTime.of(2026, 7, 1, 9, 0));
    }

    @Test
    void trainChangedOverwritesCalendarEntryForSameTrip() {
        TrainCalendarProjector projector = new TrainCalendarProjector();
        TrainTripId tripId = TrainTripId.random();
        TrainBooked booked = new TrainBooked(
                tripId, LONDON, LocalDateTime.of(2026, 6, 9, 9, 0),
                MANCHESTER, LocalDateTime.of(2026, 6, 9, 11, 15), "");
        TrainChanged changed = new TrainChanged(
                tripId, LONDON, LocalDateTime.of(2026, 6, 12, 14, 0),
                PARIS, LocalDateTime.of(2026, 6, 12, 18, 30), "");

        projector.handle(Stream.of(stored(booked), stored(changed)));

        assertThat(projector.entries())
                .hasSize(1)
                .extracting(CalendarEntry::mainTitle, CalendarEntry::start)
                .containsExactly(tuple("🚄 London → Paris", LocalDateTime.of(2026, 6, 12, 14, 0)));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}

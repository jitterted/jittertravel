package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateEventCalendarProjectorTest {

    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);
    private static final LocalTime START = LocalTime.of(19, 0);
    private static final LocalTime END = LocalTime.of(22, 0);

    @Test
    void buildsOwnerCalendarEntryFromPrivateEventPlanned() {
        PrivateEventCalendarProjector projector = new PrivateEventCalendarProjector();
        PrivateEventPlanned event = new PrivateEventPlanned(
                PrivateEventId.random(),
                "Dinner with the Smiths",
                "Alo",
                new Address("163 Spadina Ave", "Toronto", "ON", "M5V 2L6", "Canada", null),
                torontoTime(DATE, START), torontoTime(DATE, END)
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.kind()).isEqualTo(EntryKind.PRIVATE_EVENT);
        // The owner sees full detail — title, venue, city/country, and the time range.
        assertThat(entry.mainTitle()).isEqualTo("Dinner with the Smiths");
        assertThat(entry.subTitle()).isEqualTo(List.of(
                new SubtitleLine.Text("Alo"),
                new SubtitleLine.Text("Toronto, Canada"),
                new SubtitleLine.Range(torontoTime(DATE, START), torontoTime(DATE, END))));
        assertThat(entry.start()).isEqualTo(LocalDateTime.of(2026, 7, 10, 19, 0));
        assertThat(entry.end()).isEqualTo(LocalDateTime.of(2026, 7, 10, 22, 0));
        assertThat(entry.continuationTitle()).isNull();
        assertThat(entry.continuationSubTitle()).isNull();
        // A private event has no maps URL and no owner edit link on the calendar.
        assertThat(entry.mapsUrl()).isNull();
        assertThat(entry.editPath()).isNull();
    }

    @Test
    void blankVenueNameShowsOnlyCityAndCountry() {
        PrivateEventCalendarProjector projector = new PrivateEventCalendarProjector();
        PrivateEventPlanned event = new PrivateEventPlanned(
                PrivateEventId.random(), "Evening out", "",
                new Address("", "Toronto", "", "", "Canada", null),
                torontoTime(DATE, START), torontoTime(DATE, END)
        );

        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries().getFirst().subTitle())
                .isEqualTo(List.of(
                        new SubtitleLine.Text("Toronto, Canada"),
                        new SubtitleLine.Range(torontoTime(DATE, START), torontoTime(DATE, END))));
    }

    @Test
    void replayingTheSameEventIsIdempotent() {
        PrivateEventCalendarProjector projector = new PrivateEventCalendarProjector();
        PrivateEventPlanned event = privateEvent(PrivateEventId.random(), "Dinner", DATE);

        projector.handle(Stream.of(stored(event)));
        projector.handle(Stream.of(stored(event)));

        assertThat(projector.entries()).hasSize(1);
    }

    @Test
    void entriesAreSortedByStart() {
        PrivateEventCalendarProjector projector = new PrivateEventCalendarProjector();
        PrivateEventPlanned later = privateEvent(PrivateEventId.random(), "Later", DATE.plusWeeks(1));
        PrivateEventPlanned earlier = privateEvent(PrivateEventId.random(), "Earlier", DATE);

        projector.handle(Stream.of(stored(later), stored(earlier)));

        assertThat(projector.entries())
                .extracting(CalendarEntry::mainTitle)
                .containsExactly("Earlier", "Later");
    }

    private static PrivateEventPlanned privateEvent(PrivateEventId id, String title, LocalDate date) {
        return new PrivateEventPlanned(
                id, title, "Some Venue",
                new Address("1 Street", "Toronto", "ON", "M5V 2L6", "Canada", null),
                torontoTime(date, START), torontoTime(date, END)
        );
    }

    private static ZonedTimestamp torontoTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), TORONTO);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(), Instant.now(), event, UUID.randomUUID());
    }
}

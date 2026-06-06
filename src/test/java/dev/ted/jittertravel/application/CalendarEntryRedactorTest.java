package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarEntryRedactorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 14, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 3, 11, 0);

    private final CalendarEntryRedactor redactor = new CalendarEntryRedactor();

    @Test
    void lodgingHidesHotelNameAndMapsUrl() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING, START, END,
                "Marriott Grand", List.of("Berlin, Germany"),
                "Marriott Grand cont'd", List.of("Berlin, Germany"),
                "https://maps.google.com/marriott"
        );

        CalendarEntry redacted = redactor.redact(hotel);

        assertThat(redacted.mainTitle()).isEqualTo("Hotel");
        assertThat(redacted.continuationTitle()).isEqualTo("Hotel cont'd");
        assertThat(redacted.mapsUrl()).isNull();
    }

    @Test
    void lodgingPreservesLocationSubTitle() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING, START, END,
                "Marriott Grand", List.of("Berlin, Germany"),
                "Marriott Grand cont'd", List.of("Berlin, Germany"),
                "https://maps.google.com/marriott"
        );

        CalendarEntry redacted = redactor.redact(hotel);

        assertThat(redacted.subTitle()).isEqualTo(List.of("Berlin, Germany"));
        assertThat(redacted.continuationSubTitle()).isEqualTo(List.of("Berlin, Germany"));
    }

    @Test
    void flightHidesTimesButKeepsRoute() {
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT, START, END,
                "✈️ SFO→JFK", List.of("9:00 AM → 5:00 PM"),
                null, null, null
        );

        CalendarEntry redacted = redactor.redact(flight);

        assertThat(redacted.mainTitle()).isEqualTo("✈️ SFO→JFK");
        assertThat(redacted.subTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
    }

    @Test
    void trainHidesTimesAndServiceIdButKeepsRoute() {
        CalendarEntry train = new CalendarEntry(
                EntryKind.TRAIN, START, START,
                "🚄 London → Paris", List.of("TGV123", "9:00 AM → 2:30 PM"),
                null, null, null
        );

        CalendarEntry redacted = redactor.redact(train);

        assertThat(redacted.mainTitle()).isEqualTo("🚄 London → Paris");
        assertThat(redacted.subTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
    }

    @Test
    void conferenceIsNotRedacted() {
        CalendarEntry conference = new CalendarEntry(
                EntryKind.CONFERENCE, START, END,
                "DDD Europe 2026", List.of("Frankfurt, Germany"),
                "DDD Europe 2026 cont'd", List.of("Frankfurt, Germany"),
                null
        );

        assertThat(redactor.redact(conference)).isEqualTo(conference);
    }

    @Test
    void gatheringIsNotRedacted() {
        CalendarEntry gathering = new CalendarEntry(
                EntryKind.GATHERING, START, END,
                "London Java Community", List.of("Skills Matter", "London, GB"),
                null, null, "https://meetup.com/events/123"
        );

        assertThat(redactor.redact(gathering)).isEqualTo(gathering);
    }
}

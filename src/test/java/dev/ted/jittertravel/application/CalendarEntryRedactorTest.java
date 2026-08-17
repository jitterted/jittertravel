package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarEntryRedactorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 14, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 3, 11, 0);
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private final CalendarEntryRedactor redactor = new CalendarEntryRedactor();

    @Test
    void lodgingHidesHotelNameMapsUrlAndEditPath() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING, START, END,
                "Marriott Grand", lines("Berlin, Germany"),
                "Marriott Grand cont'd", lines("Berlin, Germany"),
                "https://maps.google.com/marriott",
                "/booked-hotels/abc"
        );

        CalendarEntry redacted = redactor.redact(hotel);

        assertThat(redacted.mainTitle()).isEqualTo("Hotel");
        assertThat(redacted.continuationTitle()).isEqualTo("Hotel cont'd");
        assertThat(redacted.mapsUrl()).isNull();
        assertThat(redacted.editPath()).isNull();
    }

    @Test
    void lodgingPreservesLocationSubTitle() {
        CalendarEntry hotel = new CalendarEntry(
                EntryKind.LODGING, START, END,
                "Marriott Grand", lines("Berlin, Germany"),
                "Marriott Grand cont'd", lines("Berlin, Germany"),
                "https://maps.google.com/marriott"
        );

        CalendarEntry redacted = redactor.redact(hotel);

        assertThat(redacted.subTitle()).isEqualTo(lines("Berlin, Germany"));
        assertThat(redacted.continuationSubTitle()).isEqualTo(lines("Berlin, Germany"));
    }

    @Test
    void flightHidesTimesButKeepsRoute() {
        CalendarEntry flight = new CalendarEntry(
                EntryKind.FLIGHT, START, END,
                "✈️ SFO→JFK", lines("9:00 AM → 5:00 PM"),
                null, null, "https://maps.google.com/sfo-terminal-2",
                "/booked-flights/abc"
        );

        CalendarEntry redacted = redactor.redact(flight);

        assertThat(redacted.mainTitle()).isEqualTo("✈️ SFO→JFK");
        assertThat(redacted.subTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
        assertThat(redacted.mapsUrl()).isNull();
        assertThat(redacted.editPath()).isNull();
    }

    @Test
    void trainHidesTimesAndServiceIdButKeepsRoute() {
        CalendarEntry train = new CalendarEntry(
                EntryKind.TRAIN, START, START,
                "🚄 London → Paris", lines("TGV123", "9:00 AM → 2:30 PM"),
                null, null, null,
                "/booked-trains/abc"
        );

        CalendarEntry redacted = redactor.redact(train);

        assertThat(redacted.mainTitle()).isEqualTo("🚄 London → Paris");
        assertThat(redacted.subTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
        assertThat(redacted.mapsUrl()).isNull();
        assertThat(redacted.editPath()).isNull();
    }

    @Test
    void conferenceKeepsPublicDetailsButDropsEditPath() {
        CalendarEntry conference = new CalendarEntry(
                EntryKind.CONFERENCE, START, END,
                "DDD Europe 2026", lines("Frankfurt, Germany"),
                "DDD Europe 2026 cont'd", lines("Frankfurt, Germany"),
                "https://dddeurope.com", "/plan-conference/abc"
        );

        CalendarEntry redacted = redactor.redact(conference);

        assertThat(redacted.mainTitle()).isEqualTo("DDD Europe 2026");
        assertThat(redacted.subTitle()).isEqualTo(lines("Frankfurt, Germany"));
        assertThat(redacted.continuationTitle()).isEqualTo("DDD Europe 2026 cont'd");
        assertThat(redacted.continuationSubTitle()).isEqualTo(lines("Frankfurt, Germany"));
        assertThat(redacted.mapsUrl()).isEqualTo("https://dddeurope.com");
        assertThat(redacted.editPath()).isNull();
    }

    /**
     * Gatherings are public events, like conferences: name, venue, and times all stay visible.
     * A future "private social event" kind will need its own, redacting branch.
     */
    @Test
    void gatheringKeepsPublicDetailsButDropsEditPath() {
        CalendarEntry gathering = new CalendarEntry(
                EntryKind.GATHERING, START, END,
                "London Java Community", lines("Skills Matter", "London, GB"),
                null, null, "https://meetup.com/events/123",
                "/planned-gatherings/abc"
        );

        CalendarEntry redacted = redactor.redact(gathering);

        assertThat(redacted.mainTitle()).isEqualTo("London Java Community");
        assertThat(redacted.subTitle()).isEqualTo(lines("Skills Matter", "London, GB"));
        assertThat(redacted.mapsUrl()).isEqualTo("https://meetup.com/events/123");
        assertThat(redacted.editPath()).isNull();
    }

    /**
     * That Ted is speaking at a gathering is public by decision (the venue and time are already
     * public), so the {@code speaking} flag must survive redaction and reach the anonymous
     * calendar. This is the field the calendar renders as a "Speaking" badge.
     */
    @Test
    void gatheringSpeakingFlagSurvivesRedaction() {
        CalendarEntry gathering = new CalendarEntry(
                EntryKind.GATHERING, START, END,
                "London Java Community", lines("Skills Matter", "London, GB"),
                null, null, "https://meetup.com/events/123",
                true, "/planned-gatherings/abc"
        );

        CalendarEntry redacted = redactor.redact(gathering);

        assertThat(redacted.speaking())
                .as("speaking is public, so it survives redaction for anonymous viewers")
                .isTrue();
        assertThat(redacted.editPath()).isNull();
    }

    /**
     * Conferences carry no speaking marker today, and the branch drops it explicitly (rather than
     * defaulting), so a conference never renders a speaking badge until submission tracking lands.
     */
    @Test
    void conferenceSpeakingIsDropped() {
        CalendarEntry conference = new CalendarEntry(
                EntryKind.CONFERENCE, START, END,
                "DDD Europe 2026", lines("Frankfurt, Germany"),
                null, null, "https://dddeurope.com",
                true, "/plan-conference/abc"
        );

        CalendarEntry redacted = redactor.redact(conference);

        assertThat(redacted.speaking())
                .as("conferences drop speaking until submission tracking exists")
                .isFalse();
    }

    /**
     * A private social event is the one entry kind that IS redacted for anonymous viewers: the
     * title becomes "Busy", the venue name is dropped, and the owner's re-localizing time
     * {@link SubtitleLine.Range} becomes a fixed, zone-labelled {@link SubtitleLine.FixedRange}.
     * Only the city and the time survive. (See docs/PrivateSocialEventPlan.md and CLAUDE.md.)
     */
    @Test
    void privateEventBecomesBusyDroppingTitleAndVenue() {
        ZonedTimestamp start = torontoTime(19, 0);
        ZonedTimestamp end = torontoTime(22, 0);
        CalendarEntry privateEvent = new CalendarEntry(
                EntryKind.PRIVATE_EVENT, START, END,
                "Dinner with the Smiths", List.of(
                        new SubtitleLine.Text("Alo"),
                        new SubtitleLine.Text("Toronto, Canada"),
                        new SubtitleLine.Range(start, end)),
                null, null, null,
                "/planned-private-events/abc"
        );

        CalendarEntry redacted = redactor.redact(privateEvent);

        assertThat(redacted.mainTitle()).isEqualTo("Busy");
        assertThat(redacted.subTitle()).isEqualTo(List.of(
                new SubtitleLine.FixedRange(start, end),
                new SubtitleLine.Text("Toronto, Canada")));
        // Title and venue are gone — asserting on their absence, per the redaction rules.
        assertThat(redacted.subTitle())
                .doesNotContain(new SubtitleLine.Text("Alo"));
        assertThat(redacted.continuationTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
        assertThat(redacted.mapsUrl()).isNull();
        assertThat(redacted.editPath()).isNull();
    }

    private static ZonedTimestamp torontoTime(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, hour, minute), TORONTO);
    }

    private static List<SubtitleLine> lines(String... values) {
        return Arrays.stream(values).<SubtitleLine>map(SubtitleLine.Text::new).toList();
    }
}

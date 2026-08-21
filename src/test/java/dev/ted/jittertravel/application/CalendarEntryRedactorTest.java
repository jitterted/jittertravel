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
                true, "/planned-gatherings/abc", null
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
                true, "/plan-conference/abc", AttendanceCommitment.GOING
        );

        CalendarEntry redacted = redactor.redact(conference);

        assertThat(redacted.speaking())
                .as("conferences drop speaking until submission tracking exists")
                .isFalse();
    }

    /**
     * The commitment level is public by decision: it is the already-collapsed "Maybe", not the
     * submission status behind it, so an anonymous viewer sees the same chip Ted does.
     */
    @Test
    void conferenceCommitmentSurvivesRedaction() {
        CalendarEntry conference = new CalendarEntry(
                EntryKind.CONFERENCE, START, END,
                "J-Fall", lines("Ede, Netherlands"),
                null, null, null,
                false, "/plan-conference/abc", AttendanceCommitment.WATCHING
        );

        CalendarEntry redacted = redactor.redact(conference);

        assertThat(redacted.commitment())
                .as("the collapsed commitment level is public, so it survives redaction")
                .isEqualTo(AttendanceCommitment.WATCHING);
    }

    /**
     * Commitment applies to conferences alone. Every other branch names it explicitly as null
     * rather than copying {@code entry.commitment()} through, so a projector that one day stamps a
     * commitment onto the wrong kind cannot publish it by accident.
     */
    @Test
    void commitmentIsDroppedFromEveryNonConferenceKind() {
        for (EntryKind kind : List.of(EntryKind.LODGING, EntryKind.FLIGHT, EntryKind.TRAIN,
                                      EntryKind.GATHERING, EntryKind.PRIVATE_EVENT)) {
            CalendarEntry entry = new CalendarEntry(
                    kind, START, END,
                    "Whatever", lines("Somewhere"),
                    null, null, null,
                    false, null, AttendanceCommitment.WATCHING, null, null
            );

            assertThat(redactor.redact(entry).commitment())
                    .as("commitment must not survive redaction on " + kind)
                    .isNull();
        }
    }

    /**
     * A private social event is the one entry kind that IS redacted for anonymous viewers: the
     * title becomes "Busy", the venue name is dropped, and the owner's re-localizing time
     * {@link SubtitleLine.Range} becomes a fixed, zone-labelled {@link SubtitleLine.FixedRange}.
     * Only the city and the time survive. (See docs/archived/PrivateSocialEventPlan.md and CLAUDE.md.)
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

    /**
     * A ground transfer is the one travel kind whose owner title names a hotel, so — unlike FLIGHT
     * and TRAIN, whose route titles are public — the title cannot survive. What is left is the
     * generic word and the projector's already-public route line.
     */
    @Test
    void groundTransferBecomesTheGenericWordDroppingTheHotelNameFromTheTitle() {
        ZonedTimestamp departs = torontoTime(12, 0);
        ZonedTimestamp arrives = torontoTime(12, 45);
        CalendarEntry transfer = new CalendarEntry(
                EntryKind.GROUND_TRANSFER, START, END,
                "\uD83D\uDE95 DEN → Marriott Lone Tree", List.of(new SubtitleLine.Range(departs, arrives)),
                null, null, null,
                false, null, null, "DEN → Lone Tree, CO, US",
                "/ground-transfers/11111111-2222-3333-4444-555555555555/cancel"
        );

        CalendarEntry redacted = redactor.redact(transfer);

        assertThat(redacted.mainTitle()).isEqualTo("\uD83D\uDE95 Ground transfer");
        assertThat(redacted.mainTitle()).doesNotContain("Marriott Lone Tree");
        assertThat(redacted.subTitle())
                .isEqualTo(List.of(new SubtitleLine.Text("DEN → Lone Tree, CO, US")));
        assertThat(redacted.continuationTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
        assertThat(redacted.mapsUrl()).isNull();
        assertThat(redacted.editPath()).isNull();
        // The owner's cancel link is an action on an OWNER-only surface: publishing it would tell a
        // stranger both that the surface exists and the id of the transfer behind it.
        assertThat(redacted.cancelPath()).isNull();
    }

    /**
     * Redaction rule 2: on a travel entry a {@code SubtitleLine} carrying a {@code ZonedTimestamp}
     * must never survive, because {@code ZonedTimeTag} writes the UTC instant into the
     * {@code datetime} attribute — a clock time leaks there even when the visible text does not.
     */
    @Test
    void noTimestampBearingSubtitleSurvivesOnAGroundTransfer() {
        CalendarEntry transfer = new CalendarEntry(
                EntryKind.GROUND_TRANSFER, START, END,
                "DEN → Marriott Lone Tree", List.of(
                        new SubtitleLine.Range(torontoTime(12, 0), torontoTime(12, 45)),
                        new SubtitleLine.At("Departs", torontoTime(12, 0)),
                        new SubtitleLine.FixedRange(torontoTime(12, 0), torontoTime(12, 45))),
                null, null, null,
                false, null, null, "DEN → Lone Tree, CO, US", null
        );

        assertThat(redactor.redact(transfer).subTitle())
                .allMatch(SubtitleLine.Text.class::isInstance,
                          "every surviving subtitle line is plain text");
    }

    private static ZonedTimestamp torontoTime(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, hour, minute), TORONTO);
    }

    private static List<SubtitleLine> lines(String... values) {
        return Arrays.stream(values).<SubtitleLine>map(SubtitleLine.Text::new).toList();
    }
}

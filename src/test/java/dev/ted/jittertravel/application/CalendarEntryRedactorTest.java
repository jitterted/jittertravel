package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two tests that used to live here are gone, and their absence is the point: with the
 * kind-specific fields moved into {@link EntryDetails}, neither case can be <em>constructed</em>
 * any more, let alone leak.
 * <ul>
 *   <li>{@code conferenceSpeakingIsDropped} — {@code EntryDetails.Conference} has no
 *       {@code speaking} component, so a conference cannot carry a speaking marker to be dropped.
 *       When submission tracking gives it one, that test comes back with the field.</li>
 *   <li>{@code commitmentIsDroppedFromEveryNonConferenceKind} — a commitment now exists only on
 *       {@code EntryDetails.Conference}, so no other kind can hold one. That loop was flagged in
 *       {@code docs/RendererVsProjectorResponsibilities.md} as a test that grows with the kinds;
 *       it is replaced by {@link #redactionNeverChangesTheKind()}, which does not.</li>
 * </ul>
 */
class CalendarEntryRedactorTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 7, 1, 14, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 3, 11, 0);
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private final CalendarEntryRedactor redactor = new CalendarEntryRedactor();

    @Test
    void lodgingHidesHotelNameMapsUrlAndEditPath() {
        CalendarEntry hotel = new CalendarEntry(
                START, END,
                "Marriott Grand", lines("Berlin, Germany"),
                "Marriott Grand cont'd", lines("Berlin, Germany"),
                new EntryDetails.Lodging("https://maps.google.com/marriott", "/booked-hotels/abc")
        );

        CalendarEntry redacted = redactor.redact(hotel);

        assertThat(redacted.mainTitle()).isEqualTo("Hotel");
        assertThat(redacted.continuationTitle()).isEqualTo("Hotel cont'd");
        assertThat(redacted.details()).isEqualTo(new EntryDetails.Lodging(null, null));
    }

    @Test
    void lodgingPreservesLocationSubTitle() {
        CalendarEntry hotel = new CalendarEntry(
                START, END,
                "Marriott Grand", lines("Berlin, Germany"),
                "Marriott Grand cont'd", lines("Berlin, Germany"),
                new EntryDetails.Lodging("https://maps.google.com/marriott", null)
        );

        CalendarEntry redacted = redactor.redact(hotel);

        assertThat(redacted.subTitle()).isEqualTo(lines("Berlin, Germany"));
        assertThat(redacted.continuationSubTitle()).isEqualTo(lines("Berlin, Germany"));
    }

    @Test
    void flightHidesTimesButKeepsRoute() {
        CalendarEntry flight = new CalendarEntry(
                START, END,
                "✈️ SFO→JFK", lines("9:00 AM → 5:00 PM"),
                new EntryDetails.Flight("/booked-flights/abc")
        );

        CalendarEntry redacted = redactor.redact(flight);

        assertThat(redacted.mainTitle()).isEqualTo("✈️ SFO→JFK");
        assertThat(redacted.subTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
        assertThat(redacted.details()).isEqualTo(new EntryDetails.Flight(null));
    }

    @Test
    void trainHidesTimesAndServiceIdButKeepsRoute() {
        CalendarEntry train = new CalendarEntry(
                START, START,
                "🚄 London → Paris", lines("TGV123", "9:00 AM → 2:30 PM"),
                new EntryDetails.Train("/booked-trains/abc")
        );

        CalendarEntry redacted = redactor.redact(train);

        assertThat(redacted.mainTitle()).isEqualTo("🚄 London → Paris");
        assertThat(redacted.subTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
        assertThat(redacted.details()).isEqualTo(new EntryDetails.Train(null));
    }

    @Test
    void conferenceKeepsItsPublicNameAndLocation() {
        CalendarEntry conference = new CalendarEntry(
                START, END,
                "DDD Europe 2026", lines("Frankfurt, Germany"),
                "DDD Europe 2026 cont'd", lines("Frankfurt, Germany"),
                new EntryDetails.Conference(AttendanceCommitment.GOING)
        );

        CalendarEntry redacted = redactor.redact(conference);

        assertThat(redacted.mainTitle()).isEqualTo("DDD Europe 2026");
        assertThat(redacted.subTitle()).isEqualTo(lines("Frankfurt, Germany"));
        assertThat(redacted.continuationTitle()).isEqualTo("DDD Europe 2026 cont'd");
        assertThat(redacted.continuationSubTitle()).isEqualTo(lines("Frankfurt, Germany"));
    }

    /**
     * Gatherings are public events, like conferences: name, venue, info URL and times all stay
     * visible. Private social events are the separate {@link EntryDetails.PrivateEvent} kind below.
     */
    @Test
    void gatheringKeepsPublicDetailsButDropsEditPath() {
        CalendarEntry gathering = new CalendarEntry(
                START, END,
                "London Java Community", lines("Skills Matter", "London, GB"),
                new EntryDetails.Gathering("https://meetup.com/events/123", false,
                                           "/planned-gatherings/abc")
        );

        CalendarEntry redacted = redactor.redact(gathering);

        assertThat(redacted.mainTitle()).isEqualTo("London Java Community");
        assertThat(redacted.subTitle()).isEqualTo(lines("Skills Matter", "London, GB"));
        assertThat(redacted.details())
                .isEqualTo(new EntryDetails.Gathering("https://meetup.com/events/123", false, null));
    }

    /**
     * That Ted is speaking at a gathering is public by decision (the venue and time are already
     * public), so the {@code speaking} flag must survive redaction and reach the anonymous
     * calendar. This is the field the calendar renders as a "Speaking" badge.
     */
    @Test
    void gatheringSpeakingFlagSurvivesRedaction() {
        CalendarEntry gathering = new CalendarEntry(
                START, END,
                "London Java Community", lines("Skills Matter", "London, GB"),
                new EntryDetails.Gathering("https://meetup.com/events/123", true,
                                           "/planned-gatherings/abc")
        );

        CalendarEntry redacted = redactor.redact(gathering);

        assertThat(gathering(redacted).speaking())
                .as("speaking is public, so it survives redaction for anonymous viewers")
                .isTrue();
        assertThat(gathering(redacted).editPath()).isNull();
    }

    /**
     * The commitment level is public by decision: it is the already-collapsed "Maybe", not the
     * submission status behind it, so an anonymous viewer sees the same chip Ted does.
     */
    @Test
    void conferenceCommitmentSurvivesRedaction() {
        CalendarEntry conference = new CalendarEntry(
                START, END,
                "J-Fall", lines("Ede, Netherlands"),
                new EntryDetails.Conference(AttendanceCommitment.WATCHING)
        );

        CalendarEntry redacted = redactor.redact(conference);

        assertThat(redacted.details())
                .as("the collapsed commitment level is public, so it survives redaction")
                .isEqualTo(new EntryDetails.Conference(AttendanceCommitment.WATCHING));
    }

    /**
     * Redaction removes content; it never re-files an entry into a different lane. This replaces
     * the old per-kind commitment loop: it states one invariant over every kind, and adding a kind
     * extends the fixture rather than the claim.
     */
    @Test
    void redactionNeverChangesTheKind() {
        for (CalendarEntry entry : oneOfEveryKind()) {
            assertThat(redactor.redact(entry).kind())
                    .as("redacting a " + entry.kind() + " must leave it in its own lane")
                    .isEqualTo(entry.kind());
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
                START, END,
                "Dinner with the Smiths", List.of(
                        new SubtitleLine.Text("Alo"),
                        new SubtitleLine.Text("Toronto, Canada"),
                        new SubtitleLine.Range(start, end)),
                new EntryDetails.PrivateEvent()
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
                START, END,
                "🚕 DEN → Marriott Lone Tree", List.of(new SubtitleLine.Range(departs, arrives)),
                new EntryDetails.GroundTransfer("DEN → Lone Tree, CO, US",
                        "/ground-transfers/11111111-2222-3333-4444-555555555555/cancel")
        );

        CalendarEntry redacted = redactor.redact(transfer);

        assertThat(redacted.mainTitle()).isEqualTo("🚕 Ground transfer");
        assertThat(redacted.mainTitle()).doesNotContain("Marriott Lone Tree");
        assertThat(redacted.subTitle())
                .isEqualTo(List.of(new SubtitleLine.Text("DEN → Lone Tree, CO, US")));
        assertThat(redacted.continuationTitle()).isNull();
        assertThat(redacted.continuationSubTitle()).isNull();
        // The publishable route has done its job by now, and the owner's cancel link is an action
        // on an OWNER-only surface: publishing it would tell a stranger both that the surface
        // exists and the id of the transfer behind it.
        assertThat(redacted.details()).isEqualTo(new EntryDetails.GroundTransfer(null, null));
    }

    /**
     * Redaction rule 2: on a travel entry a {@code SubtitleLine} carrying a {@code ZonedTimestamp}
     * must never survive, because {@code ZonedTimeTag} writes the UTC instant into the
     * {@code datetime} attribute — a clock time leaks there even when the visible text does not.
     */
    @Test
    void noTimestampBearingSubtitleSurvivesOnAGroundTransfer() {
        CalendarEntry transfer = new CalendarEntry(
                START, END,
                "DEN → Marriott Lone Tree", List.of(
                        new SubtitleLine.Range(torontoTime(12, 0), torontoTime(12, 45)),
                        new SubtitleLine.At("Departs", torontoTime(12, 0)),
                        new SubtitleLine.FixedRange(torontoTime(12, 0), torontoTime(12, 45))),
                new EntryDetails.GroundTransfer("DEN → Lone Tree, CO, US", null)
        );

        assertThat(redactor.redact(transfer).subTitle())
                .allMatch(SubtitleLine.Text.class::isInstance,
                          "every surviving subtitle line is plain text");
    }

    /** One entry per {@link EntryKind}, so a kind-wide claim can be stated once. */
    private static List<CalendarEntry> oneOfEveryKind() {
        return List.of(
                entryWith(new EntryDetails.Conference(AttendanceCommitment.WATCHING)),
                entryWith(new EntryDetails.Gathering("https://example.com", true, "/edit")),
                entryWith(new EntryDetails.PrivateEvent()),
                entryWith(new EntryDetails.Flight("/edit")),
                entryWith(new EntryDetails.Train("/edit")),
                entryWith(new EntryDetails.GroundTransfer("A → B", "/cancel")),
                entryWith(new EntryDetails.Lodging("https://maps.example.com", "/edit")));
    }

    private static CalendarEntry entryWith(EntryDetails details) {
        return new CalendarEntry(START, END, "Whatever", lines("Somewhere"), details);
    }

    private static EntryDetails.Gathering gathering(CalendarEntry entry) {
        return (EntryDetails.Gathering) entry.details();
    }

    private static ZonedTimestamp torontoTime(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, hour, minute), TORONTO);
    }

    private static List<SubtitleLine> lines(String... values) {
        return Arrays.stream(values).<SubtitleLine>map(SubtitleLine.Text::new).toList();
    }
}

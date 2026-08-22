package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Lifecycle propagation for the calendar's <strong>removal</strong> events, across
 * <strong>both</strong> read models at once.
 * <p>
 * Since the S2 refactor the calendar is projected twice — the seven owner projectors behind
 * {@link CalendarAggregator}, and {@link PublicCalendarProjector} for anonymous visitors — from the
 * same event stream, by two separate switches. Nothing makes those switches agree. A removal handled
 * on only one side does not fail loudly; it fails in whichever direction that side is. Handled only
 * on the owner side, the entry disappears for Ted and **stays on the anonymous calendar
 * indefinitely** — a booking Ted has cancelled, still telling strangers where he sleeps.
 * <p>
 * So each scenario below drives one event stream into both read models and asserts the entry is in
 * both, then gone from both. The presence assertion is not decoration: without it, a creation event
 * that one side ignored would leave that side empty from the start and the removal assertion would
 * pass for the wrong reason.
 * <p>
 * <strong>Adding a removal event means adding a row here.</strong> Nothing forces that — this is the
 * scenario-test guard the project prefers over sealing {@code Event}, and it is only as complete as
 * its list. The alternative (a source scan comparing the two switches' matched types, in the style
 * of {@code PublicCalendarBuildsOnlyPublishableEntriesTest}) is recorded in
 * {@code docs/Cleanup_Tasks.md} if this ever proves too easy to forget.
 */
class CalendarRemovalPropagationTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");

    static Stream<Arguments> removalScenarios() {
        HotelBookingId bookingId = HotelBookingId.random();
        GroundTransferId transferId = GroundTransferId.random();
        ConferenceId cancelledConference = ConferenceId.random();
        ConferenceId declinedConference = ConferenceId.random();
        ConferenceId confirmedThenCancelled = ConferenceId.random();

        return Stream.of(
                arguments("a cancelled hotel booking",
                        List.of(hotelBooked(bookingId)),
                        new HotelBookingCancelled(bookingId, "plans changed")),
                arguments("a cancelled ground transfer",
                        List.of(transferPlanned(transferId)),
                        new GroundTransferCancelled(transferId)),
                arguments("an organizer-cancelled conference",
                        List.of(conferencePlanned(cancelledConference, "PLoP")),
                        new ConferenceCancelled(cancelledConference, "organizers pulled it")),
                arguments("a declined conference",
                        List.of(conferencePlanned(declinedConference, "J-Fall")),
                        new ConferenceAttendanceDeclined(declinedConference, "clash",
                                Instant.parse("2026-05-01T00:00:00Z"))),
                // The state that is easiest to get wrong: an entry both projectors have already
                // rewritten once (WATCHING -> GOING) still has to be removable by id.
                arguments("a conference cancelled after Ted had committed to it",
                        List.of(conferencePlanned(confirmedThenCancelled, "dev2next"),
                                new ConferenceAttendanceConfirmed(confirmedThenCancelled,
                                        AttendanceBasis.SPEAKING_ACCEPTED,
                                        Instant.parse("2026-05-01T00:00:00Z"))),
                        new ConferenceCancelled(confirmedThenCancelled, "organizers pulled it")));
    }

    @ParameterizedTest(name = "{0} leaves both the owner''s calendar and the public one")
    @MethodSource("removalScenarios")
    void removalReachesBothReadModels(String scenario, List<Event> creating, Event removal) {
        OwnerCalendar owner = new OwnerCalendar();
        PublicCalendarProjector publicCalendar = new PublicCalendarProjector();

        handle(creating, owner, publicCalendar);

        assertThat(owner.entries())
                .as("%s must be on the owner's calendar before it can be removed from it", scenario)
                .isNotEmpty();
        assertThat(publicCalendar.entries())
                .as("%s must be on the public calendar before it can be removed from it", scenario)
                .isNotEmpty();

        handle(List.of(removal), owner, publicCalendar);

        assertThat(owner.entries())
                .as("%s must leave the owner's calendar", scenario)
                .isEmpty();
        assertThat(publicCalendar.entries())
                .as("%s must leave the public calendar — an entry that survives here is a "
                    + "cancelled booking still telling strangers where Ted is", scenario)
                .isEmpty();
    }

    private static void handle(List<Event> events, OwnerCalendar owner,
                               PublicCalendarProjector publicCalendar) {
        owner.handle(events);
        publicCalendar.handle(events.stream().map(CalendarRemovalPropagationTest::stored));
    }

    /** The owner's seven calendar projectors, wired as production wires them. */
    private static class OwnerCalendar {
        private final ConferenceCalendarProjector conferences = new ConferenceCalendarProjector();
        private final FlightCalendarProjector flights = new FlightCalendarProjector();
        private final TrainCalendarProjector trains = new TrainCalendarProjector();
        private final HotelCalendarProjector hotels = new HotelCalendarProjector();
        private final GatheringCalendarProjector gatherings = new GatheringCalendarProjector();
        private final PrivateEventCalendarProjector privateEvents = new PrivateEventCalendarProjector();
        private final GroundTransferCalendarProjector transfers = new GroundTransferCalendarProjector();

        void handle(List<Event> events) {
            Stream.of(conferences, flights, trains, hotels, gatherings, privateEvents, transfers)
                    .forEach(projector -> projector.handle(
                            events.stream().map(CalendarRemovalPropagationTest::stored)));
        }

        List<CalendarEntry> entries() {
            return new CalendarAggregator(conferences, flights, trains, hotels, gatherings,
                                          privateEvents, transfers).allEntries();
        }
    }

    private static HotelBooked hotelBooked(HotelBookingId bookingId) {
        return new HotelBooked(bookingId, "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 15, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                BookingIntent.FINAL, "https://maps.google.com/marriott", null);
    }

    private static GroundTransferPlanned transferPlanned(GroundTransferId transferId) {
        return new GroundTransferPlanned(transferId,
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 45), DENVER));
    }

    private static ConferencePlanned conferencePlanned(ConferenceId conferenceId, String name) {
        return new ConferencePlanned(conferenceId, name,
                zoned(LocalDateTime.of(2026, 11, 5, 9, 0), AMSTERDAM),
                zoned(LocalDateTime.of(2026, 11, 6, 17, 0), AMSTERDAM),
                "Reehorst",
                new Address("1 Conf St", "Ede", "", "6710", "Netherlands", null),
                ConferenceFormat.CALL_FOR_PAPERS);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }

    private static ZonedTimestamp zoned(LocalDateTime local, ZoneId zone) {
        return ZonedTimestamp.fromLocal(local, zone);
    }
}

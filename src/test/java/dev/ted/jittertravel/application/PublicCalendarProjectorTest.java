package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.AirportCode;
import dev.ted.jittertravel.domain.AttendanceBasis;
import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.domain.ConferenceAttendanceConfirmed;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.FlightBooked;
import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.GatheringPlanned;
import dev.ted.jittertravel.domain.GroundTransferCancelled;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.HotelBooked;
import dev.ted.jittertravel.domain.HotelBookingCancelled;
import dev.ted.jittertravel.domain.HotelBookingId;
import dev.ted.jittertravel.domain.PrivateEventId;
import dev.ted.jittertravel.domain.PrivateEventPlanned;
import dev.ted.jittertravel.domain.TrainBooked;
import dev.ted.jittertravel.domain.TrainStationAddress;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an anonymous visitor is shown, per kind — and, in
 * {@link #everyEntryCarriesOnlyPublishableDetails()}, the one invariant that replaced the
 * redactor's compile-time forcing function.
 * <p>
 * That test is written so it does <strong>not</strong> need editing when a kind is added: it states
 * "whatever this projector emits carries {@link EntryDetails.Publishable} details", not a list of
 * permitted types. A test that must be edited on every change stops guarding, because editing it is
 * exactly what a leaking change would do.
 */
class PublicCalendarProjectorTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    private final PublicCalendarProjector projector = new PublicCalendarProjector();

    /**
     * The replacement for redaction rule 1's compile-time check. Inside the projector the same
     * claim is a compiler check — every entry is built through a helper taking a
     * {@code Publishable} — and this is the runtime backstop that survives a refactor of those
     * helpers.
     */
    @Test
    void everyEntryCarriesOnlyPublishableDetails() {
        projector.handle(oneOfEveryKind().stream());

        assertThat(projector.entries())
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry.details())
                        .as("a %s entry reached the public calendar", entry.kind())
                        .isInstanceOf(EntryDetails.Publishable.class));
    }

    @Test
    void aStayPublishesTheWordHotelAndTheCityButNeverTheName() {
        projector.handle(Stream.of(stored(new HotelBooked(HotelBookingId.random(), "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 15, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                BookingIntent.FINAL, "https://maps.google.com/marriott", null))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("Hotel");
        assertThat(entry.continuationTitle()).isEqualTo("Hotel cont'd");
        assertThat(entry.subTitle()).isEqualTo(List.of(new SubtitleLine.Text("Lone Tree, US")));
        assertThat(entry.toString())
                .as("neither the hotel's name nor its map link may appear anywhere on the entry")
                .doesNotContain("Marriott Lone Tree")
                .doesNotContain("maps.google.com");
    }

    @Test
    void aCancelledStayLeavesThePublicCalendar() {
        HotelBookingId bookingId = HotelBookingId.random();
        projector.handle(Stream.of(stored(new HotelBooked(bookingId, "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 15, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                BookingIntent.FINAL, "", null))));

        projector.handle(Stream.of(stored(new HotelBookingCancelled(bookingId, "plans changed"))));

        assertThat(projector.entries()).isEmpty();
    }

    @Test
    void aFlightPublishesItsRouteAndNoTimeAtAll() {
        projector.handle(Stream.of(stored(new FlightBooked(FlightId.random(), "United", "UA123",
                new AirportCode("SFO"), zoned(LocalDateTime.of(2026, 6, 17, 9, 0), DENVER),
                new AirportCode("JFK"), zoned(LocalDateTime.of(2026, 6, 17, 17, 0), DENVER)))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("✈️ SFO→JFK");
        assertThat(entry.subTitle())
                .as("a leg's only possible subtitle is when it runs, and that is private")
                .isNull();
        assertThat(entry.toString())
                .as("the flight number is a carrier identifier, never published")
                .doesNotContain("UA123");
    }

    @Test
    void anOvernightFlightStillSplitsIntoTheTwoDayColumnsItOccupies() {
        projector.handle(Stream.of(stored(new FlightBooked(FlightId.random(), "United", "UA59",
                new AirportCode("SFO"), zoned(LocalDateTime.of(2026, 6, 26, 13, 55), DENVER),
                new AirportCode("FRA"), zoned(LocalDateTime.of(2026, 6, 27, 9, 45), LONDON)))));

        assertThat(projector.entries())
                .as("which days a journey occupies is public; when it departs is not")
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.subTitle()).isNull());
    }

    @Test
    void aTrainPublishesItsCitiesButNeverItsServiceId() {
        projector.handle(Stream.of(stored(new TrainBooked(TrainTripId.random(),
                new TrainStationAddress("Frankfurt Hbf", "Frankfurt", "DE", ""),
                zoned(LocalDateTime.of(2026, 6, 28, 9, 0), LONDON),
                new TrainStationAddress("Gare du Nord", "Paris", "FR", ""),
                zoned(LocalDateTime.of(2026, 6, 28, 14, 30), LONDON),
                "ICE 123"))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("🚄 Frankfurt → Paris");
        assertThat(entry.toString()).doesNotContain("ICE 123");
    }

    /**
     * The transfer whose owner title reads "DEN → Marriott Lone Tree". The public projector never
     * builds that title, so there is nothing to strip: it names the endpoints in their publishable
     * form, which for a hotel end is its city.
     */
    @Test
    void aTransferPublishesTheGenericWordAndCitiesNeverTheHotelItGoesTo() {
        projector.handle(Stream.of(stored(new GroundTransferPlanned(GroundTransferId.random(),
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 45), DENVER)))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("🚕 Ground transfer");
        assertThat(entry.subTitle())
                .isEqualTo(List.of(new SubtitleLine.Text("DEN → Lone Tree, CO, US")));
        assertThat(entry.toString()).doesNotContain("Marriott Lone Tree");
    }

    @Test
    void aTransferOutOfAHotelReadsTheOtherWayRoundAndStillNamesNoHotel() {
        projector.handle(Stream.of(stored(new GroundTransferPlanned(GroundTransferId.random(),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 9, 11, 45), DENVER)))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.subTitle())
                .isEqualTo(List.of(new SubtitleLine.Text("Lone Tree, CO, US → DEN")));
        assertThat(entry.toString()).doesNotContain("Marriott Lone Tree");
    }

    @Test
    void aCancelledTransferLeavesThePublicCalendar() {
        GroundTransferId transferId = GroundTransferId.random();
        projector.handle(Stream.of(stored(new GroundTransferPlanned(transferId,
                "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                "", "Marriott Lone Tree",
                new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 0), DENVER),
                zoned(LocalDateTime.of(2026, 7, 5, 12, 45), DENVER)))));

        projector.handle(Stream.of(stored(new GroundTransferCancelled(transferId))));

        assertThat(projector.entries()).isEmpty();
    }

    /**
     * "Busy", the city, and the time in the event's own zone — built that way from the start rather
     * than reverse-engineered out of an owner subtitle, which is what the redactor had to do.
     */
    @Test
    void aPrivateEventPublishesBusyTheCityAndAFixedTimeOnly() {
        ZonedTimestamp start = zoned(LocalDateTime.of(2026, 6, 20, 19, 0), TORONTO);
        ZonedTimestamp end = zoned(LocalDateTime.of(2026, 6, 20, 22, 0), TORONTO);
        projector.handle(Stream.of(stored(new PrivateEventPlanned(PrivateEventId.random(),
                "Dinner with the Smiths", "Alo",
                new Address("5 Dine Way", "Toronto", "ON", "M5V", "Canada", null), start, end))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("Busy");
        assertThat(entry.subTitle()).isEqualTo(List.of(
                new SubtitleLine.FixedRange(start, end),
                new SubtitleLine.Text("Toronto, Canada")));
        assertThat(entry.toString())
                .doesNotContain("Dinner with the Smiths")
                .doesNotContain("Alo");
    }

    @Test
    void aGatheringIsPublishedInFullIncludingItsSpeakingMarkerAndInfoUrl() {
        projector.handle(Stream.of(stored(new GatheringPlanned(GatheringId.random(),
                "London Java Community", "Skills Matter",
                new Address("3 Meet Ln", "London", "", "EC1A 1BB", "GB", null),
                zoned(LocalDateTime.of(2026, 6, 18, 18, 0), LONDON),
                zoned(LocalDateTime.of(2026, 6, 18, 21, 0), LONDON),
                true, "https://meetup.com/ljc/events/123"))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle()).isEqualTo("London Java Community");
        assertThat(entry.subTitle()).hasSize(3);
        assertThat(entry.details()).isEqualTo(
                new EntryDetails.PublicGathering("https://meetup.com/ljc/events/123", true));
    }

    @Test
    void aPlannedConferenceIsPublishedAsMerelyWatched() {
        projector.handle(Stream.of(stored(conferencePlanned(ConferenceId.random(), "J-Fall"))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(AttendanceCommitment.WATCHING));
    }

    @Test
    void confirmingAttendanceTurnsThePublicEntryIntoGoing() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(conferencePlanned(conferenceId, "dev2next"))));

        projector.handle(Stream.of(stored(new ConferenceAttendanceConfirmed(conferenceId,
                AttendanceBasis.SPEAKING_ACCEPTED, Instant.parse("2026-05-01T00:00:00Z")))));

        assertThat(projector.entries().getFirst().details())
                .isEqualTo(new EntryDetails.PublicConference(AttendanceCommitment.GOING));
    }

    /**
     * The basis is submission status wearing a different hat: the projector never reads it, so
     * redaction rule 1 is satisfied structurally rather than by anything stripping it later.
     */
    @Test
    void theBasisForGoingNeverReachesThePublicEntry() {
        ConferenceId conferenceId = ConferenceId.random();
        projector.handle(Stream.of(stored(conferencePlanned(conferenceId, "dev2next"))));

        projector.handle(Stream.of(stored(new ConferenceAttendanceConfirmed(conferenceId,
                AttendanceBasis.SPEAKING_ACCEPTED, Instant.parse("2026-05-01T00:00:00Z")))));

        assertThat(projector.entries().getFirst().toString())
                .doesNotContain("SPEAKING_ACCEPTED");
    }

    @Test
    void aDeclinedOrCancelledConferenceLeavesThePublicCalendarEntirely() {
        ConferenceId declined = ConferenceId.random();
        ConferenceId cancelled = ConferenceId.random();
        projector.handle(Stream.of(
                stored(conferencePlanned(declined, "Declined Conf")),
                stored(conferencePlanned(cancelled, "Cancelled Conf"))));

        projector.handle(Stream.of(
                stored(new ConferenceAttendanceDeclined(declined, "clash",
                        Instant.parse("2026-05-01T00:00:00Z"))),
                stored(new ConferenceCancelled(cancelled, "organizers pulled it"))));

        assertThat(projector.entries()).isEmpty();
    }

    private static ConferencePlanned conferencePlanned(ConferenceId conferenceId, String name) {
        return new ConferencePlanned(conferenceId, name,
                zoned(LocalDateTime.of(2026, 11, 5, 9, 0), LONDON),
                zoned(LocalDateTime.of(2026, 11, 6, 17, 0), LONDON),
                "Grand Venue",
                new Address("1 Conf St", "Ede", "", "6710", "Netherlands", null),
                ConferenceFormat.CALL_FOR_PAPERS);
    }

    /** One event of every kind the public calendar can show. */
    private static List<StoredEvent> oneOfEveryKind() {
        return List.of(
                stored(conferencePlanned(ConferenceId.random(), "J-Fall")),
                stored(new GatheringPlanned(GatheringId.random(), "London Java Community",
                        "Skills Matter",
                        new Address("3 Meet Ln", "London", "", "EC1A 1BB", "GB", null),
                        zoned(LocalDateTime.of(2026, 6, 18, 18, 0), LONDON),
                        zoned(LocalDateTime.of(2026, 6, 18, 21, 0), LONDON),
                        true, "https://meetup.com/ljc/events/123")),
                stored(new PrivateEventPlanned(PrivateEventId.random(), "Dinner", "Alo",
                        new Address("5 Dine Way", "Toronto", "ON", "M5V", "Canada", null),
                        zoned(LocalDateTime.of(2026, 6, 20, 19, 0), TORONTO),
                        zoned(LocalDateTime.of(2026, 6, 20, 22, 0), TORONTO))),
                stored(new FlightBooked(FlightId.random(), "United", "UA123",
                        new AirportCode("SFO"), zoned(LocalDateTime.of(2026, 6, 17, 9, 0), DENVER),
                        new AirportCode("JFK"), zoned(LocalDateTime.of(2026, 6, 17, 17, 0), DENVER))),
                stored(new TrainBooked(TrainTripId.random(),
                        new TrainStationAddress("Frankfurt Hbf", "Frankfurt", "DE", ""),
                        zoned(LocalDateTime.of(2026, 6, 28, 9, 0), LONDON),
                        new TrainStationAddress("Gare du Nord", "Paris", "FR", ""),
                        zoned(LocalDateTime.of(2026, 6, 28, 14, 30), LONDON), "ICE 123")),
                stored(new HotelBooked(HotelBookingId.random(), "Marriott Lone Tree",
                        new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                        zoned(LocalDateTime.of(2026, 7, 5, 15, 0), DENVER),
                        zoned(LocalDateTime.of(2026, 7, 9, 11, 0), DENVER),
                        BookingIntent.FINAL, "", null)),
                stored(new GroundTransferPlanned(GroundTransferId.random(),
                        "DEN", "", new Address("", "Denver", "CO", "", "US", null),
                        "", "Marriott Lone Tree",
                        new Address("6 Sleep St", "Lone Tree", "CO", "80124", "US", null),
                        zoned(LocalDateTime.of(2026, 7, 5, 12, 0), DENVER),
                        zoned(LocalDateTime.of(2026, 7, 5, 12, 45), DENVER))));
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }

    private static ZonedTimestamp zoned(LocalDateTime local, ZoneId zone) {
        return ZonedTimestamp.fromLocal(local, zone);
    }
}

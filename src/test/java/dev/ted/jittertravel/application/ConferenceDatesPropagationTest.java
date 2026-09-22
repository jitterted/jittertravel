package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceDatesChanged;
import dev.ted.jittertravel.domain.ConferenceFormat;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.domain.TalkAccepted;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard for {@link ConferenceDatesChanged}: a moved conference moves on every read model that holds
 * its days — the dashboard and detail page, the owner calendar, the public calendar, the itinerary
 * and the schedule. Drop the arm from any one of them and that read model keeps the planned days,
 * which is the failure that matters: the anonymous calendar saying April while the owner's says
 * March, and nothing else in the tree noticing.
 * <p>
 * The scenario is the one that prompted the event (Ted, 2026-09-22): DevNexus 2027 moved from
 * April 5–7 back a week to March 29–31.
 * <p>
 * <strong>Both orders are covered</strong>, because the two halves of each projector's state are
 * rebuilt by different arms. A move after a commitment must keep the commitment; a commitment after
 * a move must keep the move — the second is the easier one to lose, since every progress arm
 * rebuilds the entry from whatever dates it kept.
 */
class ConferenceDatesPropagationTest {

    private static final ZoneId ATLANTA_ZONE = ZoneId.of("America/New_York");
    private static final Address ATLANTA =
            new Address("285 Andrew Young International Blvd NW", "Atlanta", "GA", "30313", "US", "Atlanta");
    private static final LocalDate PLANNED_FIRST_DAY = LocalDate.of(2027, 4, 5);
    private static final LocalDate MOVED_FIRST_DAY = LocalDate.of(2027, 3, 29);
    private static final LocalDate MOVED_LAST_DAY = LocalDate.of(2027, 3, 31);
    private static final Instant DECIDED_ON = Instant.parse("2026-09-01T00:00:00Z");

    private final ConferenceId conferenceId = ConferenceId.random();
    private final AtomicLong sequence = new AtomicLong();

    @Test
    void dashboardAndDetailPageShowTheMovedDates() {
        ConferenceProjector projector = new ConferenceProjector();
        projector.handle(play(planned(), moved()));

        assertThat(projector.findById(conferenceId))
                .as("the dashboard row for DevNexus")
                .isPresent()
                .map(ConferenceView::startDate)
                .as("the row starts on the moved first day")
                .hasValue(movedStart());
        assertThat(projector.detailById(conferenceId))
                .as("the detail page for DevNexus")
                .isPresent()
                .map(ConferenceDetailView::endDate)
                .as("the detail page ends on the moved last day")
                .hasValue(movedEnd());
    }

    @Test
    void ownerCalendarShowsTheMovedDates() {
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        projector.handle(play(planned(), moved()));

        List<CalendarEntry> entries = projector.entries();

        assertThat(entries)
                .as("one DevNexus entry")
                .hasSize(1);
        CalendarEntry entry = entries.getFirst();
        assertThat(entry.start())
                .as("owner calendar start")
                .isEqualTo(movedStart().localDateTime());
        assertThat(entry.end())
                .as("owner calendar end")
                .isEqualTo(movedEnd().localDateTime());
    }

    @Test
    void publicCalendarShowsTheMovedDates() {
        PublicCalendarProjector projector = new PublicCalendarProjector();
        projector.handle(play(planned(), moved()));

        List<CalendarEntry> entries = projector.entries();

        assertThat(entries)
                .as("one DevNexus entry")
                .hasSize(1);
        CalendarEntry entry = entries.getFirst();
        assertThat(entry.start())
                .as("public calendar start")
                .isEqualTo(movedStart().localDateTime());
        assertThat(entry.end())
                .as("public calendar end")
                .isEqualTo(movedEnd().localDateTime());
        assertThat(entry.details())
                .as("still built through the allow-list")
                .isInstanceOf(EntryDetails.PublicConference.class);
    }

    @Test
    void itineraryShowsTheMovedDays() {
        ItineraryProjector projector = new ItineraryProjector();
        projector.handle(play(planned(), moved()));

        assertThat(projector.entriesForDate(MOVED_FIRST_DAY))
                .as("DevNexus is on the moved first day")
                .hasSize(1)
                .first()
                .isInstanceOf(ConferenceItineraryEntry.class);
        assertThat(projector.entriesForDate(PLANNED_FIRST_DAY))
                .as("and no longer on the planned one")
                .isEmpty();
    }

    @Test
    void scheduleAsksForHotelNightsOnTheMovedDays() {
        ScheduleGapProjector projector = new ScheduleGapProjector(new StaticAirportCityResolver());
        projector.handle(play(planned(), moved()));

        assertThat(projector.problems())
                .filteredOn(ScheduleProblem.MissingHotel.class::isInstance)
                .as("the nights to cover are the moved ones")
                .containsExactly(new ScheduleProblem.MissingHotel(
                        "Atlanta", MOVED_FIRST_DAY, MOVED_LAST_DAY, "DevNexus"));
    }

    @Test
    void aMoveKeepsTheCommitmentAndTheSpeakingBadge() {
        PublicCalendarProjector publicCalendar = new PublicCalendarProjector();
        ConferenceCalendarProjector ownerCalendar = new ConferenceCalendarProjector();
        List<Event> events = List.of(planned(), new TalkAccepted(conferenceId, DECIDED_ON), moved());
        publicCalendar.handle(play(events));
        ownerCalendar.handle(play(events));

        EntryDetails publicDetails = publicCalendar.entries().getFirst().details();
        assertThat(publicDetails)
                .isInstanceOf(EntryDetails.PublicConference.class);
        EntryDetails.PublicConference publicConference = (EntryDetails.PublicConference) publicDetails;
        assertThat(publicConference.commitment())
                .as("public commitment after the move")
                .isEqualTo(AttendanceCommitment.GOING);
        assertThat(publicConference.speaking())
                .as("public speaking badge after the move")
                .isTrue();

        EntryDetails ownerDetails = ownerCalendar.entries().getFirst().details();
        assertThat(ownerDetails)
                .isInstanceOf(EntryDetails.Conference.class);
        EntryDetails.Conference ownerConference = (EntryDetails.Conference) ownerDetails;
        assertThat(ownerConference.commitment())
                .as("owner commitment after the move")
                .isEqualTo(AttendanceCommitment.GOING);
        assertThat(ownerConference.speaking())
                .as("owner speaking badge after the move")
                .isTrue();
    }

    @Test
    void aCommitmentAfterTheMoveKeepsTheMovedDates() {
        List<Event> events = List.of(planned(), moved(), new TalkAccepted(conferenceId, DECIDED_ON));
        ConferenceProjector dashboard = new ConferenceProjector();
        ConferenceCalendarProjector ownerCalendar = new ConferenceCalendarProjector();
        PublicCalendarProjector publicCalendar = new PublicCalendarProjector();
        ItineraryProjector itinerary = new ItineraryProjector();
        dashboard.handle(play(events));
        ownerCalendar.handle(play(events));
        publicCalendar.handle(play(events));
        itinerary.handle(play(events));

        assertThat(dashboard.findById(conferenceId))
                .as("the dashboard row for DevNexus")
                .isPresent()
                .map(ConferenceView::startDate)
                .as("dashboard start after the acceptance")
                .hasValue(movedStart());
        assertThat(ownerCalendar.entries().getFirst().start())
                .as("owner calendar start after the acceptance")
                .isEqualTo(movedStart().localDateTime());
        assertThat(publicCalendar.entries().getFirst().start())
                .as("public calendar start after the acceptance")
                .isEqualTo(movedStart().localDateTime());
        assertThat(itinerary.entriesForDate(MOVED_FIRST_DAY))
                .as("itinerary still on the moved first day after the acceptance")
                .hasSize(1);
    }

    @Test
    void aMoveDoesNotBringADeclinedConferenceBackOntoTheCalendars() {
        List<Event> events = List.of(planned(),
                new ConferenceAttendanceDeclined(conferenceId, "Too far", DECIDED_ON), moved());
        ConferenceCalendarProjector ownerCalendar = new ConferenceCalendarProjector();
        PublicCalendarProjector publicCalendar = new PublicCalendarProjector();
        ItineraryProjector itinerary = new ItineraryProjector();
        ownerCalendar.handle(play(events));
        publicCalendar.handle(play(events));
        itinerary.handle(play(events));

        assertThat(ownerCalendar.entries())
                .as("owner calendar")
                .isEmpty();
        assertThat(publicCalendar.entries())
                .as("public calendar")
                .isEmpty();
        assertThat(itinerary.entriesForDate(MOVED_FIRST_DAY))
                .as("itinerary")
                .isEmpty();
    }

    @Test
    void aMoveDoesNotBringACancelledConferenceBack() {
        List<Event> events = List.of(planned(),
                new ConferenceCancelled(conferenceId, "Organizers called it off"), moved());
        ConferenceProjector dashboard = new ConferenceProjector();
        ScheduleGapProjector schedule = new ScheduleGapProjector(new StaticAirportCityResolver());
        dashboard.handle(play(events));
        schedule.handle(play(events));

        assertThat(dashboard.findById(conferenceId))
                .as("dashboard")
                .isEmpty();
        assertThat(schedule.problems())
                .as("schedule")
                .isEmpty();
    }

    @Test
    void theLastMoveWins() {
        // A third date, neither the planned nor the first move: moving back to the planned days
        // would pass with the arm deleted, which is how this case first shipped.
        LocalDate movedAgain = LocalDate.of(2027, 3, 22);
        ConferenceDatesChanged secondMove = new ConferenceDatesChanged(conferenceId,
                zt(movedAgain.atTime(9, 0)), zt(movedAgain.plusDays(2).atTime(17, 0)));
        ConferenceCalendarProjector projector = new ConferenceCalendarProjector();
        projector.handle(play(planned(), moved(), secondMove));

        assertThat(projector.entries().getFirst().start())
                .as("the most recent move is the one in force")
                .isEqualTo(movedAgain.atTime(9, 0));
    }

    private ConferencePlanned planned() {
        return new ConferencePlanned(conferenceId, "DevNexus",
                zt(PLANNED_FIRST_DAY.atTime(9, 0)), zt(PLANNED_FIRST_DAY.plusDays(2).atTime(17, 0)),
                "Georgia World Congress Center", ATLANTA, ConferenceFormat.CALL_FOR_PAPERS);
    }

    private ConferenceDatesChanged moved() {
        return new ConferenceDatesChanged(conferenceId, movedStart(), movedEnd());
    }

    private static ZonedTimestamp movedStart() {
        return zt(MOVED_FIRST_DAY.atTime(9, 0));
    }

    private static ZonedTimestamp movedEnd() {
        return zt(MOVED_LAST_DAY.atTime(17, 0));
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, ATLANTA_ZONE);
    }

    private Stream<StoredEvent> play(Event... events) {
        return play(List.of(events));
    }

    private Stream<StoredEvent> play(List<Event> events) {
        return events.stream().map(this::stored);
    }

    private StoredEvent stored(Event event) {
        return new StoredEvent(sequence.incrementAndGet(), event.getClass(), UUID.randomUUID(),
                DECIDED_ON, event, UUID.randomUUID());
    }
}

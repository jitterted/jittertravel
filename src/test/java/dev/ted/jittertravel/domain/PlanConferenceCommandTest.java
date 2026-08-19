package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The venue zone is what every rule is read in: "at least a day out" asks whether the conference
 * starts on a later calendar day <em>where it happens</em>, so the answer never depends on where
 * the server runs. The test JVM is pinned to UTC (pom.xml), so the fixtures deliberately sit in a
 * far-away zone — a rule accidentally evaluated in the server zone fails here.
 */
class PlanConferenceCommandTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("America/Los_Angeles");
    private static final Address VENUE = new Address("747 Howard St", "San Francisco", "CA",
                                                     "94103", "USA", null);

    @Test
    void startLaterOnTheSameVenueDayIsRejected() {
        Instant now = instantAt(LocalDateTime.of(2026, 5, 16, 10, 0));

        assertThatExceptionOfType(DateRangeNotInFuture.class)
                .isThrownBy(() -> conference(LocalDateTime.of(2026, 5, 16, 18, 0),
                                             LocalDateTime.of(2026, 5, 18, 17, 0))
                        .execute(new PlanConferenceContext(now)).toList());
    }

    @Test
    void startOnTheNextVenueDayIsAcceptedEvenIfLessThanTwentyFourHoursAway() {
        Instant now = instantAt(LocalDateTime.of(2026, 5, 16, 10, 0));

        var events = conference(LocalDateTime.of(2026, 5, 17, 9, 0),
                                LocalDateTime.of(2026, 5, 18, 17, 0))
                .execute(new PlanConferenceContext(now)).toList();

        assertThat(events)
                .as("the rule is 'a later day at the venue', not 'a full 24 hours out'")
                .hasSize(1);
    }

    @Test
    void endBeforeStartFails() {
        Instant now = instantAt(LocalDateTime.of(2026, 5, 16, 10, 0));

        assertThatExceptionOfType(InvalidDateRange.class)
                .isThrownBy(() -> conference(LocalDateTime.of(2026, 5, 20, 9, 0),
                                             LocalDateTime.of(2026, 5, 20, 8, 0))
                        .execute(new PlanConferenceContext(now)).toList());
    }

    @Test
    void endOnTheSameDayAsStartIsAllowed() {
        Instant now = instantAt(LocalDateTime.of(2026, 5, 16, 10, 0));

        var events = conference(LocalDateTime.of(2026, 5, 20, 9, 0),
                                LocalDateTime.of(2026, 5, 20, 17, 0))
                .execute(new PlanConferenceContext(now)).toList();

        assertThat(events)
                .as("a one-day conference is legitimate — it is what the gathering migration reads")
                .hasSize(1);
    }

    @Test
    void successCarriesEveryFieldOntoTheEvent() {
        ConferenceId conferenceId = ConferenceId.random();
        ZonedTimestamp start = zt(LocalDateTime.of(2026, 5, 20, 9, 0));
        ZonedTimestamp end = zt(LocalDateTime.of(2026, 5, 22, 17, 0));
        // A non-default format (OPEN_SPACE) proves it rides through rather than being defaulted.
        PlanConferenceCommand command = new PlanConferenceCommand(
                conferenceId, "Successful Conference", start, end, "Moscone Center", VENUE,
                ConferenceFormat.OPEN_SPACE);

        ConferenceTentativelyPlanned event = command
                .execute(new PlanConferenceContext(instantAt(LocalDateTime.of(2026, 5, 16, 10, 0))))
                .toList()
                .getFirst();

        assertThat(event.conferenceId())
                .isEqualTo(conferenceId);
        assertThat(event.name())
                .isEqualTo("Successful Conference");
        assertThat(event.startDate())
                .isEqualTo(start);
        assertThat(event.endDate())
                .isEqualTo(end);
        assertThat(event.venueName())
                .isEqualTo("Moscone Center");
        assertThat(event.venueAddress().street())
                .isEqualTo("747 Howard St");
        assertThat(event.format())
                .as("the chosen conference format rides onto the event")
                .isEqualTo(ConferenceFormat.OPEN_SPACE);
    }

    @Test
    void theVenueWallClockBecomesTheInstantThatMomentActuallyIs() {
        ZonedTimestamp start = zt(LocalDateTime.of(2026, 5, 20, 9, 0));

        assertThat(start.utc())
                .as("09:00 Pacific in May (PDT, UTC-7) is 16:00Z")
                .isEqualTo(Instant.parse("2026-05-20T16:00:00Z"));
    }

    private static PlanConferenceCommand conference(LocalDateTime start, LocalDateTime end) {
        return new PlanConferenceCommand(
                ConferenceId.random(), "JitterConf", zt(start), zt(end), "Moscone Center", VENUE,
                ConferenceFormat.CALL_FOR_PAPERS);
    }

    private static ZonedTimestamp zt(LocalDateTime local) {
        return ZonedTimestamp.fromLocal(local, VENUE_ZONE);
    }

    private static Instant instantAt(LocalDateTime venueLocal) {
        return venueLocal.atZone(VENUE_ZONE).toInstant();
    }
}

package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceAttendanceDeclined;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.TalkRejected;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The liveness rule every conference command decides from. Each command's own test covers it
 * through the command; this pins the rule once, including the two conferences it must keep apart.
 */
class LiveConferencePlanTest {

    private static final ZoneId VENUE_ZONE = ZoneId.of("Africa/Casablanca");
    private static final Instant SOME_INSTANT = Instant.parse("2026-08-16T18:30:00Z");

    private final ConferenceId conferenceId = ConferenceId.random();
    private final ConferenceId otherConferenceId = ConferenceId.random();

    @Test
    void aPlannedConferenceIsLiveAndCarriesItsPlan() {
        ConferencePlanned plan = planned(conferenceId);

        assertThat(new LiveConferencePlan(conferenceId).in(events(plan)))
                .as("the plan of a conference that was planned and is not gone")
                .hasValue(plan);
    }

    @Test
    void aConferenceNeverPlannedIsNotLive() {
        assertThat(new LiveConferencePlan(conferenceId).in(events(planned(otherConferenceId))))
                .as("another conference's plan says nothing about this one")
                .isEmpty();
    }

    @Test
    void aCancelledConferenceIsNotLive() {
        assertThat(new LiveConferencePlan(conferenceId).in(events(
                planned(conferenceId),
                new ConferenceCancelled(conferenceId, "Organizers pulled it"))))
                .isEmpty();
    }

    @Test
    void aDeclinedConferenceIsNotLive() {
        assertThat(new LiveConferencePlan(conferenceId).in(events(
                planned(conferenceId),
                new ConferenceAttendanceDeclined(conferenceId, "Schedule clash", SOME_INSTANT))))
                .isEmpty();
    }

    @Test
    void anotherConferenceGoingAwayLeavesThisOneLive() {
        ConferencePlanned plan = planned(conferenceId);

        assertThat(new LiveConferencePlan(conferenceId).in(events(
                plan,
                planned(otherConferenceId),
                new ConferenceCancelled(otherConferenceId, "Organizers pulled it"),
                new ConferenceAttendanceDeclined(otherConferenceId, "Schedule clash", SOME_INSTANT))))
                .hasValue(plan);
    }

    @Test
    void aRejectionLeavesTheConferenceLive() {
        ConferencePlanned plan = planned(conferenceId);

        assertThat(new LiveConferencePlan(conferenceId).in(events(plan, new TalkRejected(conferenceId, SOME_INSTANT))))
                .as("a rejection drops a conference from the calendars, not from the commands")
                .hasValue(plan);
    }

    private static ConferencePlanned planned(ConferenceId conferenceId) {
        Address venue = new Address("Avenue de France", "Marrakesh", "", "40000", "Morocco", "Marrakesh");
        return new ConferencePlanned(
                conferenceId, "Devoxx Morocco",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 7, 9, 0), VENUE_ZONE),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 10, 9, 17, 0), VENUE_ZONE),
                "Palais des Congrès", venue);
    }

    private static Stream<StoredEvent> events(Event... payloads) {
        return Stream.of(payloads)
                     .map(payload -> new StoredEvent(0, payload.getClass(), UUID.randomUUID(),
                                                     SOME_INSTANT, payload, UUID.randomUUID()));
    }
}

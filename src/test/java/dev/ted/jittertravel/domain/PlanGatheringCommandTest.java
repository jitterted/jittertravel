package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanGatheringCommandTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    // 13:00 on 1 June in London — "today" for every case below that uses LONDON.
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate NEXT_WEEK = TODAY.plusWeeks(1);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    private static final Address LOCATION = new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null);

    @Test
    void validCommandProducesGatheringPlannedEventWithAllFields() {
        PlanGatheringCommand command = validCommand();

        List<GatheringPlanned> events = command.execute(new GatheringPlanningContext(NOW)).toList();

        assertThat(events)
                .hasSize(1);
        GatheringPlanned event = events.getFirst();
        assertThat(event.gatheringId())
                .isEqualTo(command.gatheringId());
        assertThat(event.title())
                .isEqualTo("LJC November Meetup");
        assertThat(event.venueName())
                .isEqualTo("Skills Matter");
        assertThat(event.location())
                .isEqualTo(LOCATION);
        assertThat(event.startsAt())
                .isEqualTo(londonTime(NEXT_WEEK, START));
        assertThat(event.endsAt())
                .isEqualTo(londonTime(NEXT_WEEK, END));
        assertThat(event.speaking())
                .as("speaking flag should match command")
                .isTrue();
        assertThat(event.infoUrl())
                .isEqualTo("https://example.com/event");
    }

    @Test
    void gatheringLaterTodayThrowsGatheringDateNotInFuture() {
        // The rule is about the date, not the moment: 18:00 is still ahead of NOW (13:00), but a
        // gathering must be planned for a later day.
        PlanGatheringCommand command = commandFor(londonTime(TODAY, START), londonTime(TODAY, END));

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(NOW)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void gatheringDateInPastThrowsGatheringDateNotInFuture() {
        PlanGatheringCommand command = commandFor(
                londonTime(TODAY.minusDays(1), START), londonTime(TODAY.minusDays(1), END));

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(NOW)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void futureDateIsJudgedAtTheVenueNotInUtc() {
        // 20:00 UTC on 1 June is already 08:00 on 2 June in Auckland, so an Auckland gathering that
        // evening is *today* there and must be rejected — even though its date is "tomorrow" by UTC.
        ZoneId auckland = ZoneId.of("Pacific/Auckland");
        Instant now = Instant.parse("2026-06-01T20:00:00Z");
        ZonedTimestamp startsAt = ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 2, 19, 0), auckland);
        ZonedTimestamp endsAt = ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 2, 21, 0), auckland);

        PlanGatheringCommand command = commandFor(startsAt, endsAt);

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(now)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void endTimeBeforeStartTimeThrowsInvalidGatheringTimeRange() {
        PlanGatheringCommand command = commandFor(londonTime(NEXT_WEEK, END), londonTime(NEXT_WEEK, START));

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(NOW)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    @Test
    void endTimeEqualToStartTimeThrowsInvalidGatheringTimeRange() {
        PlanGatheringCommand command = commandFor(londonTime(NEXT_WEEK, START), londonTime(NEXT_WEEK, START));

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(NOW)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    private static ZonedTimestamp londonTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), LONDON);
    }

    private static PlanGatheringCommand commandFor(ZonedTimestamp startsAt, ZonedTimestamp endsAt) {
        return new PlanGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, startsAt, endsAt, false, "");
    }

    private static PlanGatheringCommand validCommand() {
        return new PlanGatheringCommand(
                GatheringId.random(), "LJC November Meetup", "Skills Matter", LOCATION,
                londonTime(NEXT_WEEK, START), londonTime(NEXT_WEEK, END), true,
                "https://example.com/event");
    }
}

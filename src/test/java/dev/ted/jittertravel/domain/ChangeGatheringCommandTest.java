package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeGatheringCommandTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    // 13:00 on 1 June in London — "today" for every case below.
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate NEXT_WEEK = TODAY.plusWeeks(1);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    private static final Address LOCATION = new Address("2 New St", "Manchester", "", "M1 1AA", "GB", null);

    @Test
    void validChangeProducesGatheringChangedEventWithAllFields() {
        ChangeGatheringCommand command = validCommand();

        List<GatheringChanged> events = command.execute(new ChangeGatheringContext(true, NOW)).toList();

        assertThat(events)
                .hasSize(1);
        GatheringChanged event = events.getFirst();
        assertThat(event.gatheringId())
                .isEqualTo(command.gatheringId());
        assertThat(event.title())
                .isEqualTo("LJC November Meetup");
        assertThat(event.venueName())
                .isEqualTo("Federation House");
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
    void changeRejectedWhenGatheringDoesNotExist() {
        ChangeGatheringCommand command = validCommand();

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(false, NOW)))
                .isInstanceOf(GatheringNotFound.class);
    }

    @Test
    void newDateLaterTodayThrowsGatheringDateNotInFuture() {
        ChangeGatheringCommand command = commandFor(londonTime(TODAY, START), londonTime(TODAY, END));

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, NOW)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void newDateInPastThrowsGatheringDateNotInFuture() {
        ChangeGatheringCommand command = commandFor(
                londonTime(TODAY.minusDays(1), START), londonTime(TODAY.minusDays(1), END));

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, NOW)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void endTimeBeforeStartTimeThrowsInvalidGatheringTimeRange() {
        ChangeGatheringCommand command = commandFor(londonTime(NEXT_WEEK, END), londonTime(NEXT_WEEK, START));

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, NOW)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    @Test
    void endTimeEqualToStartTimeThrowsInvalidGatheringTimeRange() {
        ChangeGatheringCommand command = commandFor(londonTime(NEXT_WEEK, START), londonTime(NEXT_WEEK, START));

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, NOW)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    @Test
    void existenceIsCheckedBeforeDateValidation() {
        // A non-existent gathering with an otherwise-invalid date still fails as GatheringNotFound first.
        ChangeGatheringCommand command = commandFor(
                londonTime(TODAY.minusDays(1), START), londonTime(TODAY.minusDays(1), END));

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(false, NOW)))
                .isInstanceOf(GatheringNotFound.class);
    }

    @Test
    void gatheringWhoseOriginalDateHasPassedMayStillBeMovedToAFutureDate() {
        // There is no gate on the original date — only the new date must be in the future.
        ChangeGatheringCommand command = commandFor(londonTime(NEXT_WEEK, START), londonTime(NEXT_WEEK, END));

        List<GatheringChanged> events = command.execute(new ChangeGatheringContext(true, NOW)).toList();

        assertThat(events)
                .hasSize(1);
    }

    private static ZonedTimestamp londonTime(LocalDate date, LocalTime time) {
        return ZonedTimestamp.fromLocal(date.atTime(time), LONDON);
    }

    private static ChangeGatheringCommand commandFor(ZonedTimestamp startsAt, ZonedTimestamp endsAt) {
        return new ChangeGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, startsAt, endsAt, false, "");
    }

    private static ChangeGatheringCommand validCommand() {
        return new ChangeGatheringCommand(
                GatheringId.random(), "LJC November Meetup", "Federation House", LOCATION,
                londonTime(NEXT_WEEK, START), londonTime(NEXT_WEEK, END), true,
                "https://example.com/event");
    }
}

package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangeGatheringCommandTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate NEXT_WEEK = TODAY.plusWeeks(1);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    private static final Address LOCATION = new Address("2 New St", "Manchester", "", "M1 1AA", "GB", null);

    @Test
    void validChangeProducesGatheringChangedEventWithAllFields() {
        ChangeGatheringCommand command = validCommand();

        List<GatheringChanged> events = command.execute(new ChangeGatheringContext(true, TODAY)).toList();

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
        assertThat(event.date())
                .isEqualTo(NEXT_WEEK);
        assertThat(event.startTime())
                .isEqualTo(START);
        assertThat(event.endTime())
                .isEqualTo(END);
        assertThat(event.speaking())
                .as("speaking flag should match command")
                .isTrue();
        assertThat(event.infoUrl())
                .isEqualTo("https://example.com/event");
    }

    @Test
    void changeRejectedWhenGatheringDoesNotExist() {
        ChangeGatheringCommand command = validCommand();

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(false, TODAY)))
                .isInstanceOf(GatheringNotFound.class);
    }

    @Test
    void newDateTodayThrowsGatheringDateNotInFuture() {
        ChangeGatheringCommand command = new ChangeGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, TODAY, START, END, false, "");

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, TODAY)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void newDateInPastThrowsGatheringDateNotInFuture() {
        ChangeGatheringCommand command = new ChangeGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, TODAY.minusDays(1), START, END, false, "");

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, TODAY)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void endTimeBeforeStartTimeThrowsInvalidGatheringTimeRange() {
        ChangeGatheringCommand command = new ChangeGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, NEXT_WEEK, END, START, false, "");

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, TODAY)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    @Test
    void endTimeEqualToStartTimeThrowsInvalidGatheringTimeRange() {
        ChangeGatheringCommand command = new ChangeGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, NEXT_WEEK, START, START, false, "");

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(true, TODAY)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    @Test
    void existenceIsCheckedBeforeDateValidation() {
        // A non-existent gathering with an otherwise-invalid date still fails as GatheringNotFound first.
        ChangeGatheringCommand command = new ChangeGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, TODAY.minusDays(1), START, END, false, "");

        assertThatThrownBy(() -> command.execute(new ChangeGatheringContext(false, TODAY)))
                .isInstanceOf(GatheringNotFound.class);
    }

    @Test
    void gatheringWhoseOriginalDateHasPassedMayStillBeMovedToAFutureDate() {
        // There is no gate on the original date — only the new date must be in the future.
        ChangeGatheringCommand command = new ChangeGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, NEXT_WEEK, START, END, false, "");

        List<GatheringChanged> events = command.execute(new ChangeGatheringContext(true, TODAY)).toList();

        assertThat(events)
                .hasSize(1);
    }

    private static ChangeGatheringCommand validCommand() {
        return new ChangeGatheringCommand(
                GatheringId.random(), "LJC November Meetup", "Federation House", LOCATION,
                NEXT_WEEK, START, END, true, "https://example.com/event");
    }
}

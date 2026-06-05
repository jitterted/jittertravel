package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanGatheringCommandTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 1);
    private static final LocalDate NEXT_WEEK = TODAY.plusWeeks(1);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final LocalTime END = LocalTime.of(21, 0);
    private static final Address LOCATION = new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null);

    @Test
    void validCommandProducesGatheringPlannedEventWithAllFields() {
        PlanGatheringCommand command = validCommand();

        List<GatheringPlanned> events = command.execute(new GatheringPlanningContext(TODAY)).toList();

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
    void gatheringDateTodayThrowsGatheringDateNotInFuture() {
        PlanGatheringCommand command = new PlanGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, TODAY, START, END, false, "");

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(TODAY)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void gatheringDateInPastThrowsGatheringDateNotInFuture() {
        PlanGatheringCommand command = new PlanGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, TODAY.minusDays(1), START, END, false, "");

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(TODAY)))
                .isInstanceOf(GatheringDateNotInFuture.class);
    }

    @Test
    void endTimeBeforeStartTimeThrowsInvalidGatheringTimeRange() {
        PlanGatheringCommand command = new PlanGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, NEXT_WEEK, END, START, false, "");

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(TODAY)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    @Test
    void endTimeEqualToStartTimeThrowsInvalidGatheringTimeRange() {
        PlanGatheringCommand command = new PlanGatheringCommand(
                GatheringId.random(), "LJC", "Venue", LOCATION, NEXT_WEEK, START, START, false, "");

        assertThatThrownBy(() -> command.execute(new GatheringPlanningContext(TODAY)))
                .isInstanceOf(InvalidGatheringTimeRange.class);
    }

    private static PlanGatheringCommand validCommand() {
        return new PlanGatheringCommand(
                GatheringId.random(), "LJC November Meetup", "Skills Matter", LOCATION,
                NEXT_WEEK, START, END, true, "https://example.com/event");
    }
}

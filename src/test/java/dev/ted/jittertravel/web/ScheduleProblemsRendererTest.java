package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleProblemsRendererTest {

    @Test
    void noProblemsRendersCleanMessage() {
        String html = ScheduleProblemsRenderer.render(List.of());

        assertThat(html).contains("No problems found");
    }

    @Test
    void missingTravelShowsCitiesAndTimes() {
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "London",
                LocalDateTime.of(2026, 7, 1, 14, 30),
                "Berlin",
                LocalDateTime.of(2026, 7, 3, 9, 0)
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("London → Berlin")
                .contains("Arrive Jul 1, 2:30 PM")
                .contains("next leg departs Jul 3, 9:00 AM");
    }

    @Test
    void missingHotelShowsCityAndDates() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "Berlin",
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 5),
                "JavaOne"
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("Berlin — for JavaOne")
                .contains("checking in on Wed, Jul 1")
                .contains("check out on Sun, Jul 5");
    }

    @Test
    void missingHotelWithNoConferenceNameOmitsConferencePart() {
        ScheduleProblem problem = new ScheduleProblem.MissingHotel(
                "Paris",
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 14),
                ""
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("Paris")
                .doesNotContain("— for");
    }

    @Test
    void schedulingConflictShowsGatheringNamesAndTimes() {
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                "Mob Session",
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                "Team Lunch",
                LocalTime.of(11, 30),
                LocalTime.of(13, 0),
                LocalDate.of(2026, 7, 15)
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("Mob Session conflicts with Team Lunch")
                .contains("Wed, Jul 15")
                .contains("10:00 AM")
                .contains("12:00 PM")
                .contains("overlaps")
                .contains("11:30 AM")
                .contains("1:00 PM");
    }

    @Test
    void differentCityConflictShowsNamesAndClearLink() {
        GatheringId gatheringId = GatheringId.random();
        ConferenceId conferenceId = ConferenceId.random();
        ScheduleProblem problem = new ScheduleProblem.DifferentCityConflict(
                "BRU JUG", "Brussels",
                "JavaOne", "Amsterdam",
                LocalDate.of(2026, 9, 16),
                gatheringId, conferenceId
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("BRU JUG")
                .contains("Brussels")
                .contains("JavaOne")
                .contains("Amsterdam")
                .contains("Sep 16")
                .contains("/clear-conflict")
                .contains(gatheringId.id().toString())
                .contains(conferenceId.id().toString());
    }

    @Test
    void emptyTravelColumnShowsNone() {
        ScheduleProblem hotelOnly = new ScheduleProblem.MissingHotel(
                "Paris", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), "");

        String html = ScheduleProblemsRenderer.render(List.of(hotelOnly));

        assertThat(html).contains("None");
    }
}

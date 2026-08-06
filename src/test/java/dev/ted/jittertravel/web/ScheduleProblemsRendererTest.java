package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.ScheduleProblem;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.GatheringId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 14, 30), ZoneId.of("Europe/London")),
                "Berlin",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Europe/Berlin"))
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("London → Berlin")
                .contains("Arrive <time")
                .contains(">Jul 1, 2:30 PM</time> — next leg departs <time")
                .contains(">Jul 3, 9:00 AM</time>");
    }

    @Test
    void missingTravelTimesRenderAsTimeElementsCarryingTheUtcInstant() {
        // Each end is in its own zone: the text is that end's wall-clock, the datetime attribute
        // its instant (London BST 14:30 -> 13:30Z, Berlin CEST 09:00 -> 07:00Z).
        ScheduleProblem problem = new ScheduleProblem.MissingTravel(
                "London",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 1, 14, 30), ZoneId.of("Europe/London")),
                "Berlin",
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 3, 9, 0), ZoneId.of("Europe/Berlin"))
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("<time datetime=\"2026-07-01T13:30:00Z\" data-fmt=\"MMM d, h:mm a\">"
                          + "Jul 1, 2:30 PM</time>")
                .contains("<time datetime=\"2026-07-03T07:00:00Z\" data-fmt=\"MMM d, h:mm a\">"
                          + "Jul 3, 9:00 AM</time>");
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
                gathering("Mob Session", "London", LocalDateTime.of(2026, 7, 15, 10, 0),
                          LocalDateTime.of(2026, 7, 15, 12, 0), "Europe/London"),
                gathering("Team Lunch", "London", LocalDateTime.of(2026, 7, 15, 11, 30),
                          LocalDateTime.of(2026, 7, 15, 13, 0), "Europe/London")
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
    void crossZoneSchedulingConflictShowsEachGatheringsOwnDateAndCity() {
        // A San Francisco evening overlaps a Tokyo morning that falls on the *next* local day.
        // Reporting one shared date would put B's times under A's date — times that never
        // happened on that day at either venue.
        ScheduleProblem problem = new ScheduleProblem.SchedulingConflict(
                gathering("SF Java", "San Francisco", LocalDateTime.of(2026, 10, 3, 18, 0),
                          LocalDateTime.of(2026, 10, 3, 21, 0), "America/Los_Angeles"),
                gathering("Tokyo JUG", "Tokyo", LocalDateTime.of(2026, 10, 4, 9, 0),
                          LocalDateTime.of(2026, 10, 4, 12, 0), "Asia/Tokyo")
        );

        String html = ScheduleProblemsRenderer.render(List.of(problem));

        assertThat(html)
                .contains("SF Java conflicts with Tokyo JUG")
                .contains("<time datetime=\"2026-10-04T01:00:00Z\" data-fmt=\"EEE, MMM d, h:mm a\">"
                          + "Sat, Oct 3, 6:00 PM</time>")
                .contains("<time datetime=\"2026-10-04T04:00:00Z\" data-fmt=\"h:mm a\">9:00 PM</time>")
                .contains("(San Francisco)")
                .contains("<time datetime=\"2026-10-04T00:00:00Z\" data-fmt=\"EEE, MMM d, h:mm a\">"
                          + "Sun, Oct 4, 9:00 AM</time>")
                .contains("<time datetime=\"2026-10-04T03:00:00Z\" data-fmt=\"h:mm a\">12:00 PM</time>")
                .contains("(Tokyo)");
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

    private static ScheduleProblem.ConflictingGathering gathering(
            String name, String city, LocalDateTime start, LocalDateTime end, String zone) {
        ZoneId zoneId = ZoneId.of(zone);
        return new ScheduleProblem.ConflictingGathering(
                name, city,
                ZonedTimestamp.fromLocal(start, zoneId),
                ZonedTimestamp.fromLocal(end, zoneId));
    }
}

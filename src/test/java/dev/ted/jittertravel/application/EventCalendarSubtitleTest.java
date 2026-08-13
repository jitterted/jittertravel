package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventCalendarSubtitleTest {

    private static final ZoneId UK = ZoneId.of("Europe/London");
    private static final ZonedTimestamp START = at(18, 0);
    private static final ZonedTimestamp END = at(21, 0);

    private final EventCalendarSubtitle subtitle = new EventCalendarSubtitle();

    @Test
    void venueThenCityCountryThenTimeRange() {
        Address location = new Address("1 Example St", "London", "", "EC1A 1BB", "GB", null);

        assertThat(subtitle.venueLocationAndTime("Skills Matter", location, START, END))
                .containsExactly(
                        new SubtitleLine.Text("Skills Matter"),
                        new SubtitleLine.Text("London, GB"),
                        new SubtitleLine.Range(START, END));
    }

    @Test
    void blankVenueOmitsTheVenueLine() {
        Address location = new Address("", "London", "", "", "GB", null);

        assertThat(subtitle.venueLocationAndTime("", location, START, END))
                .containsExactly(
                        new SubtitleLine.Text("London, GB"),
                        new SubtitleLine.Range(START, END));
    }

    @Test
    void blankCountryShowsOnlyTheCity() {
        Address location = new Address("", "London", "", "", "", null);

        assertThat(subtitle.venueLocationAndTime("", location, START, END))
                .containsExactly(
                        new SubtitleLine.Text("London"),
                        new SubtitleLine.Range(START, END));
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 7, 10, hour, minute), UK);
    }
}

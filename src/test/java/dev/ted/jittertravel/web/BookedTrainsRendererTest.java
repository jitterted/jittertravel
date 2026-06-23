package dev.ted.jittertravel.web;

import dev.ted.jittertravel.application.BookedTrainView;
import dev.ted.jittertravel.application.TimeView;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookedTrainsRendererTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/London");
    private static final ZonedTimestamp DEPARTURE = zt(LocalDateTime.of(2026, 6, 9, 9, 0));
    private static final ZonedTimestamp ARRIVAL = zt(LocalDateTime.of(2026, 6, 9, 13, 0));

    @Test
    void emptyAllListRendersBookedYetMessage() {
        String html = BookedTrainsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("No train trips booked yet.");
    }

    @Test
    void emptyFutureListRendersNoUpcomingMessage() {
        String html = BookedTrainsRenderer.render(List.of(), TimeView.FUTURE);

        assertThat(html)
                .contains("No upcoming trains.");
    }

    @Test
    void activeFilterMarkedOnToggleLink() {
        String html = BookedTrainsRenderer.render(List.of(), TimeView.ALL);

        assertThat(html)
                .contains("<a href=\"/booked-trains?filter=all\" class=\"active\">All</a>")
                .contains("<a href=\"/booked-trains?filter=future\">Upcoming</a>");
    }

    @Test
    void trainWithMapsUrlRendersAsLink() {
        BookedTrainView train = trainView("London Euston", "London",
                "https://maps.google.com/euston",
                "Manchester Piccadilly", "Manchester", "");

        String html = BookedTrainsRenderer.render(List.of(train), TimeView.FUTURE);

        assertThat(html)
                .contains("<a href=\"https://maps.google.com/euston\"")
                .contains("London Euston");
    }

    @Test
    void stationWithNoMapsUrlRendersAsPlainText() {
        BookedTrainView train = trainView("London Euston", "London", "",
                "Manchester Piccadilly", "Manchester", "");

        String html = BookedTrainsRenderer.render(List.of(train), TimeView.FUTURE);

        assertThat(html)
                .contains("<span class=\"station-name\">London Euston</span>")
                .doesNotContain("<a href=\"\"");
    }

    @Test
    void serviceIdAppearsWhenPresent() {
        BookedTrainView train = new BookedTrainView(
                TrainTripId.random(),
                "LNER - Azuma 1A34",
                "London Euston", "London", "",
                DEPARTURE,
                "Manchester Piccadilly", "Manchester", "",
                ARRIVAL
        );

        String html = BookedTrainsRenderer.render(List.of(train), TimeView.FUTURE);

        assertThat(html).contains("LNER - Azuma 1A34");
    }

    @Test
    void departureDateTimeDisplayFormattedCorrectly() {
        BookedTrainView train = new BookedTrainView(
                TrainTripId.random(),
                "",
                "London Euston", "London", "",
                DEPARTURE,
                "Manchester Piccadilly", "Manchester", "",
                ARRIVAL
        );

        String html = BookedTrainsRenderer.render(List.of(train), TimeView.FUTURE);

        assertThat(html)
                .contains("Tue, Jun 9, 9:00 AM")
                .contains("Tue, Jun 9, 1:00 PM");
    }

    @Test
    void eachTrainRowLinksToItsEditPage() {
        TrainTripId tripId = TrainTripId.random();
        BookedTrainView train = new BookedTrainView(
                tripId,
                "",
                "London Euston", "London", "",
                DEPARTURE,
                "Manchester Piccadilly", "Manchester", "",
                ARRIVAL
        );

        String html = BookedTrainsRenderer.render(List.of(train), TimeView.FUTURE);

        assertThat(html)
                .contains("<a class=\"train-edit-link\" href=\"/booked-trains/" + tripId.id() + "\">Edit</a>");
    }

    private static BookedTrainView trainView(
            String depName, String depCity, String depMapsUrl,
            String arrName, String arrCity, String arrMapsUrl) {
        return new BookedTrainView(
                TrainTripId.random(),
                "",
                depName, depCity, depMapsUrl,
                DEPARTURE,
                arrName, arrCity, arrMapsUrl,
                ARRIVAL
        );
    }

    private static ZonedTimestamp zt(LocalDateTime wallClock) {
        return ZonedTimestamp.fromLocal(wallClock, ZONE);
    }
}

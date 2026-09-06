package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.OverlappingLegRefused;
import dev.ted.jittertravel.domain.ScheduledLeg;
import dev.ted.jittertravel.domain.ScheduledLegId;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class OverlappingLegNoticeTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void namesTheKindAndTheDepartureOfTheBlockingTrain() {
        TrainTripId tripId = TrainTripId.random();

        OverlappingLegNotice notice = notice(new ScheduledLegId.Train(tripId));

        assertThat(notice.message())
                .isEqualTo("Overlaps a train departing Jun 1, 9:00 AM");
        assertThat(notice.path())
                .isEqualTo("/booked-trains/" + tripId.id());
        assertThat(notice.label())
                .isEqualTo("Open that train");
    }

    @Test
    void namesAFlightAsAFlight() {
        FlightId flightId = FlightId.random();

        OverlappingLegNotice notice = notice(new ScheduledLegId.Flight(flightId));

        assertThat(notice.message())
                .isEqualTo("Overlaps a flight departing Jun 1, 9:00 AM");
        assertThat(notice.path())
                .isEqualTo("/booked-flights/" + flightId.id());
    }

    @Test
    void theTimeIsReadInTheBlockingLegsOwnZone() {
        // A Tokyo departure named in Berlin's clock would send Ted looking for the wrong trip.
        ScheduledLeg tokyo = new ScheduledLeg(
                new ScheduledLegId.Train(TrainTripId.random()),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 9, 0), ZoneId.of("Asia/Tokyo")),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 11, 0), ZoneId.of("Asia/Tokyo")));

        assertThat(OverlappingLegNotice.from(new OverlappingLegRefused(tokyo)).message())
                .isEqualTo("Overlaps a train departing Jun 1, 9:00 AM");
    }

    @Test
    void theMessageCarriesNoApostrophe() {
        // Thymeleaf escaping turns an apostrophe into &#39;, which every markup assertion would
        // then have to know about (CLAUDE.md, form-message rules).
        assertThat(notice(new ScheduledLegId.Train(TrainTripId.random())).message())
                .doesNotContain("'");
    }

    private static OverlappingLegNotice notice(ScheduledLegId id) {
        return OverlappingLegNotice.from(new OverlappingLegRefused(new ScheduledLeg(id,
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 9, 0), BERLIN),
                ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 6, 1, 11, 0), BERLIN))));
    }
}

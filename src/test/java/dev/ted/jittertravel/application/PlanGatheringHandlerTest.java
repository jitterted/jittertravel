package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.PlanGatheringCommand;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.web.PlanGatheringRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary's zone contract, which is what the whole UTC-storage design rests on: an explicit
 * {@code CommonZone} pick wins, otherwise the venue's location must resolve, otherwise the command
 * is rejected outright (no default zone — a silent guess would be wrong more often than right in a
 * travel app).
 */
class PlanGatheringHandlerTest {

    private final PlanGatheringHandler handler = new PlanGatheringHandler(new LocationZoneResolver());

    @Test
    void venueZoneIsDerivedFromTheLocationWhenNoZoneIsPicked() {
        PlanGatheringCommand command = handler.handle(requestIn("Tokyo", "Japan", null));

        assertThat(command.startsAt().zone())
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(command.endsAt().zone())
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void theFormsDateAndTimesBecomeInstantsInThatZone() {
        PlanGatheringCommand command = handler.handle(requestIn("Tokyo", "Japan", null));

        assertThat(command.startsAt().utc())
                .as("18:00 JST is 09:00Z")
                .isEqualTo(Instant.parse("2026-09-15T09:00:00Z"));
        assertThat(command.endsAt().utc())
                .isEqualTo(Instant.parse("2026-09-15T12:00:00Z"));
    }

    @Test
    void explicitZonePickWinsOverTheLocation() {
        // Springfield/USA is ambiguous — and even for a resolvable location, the pick is what the
        // traveler asked for.
        PlanGatheringCommand command = handler.handle(requestIn("Tokyo", "Japan", "US_CENTRAL"));

        assertThat(command.startsAt().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void unresolvableLocationWithNoPickIsRejected() {
        assertThatThrownBy(() -> handler.handle(requestIn("Springfield", "Freedonia", null)))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void unresolvableLocationIsAcceptedOnceAZoneIsPicked() {
        PlanGatheringCommand command = handler.handle(requestIn("Springfield", "Freedonia", "US_CENTRAL"));

        assertThat(command.startsAt().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    private static PlanGatheringRequest requestIn(String city, String country, String zone) {
        PlanGatheringRequest request = new PlanGatheringRequest();
        request.setGatheringId(UUID.randomUUID().toString());
        request.setTitle("Some Meetup");
        request.setVenueName("Some Venue");
        request.setStreet("1 Example St");
        request.setCity(city);
        request.setRegion("");
        request.setPostalCode("");
        request.setCountry(country);
        request.setLocationForMatching(city);
        request.setZone(zone);
        request.setDate(LocalDate.of(2026, 9, 15));
        request.setStartTime(LocalTime.of(18, 0));
        request.setEndTime(LocalTime.of(21, 0));
        request.setSpeaking(false);
        request.setInfoUrl("");
        return request;
    }
}

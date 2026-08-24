package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.ChangeGatheringCommand;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.web.ChangeGatheringRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Same zone contract as {@link PlanGatheringHandlerTest}, plus the property that matters only when
 * editing: the zone is re-derived from the submitted form, so moving a gathering to another
 * country moves its instants too.
 */
class ChangeGatheringHandlerTest {

    private final ChangeGatheringHandler handler = new ChangeGatheringHandler(new LocationZoneResolver());

    @Test
    void movingTheVenueToAnotherCountryRederivesTheZone() {
        ChangeGatheringCommand command = handler.handle(requestIn("Lisbon", "Portugal", null));

        assertThat(command.startsAt().zone())
                .isEqualTo(ZoneId.of("Europe/Lisbon"));
        assertThat(command.startsAt().localDateTime().toString())
                .as("the submitted wall-clock is kept; only its zone changes")
                .isEqualTo("2026-09-15T18:00");
    }

    @Test
    void explicitZonePickWinsOverTheLocation() {
        ChangeGatheringCommand command = handler.handle(requestIn("Lisbon", "Portugal", "UK"));

        assertThat(command.startsAt().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    void unresolvableLocationWithNoPickIsRejected() {
        assertThatThrownBy(() -> handler.handle(requestIn("Springfield", "Freedonia", null)))
                .isInstanceOf(ZoneResolutionException.class);
    }

    private static ChangeGatheringRequest requestIn(String city, String country, String zone) {
        ChangeGatheringRequest request = new ChangeGatheringRequest();
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

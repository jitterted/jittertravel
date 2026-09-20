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
        ChangeGatheringCommand command = handler.handle(SOME_GATHERING, requestIn("Lisbon", "Portugal", null));

        assertThat(command.startsAt().zone())
                .isEqualTo(ZoneId.of("Europe/Lisbon"));
        assertThat(command.startsAt().localDateTime().toString())
                .as("the submitted wall-clock is kept; only its zone changes")
                .isEqualTo("2026-09-15T18:00");
    }

    @Test
    void explicitZonePickWinsOverTheLocation() {
        ChangeGatheringCommand command = handler.handle(SOME_GATHERING, requestIn("Lisbon", "Portugal", "UK"));

        assertThat(command.startsAt().zone())
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    void unresolvableLocationWithNoPickIsRejected() {
        assertThatThrownBy(() -> handler.handle(SOME_GATHERING, requestIn("Springfield", "Freedonia", null)))
                .isInstanceOf(ZoneResolutionException.class);
    }

    /** The id is the controller's to supply now, from the path; these cases are about the zone. */
    private static final String SOME_GATHERING = UUID.randomUUID().toString();

    private static ChangeGatheringRequest requestIn(String city, String country, String zone) {
        return new ChangeGatheringRequest(
                "Some Meetup", "Some Venue", "1 Example St", city, "", "", country, city, zone,
                LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), LocalTime.of(21, 0),
                false, "");
    }
}

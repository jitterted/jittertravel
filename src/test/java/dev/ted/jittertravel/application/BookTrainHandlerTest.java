package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.web.BookTrainRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary's zone contract for trains, which unlike a hotel has <em>two independent</em>
 * endpoints: a Paris→Frankfurt trip crosses no zone boundary but a Paris→London one does, so each
 * end resolves on its own. Per endpoint: an explicit {@code CommonZone} pick wins, otherwise the
 * station's city/country must resolve, otherwise the command is rejected.
 */
class BookTrainHandlerTest {

    private final BookTrainHandler handler = new BookTrainHandler(new LocationZoneResolver());

    @Test
    void eachEndpointResolvesFromItsOwnStation() {
        BookTrainCommand command = handler.handle(trip("Paris", "France", null,
                                                       "London", "United Kingdom", null));

        assertThat(command.departureDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Paris"));
        assertThat(command.arrivalDateTime().zone())
                .as("the arrival end is in a different zone and must resolve independently")
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    void aCrossZoneTripKeepsEachWallClockInItsOwnZone() {
        BookTrainCommand command = handler.handle(trip("Paris", "France", null,
                                                       "London", "United Kingdom", null));

        assertThat(command.departureDateTime().utc())
                .as("09:00 CEST is 07:00Z")
                .isEqualTo(Instant.parse("2026-09-15T07:00:00Z"));
        assertThat(command.arrivalDateTime().utc())
                .as("11:30 BST is 10:30Z — a 3.5h journey, not 2.5h as the wall-clocks suggest")
                .isEqualTo(Instant.parse("2026-09-15T10:30:00Z"));
    }

    @Test
    void explicitPickWinsPerEndpointWithoutAffectingTheOther() {
        BookTrainCommand command = handler.handle(trip("Paris", "France", null,
                                                       "London", "United Kingdom", "US_CENTRAL"));

        assertThat(command.departureDateTime().zone())
                .as("the departure end keeps its derived zone")
                .isEqualTo(ZoneId.of("Europe/Paris"));
        assertThat(command.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void unresolvableStationWithNoPickIsRejected() {
        assertThatThrownBy(() -> handler.handle(trip("Paris", "France", null,
                                                     "Springfield", "Freedonia", null)))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void unresolvableStationIsAcceptedOnceAZoneIsPicked() {
        BookTrainCommand command = handler.handle(trip("Paris", "France", null,
                                                       "Springfield", "Freedonia", "US_CENTRAL"));

        assertThat(command.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    private static BookTrainRequest trip(String fromCity, String fromCountry, String fromZone,
                                         String toCity, String toCountry, String toZone) {
        BookTrainRequest request = new BookTrainRequest();
        request.setTrainTripId(UUID.randomUUID().toString());
        request.setServiceId("Eurostar 9024");
        request.setDepartureStationName(fromCity + " Station");
        request.setDepartureCityName(fromCity);
        request.setDepartureCountry(fromCountry);
        request.setDepartureMapsUrl("");
        request.setDepartureZone(fromZone);
        request.setDepartureDateTime(LocalDateTime.of(2026, 9, 15, 9, 0));
        request.setArrivalStationName(toCity + " Station");
        request.setArrivalCityName(toCity);
        request.setArrivalCountry(toCountry);
        request.setArrivalMapsUrl("");
        request.setArrivalZone(toZone);
        request.setArrivalDateTime(LocalDateTime.of(2026, 9, 15, 11, 30));
        return request;
    }
}

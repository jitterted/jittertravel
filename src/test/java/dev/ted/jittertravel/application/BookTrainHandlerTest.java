package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookTrainCommand;
import dev.ted.jittertravel.domain.InvalidLocationEntry;
import dev.ted.jittertravel.domain.InvalidTrainEntry;
import dev.ted.jittertravel.domain.LocationField;
import dev.ted.jittertravel.domain.LocationRole;
import dev.ted.jittertravel.domain.LocationZoneResolver;
import dev.ted.jittertravel.domain.UnresolvedStationZone;
import dev.ted.jittertravel.web.BookTrainRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid -> {
                    assertThat(invalid.zones())
                            .hasSize(1);
                    assertThat(invalid.zones().getFirst().role())
                            .isEqualTo(LocationRole.ARRIVAL);
                    assertThat(invalid.zones().getFirst().cause())
                            .as("a country was typed; it is just not one a zone follows from")
                            .isEqualTo(UnresolvedStationZone.Cause.COUNTRY_UNRECOGNISED);
                });
    }

    @Test
    void aBlankCountryIsReportedAsMissingRatherThanUnrecognised() {
        // The two want different fixes: type the country, versus pick a zone because no country
        // will help. Telling them apart is the whole reason the cause travels to the form.
        assertThatThrownBy(() -> handler.handle(trip("Paris", "France", null,
                                                     "Frankfurt", "", null)))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid ->
                        assertThat(invalid.zones().getFirst().cause())
                                .isEqualTo(UnresolvedStationZone.Cause.COUNTRY_MISSING));
    }

    @Test
    void bothUnresolvableEndsAreReportedTogether() {
        // The bug of 2026-09-06: the departure was resolved first, threw, and the arrival was
        // never looked at — so fixing one end produced the same message again.
        assertThatThrownBy(() -> handler.handle(trip("Aschaffenberg", "", null,
                                                     "Frankfurt", "", null)))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid ->
                        assertThat(invalid.zones())
                                .extracting(UnresolvedStationZone::role)
                                .containsExactly(LocationRole.DEPARTURE, LocationRole.ARRIVAL));
    }

    @Test
    void aLocationProblemAtOneEndDoesNotSuppressAZoneProblemAtTheOther() {
        // Ted's second report, 2026-09-06: departure missing its city, arrival missing its
        // country. Checking every location before any zone reported only the departure, so the
        // form still took two submits — the original bug, one layer further in. The location/zone
        // order is per end and never across the trip.
        assertThatThrownBy(() -> handler.handle(trip("", "Germany", null,
                                                     "Frankfurt", "", null)))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid -> {
                    assertThat(invalid.locations())
                            .extracting(InvalidLocationEntry::role, InvalidLocationEntry::field)
                            .containsExactly(tuple(LocationRole.DEPARTURE, LocationField.CITY));
                    assertThat(invalid.zones())
                            .extracting(UnresolvedStationZone::role)
                            .containsExactly(LocationRole.ARRIVAL);
                });
    }

    @Test
    void aStationPastedIntoTheCityIsReportedAsThatRatherThanAsAZoneProblem() {
        // Within one end the order is load-bearing: "Frankfurt (Main) Hbf" resolves no zone
        // either, so asking the zone first buries the rule that can actually name the mistake.
        assertThatThrownBy(() -> handler.handle(trip("Paris", "France", null,
                                                     "Frankfurt (Main) Hbf", "", null)))
                .isInstanceOfSatisfying(InvalidTrainEntry.class, invalid -> {
                    assertThat(invalid.locations().getFirst().field())
                            .isEqualTo(LocationField.CITY);
                    assertThat(invalid.zones())
                            .as("that end said its piece; it does not also get a zone complaint")
                            .isEmpty();
                });
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

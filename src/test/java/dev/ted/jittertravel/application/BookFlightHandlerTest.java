package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportZoneResolver;
import dev.ted.jittertravel.domain.BookFlightCommand;
import dev.ted.jittertravel.domain.ZoneResolutionException;
import dev.ted.jittertravel.web.BookFlightRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary's zone contract for flights. Flights carry only airport codes, so the zone comes
 * from {@link AirportZoneResolver} rather than an address — and the endpoints resolve
 * independently, which for a long-haul flight is the difference between a sane duration and a
 * nonsensical one. An explicit pick wins, and here it may also be a raw IANA zone ID, because the
 * AeroDataBox lookup supplies the airport's zone directly.
 */
class BookFlightHandlerTest {

    private final BookFlightHandler handler = new BookFlightHandler(new AirportZoneResolver());

    @Test
    void eachEndpointResolvesFromItsOwnAirportCode() {
        BookFlightCommand command = handler.handle(flight("SFO", null, "FRA", null));

        assertThat(command.departureDateTime().zone())
                .isEqualTo(ZoneId.of("America/Los_Angeles"));
        assertThat(command.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void aLongHaulFlightsDurationOnlyMakesSenseAsInstants() {
        BookFlightCommand command = handler.handle(flight("SFO", null, "FRA", null));

        assertThat(command.departureDateTime().utc())
                .as("15:55 PDT is 22:55Z")
                .isEqualTo(Instant.parse("2026-09-15T22:55:00Z"));
        assertThat(command.arrivalDateTime().utc())
                .as("11:45 CEST next day is 09:45Z — an 10h50m flight, not the 19h50m the "
                    + "wall-clock numbers imply")
                .isEqualTo(Instant.parse("2026-09-16T09:45:00Z"));
    }

    @Test
    void explicitCommonZonePickWinsOverTheAirportCode() {
        BookFlightCommand command = handler.handle(flight("SFO", "US_CENTRAL", "FRA", null));

        assertThat(command.departureDateTime().zone())
                .isEqualTo(ZoneId.of("America/Chicago"));
        assertThat(command.arrivalDateTime().zone())
                .as("the other endpoint is unaffected")
                .isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void aRawIanaZoneIdIsAcceptedBecauseTheFlightApiSuppliesOne() {
        BookFlightCommand command = handler.handle(flight("SFO", null, "FRA", "Asia/Tokyo"));

        assertThat(command.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void unknownAirportCodeWithNoPickIsRejected() {
        assertThatThrownBy(() -> handler.handle(flight("SFO", null, "ZZZ", null)))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void unknownAirportCodeIsAcceptedOnceAZoneIsPicked() {
        BookFlightCommand command = handler.handle(flight("SFO", null, "ZZZ", "US_EASTERN"));

        assertThat(command.arrivalDateTime().zone())
                .isEqualTo(ZoneId.of("America/New_York"));
    }

    private static BookFlightRequest flight(String departureAirport, String departureZone,
                                            String arrivalAirport, String arrivalZone) {
        BookFlightRequest request = new BookFlightRequest();
        request.setFlightId(UUID.randomUUID().toString());
        request.setAirline("Lufthansa");
        request.setFlightNumber("LH441");
        request.setDepartureAirport(departureAirport);
        request.setDepartureZone(departureZone);
        request.setDepartureDateTime(LocalDateTime.of(2026, 9, 15, 15, 55));
        request.setArrivalAirport(arrivalAirport);
        request.setArrivalZone(arrivalZone);
        request.setArrivalDateTime(LocalDateTime.of(2026, 9, 16, 11, 45));
        return request;
    }
}

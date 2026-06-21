package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.AirportCode;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AirportZoneResolverTest {

    private final AirportZoneResolver resolver = new AirportZoneResolver();

    @Test
    void resolvesKnownAirportToItsZone() {
        assertThat(resolver.resolve(AirportCode.of("FRA")))
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(resolver.resolve(AirportCode.of("SFO")))
                .isEqualTo(ZoneId.of("America/Los_Angeles"));
        assertThat(resolver.resolve(AirportCode.of("JFK")))
                .isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void throwsForAirportNotInTheCuratedTable() {
        assertThatThrownBy(() -> resolver.resolve(AirportCode.of("XXX")))
                .isInstanceOf(ZoneResolutionException.class);
    }
}

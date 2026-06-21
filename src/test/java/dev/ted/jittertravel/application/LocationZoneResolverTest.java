package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationZoneResolverTest {

    private final LocationZoneResolver resolver = new LocationZoneResolver();

    private static Address address(String city, String country) {
        return new Address("", city, "", "", country, "");
    }

    @Test
    void resolvesSingleZoneCountryFromCountryName() {
        assertThat(resolver.resolve(address("Frankfurt", "Germany")))
                .isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void cityTakesPrecedenceForMultiZoneCountry() {
        assertThat(resolver.resolve(address("Chicago", "USA")))
                .as("a US city must resolve to its own zone, not a country default")
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void resolvesCityCaseAndWhitespaceInsensitively() {
        assertThat(resolver.resolve(address("  NEW YORK ", "United States")))
                .isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    void throwsWhenLocationUnknown() {
        assertThatThrownBy(() -> resolver.resolve(address("Nowheresville", "Atlantis")))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void throwsForNullAddress() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void resolvesByCityCountryPairForTrainStations() {
        assertThat(resolver.resolve("Frankfurt", "Germany"))
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(resolver.resolve("Chicago", "USA"))
                .as("city wins over country for multi-zone countries")
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void throwsWhenCityCountryPairUnknown() {
        assertThatThrownBy(() -> resolver.resolve("Nowheresville", "Atlantis"))
                .isInstanceOf(ZoneResolutionException.class);
    }
}

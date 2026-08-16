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

    private static Address address(String city, String region, String country) {
        return new Address("", city, region, "", country, "");
    }

    @Test
    void resolvesSingleZoneCountryFromCountryName() {
        assertThat(resolver.resolve(address("Frankfurt", "Germany")))
                .isEqualTo(ZoneId.of("Europe/Berlin"));
    }

    @Test
    void resolvesMoroccoFromItsCountryName() {
        assertThat(resolver.resolve(address("Casablanca", "Morocco")))
                .isEqualTo(ZoneId.of("Africa/Casablanca"));
    }

    @Test
    void resolvesAntwerpFromTheCityEvenWhenTheCountryFieldIsMisfiled() {
        // Real stored data: an Antwerp hotel whose country field holds "Brussels" (a city, not a
        // country) and so resolves nowhere on its own. The city step runs first, landing Antwerp in
        // Belgium's zone regardless of the unusable country.
        assertThat(resolver.resolve(address("Antwerp", "Brussels")))
                .isEqualTo(ZoneId.of("Europe/Brussels"));
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

    // --- state/province resolution: the city table cannot list every small town, and for the
    // multi-zone countries the country alone is ambiguous, so the region carries the answer.

    @Test
    void unknownUsTownResolvesFromItsStateAbbreviation() {
        assertThat(resolver.resolve(address("Lone Tree", "CO", "USA")))
                .as("a town the city table does not know resolves from its state")
                .isEqualTo(ZoneId.of("America/Denver"));
    }

    @Test
    void unknownCanadianTownResolvesFromItsSpelledOutProvince() {
        assertThat(resolver.resolve(address("North Gower", "Ontario", "Canada")))
                .as("stored data spells some regions out and abbreviates others; both must work")
                .isEqualTo(ZoneId.of("America/Toronto"));
    }

    @Test
    void stateAbbreviationAndFullNameResolveAlike() {
        assertThat(resolver.resolve(address("Somewhere", "CA", "USA")))
                .isEqualTo(resolver.resolve(address("Somewhere", "California", "USA")));
    }

    @Test
    void cityStillWinsOverItsState() {
        assertThat(resolver.resolve(address("Phoenix", "AZ", "USA")))
                .as("the city table holds the exceptions, so it must be consulted first")
                .isEqualTo(ZoneId.of("America/Phoenix"));
    }

    @Test
    void regionIsScopedToItsCountry() {
        assertThat(resolver.resolve(address("Somewhere", "WA", "USA")))
                .isEqualTo(ZoneId.of("America/Los_Angeles"));
        assertThat(resolver.resolve(address("Somewhere", "WA", "Australia")))
                .as("WA is Washington in the USA and Western Australia in Australia — the same key "
                    + "must not resolve to one zone for both")
                .isEqualTo(ZoneId.of("Australia/Perth"));
    }

    @Test
    void regionIsIgnoredForSingleZoneCountries() {
        assertThat(resolver.resolve(address("Steventon", "Abingdon", "UK")))
                .as("a non-region region ('Abingdon' is a town) must not block the country fallback")
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    void unknownRegionInAMultiZoneCountryStillThrows() {
        assertThatThrownBy(() -> resolver.resolve(address("Nowheresville", "XX", "USA")))
                .as("there is no country-level default for a multi-zone country — it must fail loudly")
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void failureMessageNamesTheRegionItTried() {
        assertThatThrownBy(() -> resolver.resolve(address("Nowheresville", "XX", "USA")))
                .hasMessageContaining("region='XX'");
    }
}

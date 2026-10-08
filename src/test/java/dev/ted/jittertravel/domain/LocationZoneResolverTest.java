package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A country is stored as its ISO code and a US, Canadian or Australian region as its postal code
 * (docs/LocationDataCleanupPlan.md D2, D3), so those are the only keys the resolver knows.
 */
class LocationZoneResolverTest {

    private final LocationZoneResolver resolver = new LocationZoneResolver();

    private static Address address(String city, String country) {
        return new Address("", city, "", "", country, "");
    }

    private static Address address(String city, String region, String country) {
        return new Address("", city, region, "", country, "");
    }

    @ParameterizedTest
    @CsvSource({
            "Soltau, DE, Europe/Berlin",
            "Steventon, GB, Europe/London",
            "Gembloux, be, Europe/Brussels",
            "Casablanca, MA, Africa/Casablanca"
    })
    void resolvesASingleZoneCountryFromItsIsoCode(String city, String country, String zone) {
        assertThat(resolver.resolve(address(city, country)))
                .as(city + ", " + country)
                .isEqualTo(ZoneId.of(zone));
    }

    @ParameterizedTest
    @CsvSource({
            "Centennial, CO, US, America/Denver",
            "North Kawartha, ON, CA, America/Toronto"
    })
    void resolvesAnUnknownTownFromItsPickedStateAndCountryCodes(String city, String region,
                                                                String country, String zone) {
        assertThat(resolver.resolve(address(city, region, country)))
                .as(city + ", " + region + ", " + country)
                .isEqualTo(ZoneId.of(zone));
    }

    /**
     * The spellings stored before countries and states were picked went with the location-codes
     * migration (2026-10-08). A name is now as unknown as any other text.
     */
    @ParameterizedTest
    @CsvSource({
            "Frankfurt, '', Germany",
            "North Gower, Ontario, CA",
            "Lone Tree, Colorado, US",
            "Antwerp, '', Brussels"
    })
    void aCountryOrStateStoredAsANameNoLongerResolves(String city, String region, String country) {
        assertThatThrownBy(() -> resolver.resolve(address(city, region, country)))
                .as(city + ", " + region + ", " + country)
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void cityTakesPrecedenceForMultiZoneCountry() {
        assertThat(resolver.resolve(address("Chicago", "US")))
                .as("a US city must resolve to its own zone, not a country default")
                .isEqualTo(ZoneId.of("America/Chicago"));
    }

    @Test
    void resolvesCityCaseAndWhitespaceInsensitively() {
        assertThat(resolver.resolve(address("  NEW YORK ", "US")))
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
        assertThat(resolver.resolve("Frankfurt", "DE"))
                .isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(resolver.resolve("Chicago", "US"))
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
    void cityStillWinsOverItsState() {
        assertThat(resolver.resolve(address("Phoenix", "AZ", "US")))
                .as("the city table holds the exceptions, so it must be consulted first")
                .isEqualTo(ZoneId.of("America/Phoenix"));
    }

    @Test
    void regionIsScopedToItsCountry() {
        assertThat(resolver.resolve(address("Somewhere", "WA", "US")))
                .isEqualTo(ZoneId.of("America/Los_Angeles"));
        assertThat(resolver.resolve(address("Somewhere", "WA", "AU")))
                .as("WA is Washington in the US and Western Australia in Australia — the same key "
                    + "must not resolve to one zone for both")
                .isEqualTo(ZoneId.of("Australia/Perth"));
    }

    @Test
    void regionIsIgnoredForSingleZoneCountries() {
        assertThat(resolver.resolve(address("Steventon", "Abingdon", "GB")))
                .as("a non-region region ('Abingdon' is a town) must not block the country fallback")
                .isEqualTo(ZoneId.of("Europe/London"));
    }

    @Test
    void unknownRegionInAMultiZoneCountryStillThrows() {
        assertThatThrownBy(() -> resolver.resolve(address("Nowheresville", "XX", "US")))
                .as("there is no country-level default for a multi-zone country — it must fail loudly")
                .isInstanceOf(ZoneResolutionException.class);
    }

    @Test
    void failureMessageNamesTheRegionItTried() {
        assertThatThrownBy(() -> resolver.resolve(address("Nowheresville", "XX", "US")))
                .hasMessageContaining("region='XX'");
    }
}

package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAirportCityResolverTest {

    private final StaticAirportCityResolver resolver = new StaticAirportCityResolver();

    @Test
    void aKnownCodeResolvesToItsCity() {
        assertThat(resolver.cityFor("DEN")).isEqualTo("Denver");
    }

    @Test
    void ottawaIsKnownBothWays() {
        assertThat(resolver.cityFor("YOW"))
                .isEqualTo("Ottawa");
        assertThat(resolver.soleAirportFor("Ottawa"))
                .contains("YOW");
    }

    @Test
    void anUnknownCodeIsReturnedAsIsRatherThanGuessed() {
        assertThat(resolver.cityFor("ZZZ")).isEqualTo("ZZZ");
    }

    @Test
    void aCityWithExactlyOneAirportResolvesBackToIt() {
        assertThat(resolver.soleAirportFor("Frankfurt")).contains("FRA");
        assertThat(resolver.soleAirportFor("Denver")).contains("DEN");
    }

    /**
     * The reason the fix link carries cities rather than codes. Picking one of London's four would
     * be a guess, and Ted has to notice a wrong prefilled airport to undo it — so the answer is
     * "nothing", and the field stays blank.
     */
    @Test
    void aCityWithSeveralAirportsResolvesToNothing() {
        assertThat(resolver.soleAirportFor("London"))
                .as("LHR, LGW, STN and LCY — there is no single answer")
                .isEmpty();
        assertThat(resolver.soleAirportFor("New York"))
                .as("JFK, EWR and LGA")
                .isEmpty();
        assertThat(resolver.soleAirportFor("Paris")).isEmpty();
        assertThat(resolver.soleAirportFor("Chicago")).isEmpty();
        assertThat(resolver.soleAirportFor("Tokyo")).isEmpty();
        assertThat(resolver.soleAirportFor("Washington DC")).isEmpty();
    }

    @Test
    void aCityTheTableDoesNotKnowResolvesToNothing() {
        assertThat(resolver.soleAirportFor("Soltau")).isEmpty();
    }

    @Test
    void theLookupIgnoresCaseAndSurroundingSpace() {
        // Cities reach this from a problem record, which took them from an address someone typed.
        assertThat(resolver.soleAirportFor("  denver ")).contains("DEN");
    }

    @Test
    void aMissingCityIsNotAnError() {
        assertThat(resolver.soleAirportFor(null)).isEmpty();
    }

    @Test
    void anAirportsAddressIsItsCityStateAndCountryCode() {
        assertThat(resolver.addressFor("den"))
                .as("Denver International, looked up in lower case")
                .hasValue(new Address("", "Denver", "CO", "", "US", "Denver"));
    }

    @Test
    void anAirportOutsideTheStateListCountriesHasNoRegion() {
        assertThat(resolver.addressFor("FRA"))
                .as("Frankfurt")
                .hasValue(new Address("", "Frankfurt", "", "", "DE", "Frankfurt"));
    }

    @Test
    void anUnknownAirportHasNoAddress() {
        assertThat(resolver.addressFor("ZZZ"))
                .as("A code the table does not know")
                .isEmpty();
    }

    /**
     * Every entry is held to the rules a typed address now meets: a real ISO country, and a state
     * from the list exactly where the country has one. A wrong entry here would be frozen into every
     * transfer that leaves from that airport.
     */
    @Test
    void everyEntryIsACompleteAddress() {
        Countries countries = new Countries();
        Subdivisions subdivisions = new Subdivisions();
        for (String code : resolver.knownCodes()) {
            assertThat(resolver.addressFor(code))
                    .as("Address for " + code)
                    .isPresent();
            Address address = resolver.addressFor(code).orElseThrow();
            assertThat(countries.isKnown(address.country()))
                    .as(code + " has a known country code: " + address.country())
                    .isTrue();
            if (subdivisions.required(address.country())) {
                assertThat(subdivisions.find(address.country(), address.region()))
                        .as(code + " names a state of " + address.country() + ": " + address.region())
                        .isPresent();
            } else {
                assertThat(address.region())
                        .as(code + " has no region outside the state-list countries")
                        .isEmpty();
            }
        }
        assertThat(resolver.knownCodes())
                .as("All 58 airports were checked")
                .hasSize(58);
    }
}

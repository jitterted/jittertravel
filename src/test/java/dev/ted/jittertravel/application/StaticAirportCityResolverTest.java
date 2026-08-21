package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAirportCityResolverTest {

    private final StaticAirportCityResolver resolver = new StaticAirportCityResolver();

    @Test
    void aKnownCodeResolvesToItsCity() {
        assertThat(resolver.cityFor("DEN")).isEqualTo("Denver");
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
}

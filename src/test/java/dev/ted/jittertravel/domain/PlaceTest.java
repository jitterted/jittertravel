package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every case here is built so the <em>wrong</em> field is a different string from the right one —
 * an address whose {@code city} differs from its {@code locationForMatching}, a station whose
 * {@code name} differs from its {@code city}, an airport code the table maps to a city. A fixture
 * where both fields read the same would pass whichever field production picked, which is precisely
 * the divergence this type exists to prevent.
 */
class PlaceTest {

    @Test
    void addressYieldsLocationForMatchingRatherThanCity() {
        Address venueInAHamlet = new Address("Schlossweg 1", "Rückersbach", "BY", "63867",
                                             "DE", "Johannesberg");

        assertThat(Place.of(venueInAHamlet).value())
                .isEqualTo("Johannesberg");
    }

    @Test
    void addressWithNoSeparateMatchingLocationFallsBackToItsCity() {
        Address plainAddress = new Address("1 Market St", "San Francisco", "CA", "94105",
                                           "US", "");

        assertThat(Place.of(plainAddress).value())
                .isEqualTo("San Francisco");
    }

    @Test
    void stationYieldsItsCityRatherThanItsOwnName() {
        TrainStationAddress hamburgHbf =
                new TrainStationAddress("Hamburg Hbf", "Hamburg", "DE", "");

        assertThat(Place.of(hamburgHbf).value())
                .isEqualTo("Hamburg");
    }

    @Test
    void airportYieldsTheCityTheCuratedTableNames() {
        assertThat(Place.of(AirportCode.of("DEN"), new StaticAirportCityResolver()).value())
                .isEqualTo("Denver");
    }

    @Test
    void airportTheTableDoesNotKnowFallsBackToTheCodeItself() {
        assertThat(Place.of(AirportCode.of("ZZZ"), new StaticAirportCityResolver()).value())
                .isEqualTo("ZZZ");
    }

    @Test
    void placesMatchIgnoringCase() {
        assertThat(new Place("Denver").matches(new Place("denver")))
                .as("an address Ted typed and a curated table's spelling differ in case")
                .isTrue();
    }

    @Test
    void differentPlacesDoNotMatch() {
        assertThat(new Place("Denver").matches(new Place("Boulder")))
                .isFalse();
    }

    /**
     * The trap this type carries: two Places that {@code matches} agree on are <em>not</em>
     * {@code equals}, because the record keeps the spelling it was given for display. Pinned so
     * that anyone who later "simplifies" a call site to {@code equals} sees what it costs.
     */
    @Test
    void equalsIsCaseSensitiveWhereMatchesIsNot() {
        Place typed = new Place("Denver");
        Place curated = new Place("denver");

        assertThat(typed.matches(curated)).isTrue();
        assertThat(typed).isNotEqualTo(curated);
    }

    @Test
    void aNullValueBecomesTheBlankPlace() {
        assertThat(new Place(null).value())
                .isEqualTo("");
    }

    /**
     * The airport factory takes the resolver rather than a table of its own, so a resolver that
     * disagrees with the curated one is followed — this is the seam slice 3 needs for stations.
     */
    @Test
    void airportFactoryAsksTheResolverItIsGiven() {
        AirportCityResolver alwaysAtlantis = new AirportCityResolver() {
            @Override
            public String cityFor(String airportCode) {
                return "Atlantis";
            }

            @Override
            public Optional<String> soleAirportFor(String city) {
                return Optional.empty();
            }
        };

        assertThat(Place.of(AirportCode.of("DEN"), alwaysAtlantis).value())
                .isEqualTo("Atlantis");
    }
}

package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SubdivisionsTest {

    private final Subdivisions subdivisions = new Subdivisions();

    @ParameterizedTest
    @ValueSource(strings = {"US", "CA", "AU", " us "})
    void theUnitedStatesCanadaAndAustraliaRequireARegion(String country) {
        assertThat(subdivisions.required(country))
                .as("'" + country + "' requires a region")
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DE", "GB", "United States", ""})
    void everywhereElseTheRegionIsFreeText(String country) {
        assertThat(subdivisions.required(country))
                .as("'" + country + "' requires a region")
                .isFalse();
        assertThat(subdivisions.of(country))
                .as("Subdivisions of '" + country + "'")
                .isEmpty();
    }

    @Test
    void eachListIsComplete() {
        assertThat(subdivisions.of("US"))
                .as("50 states and DC")
                .hasSize(51);
        assertThat(subdivisions.of("CA"))
                .as("10 provinces and 3 territories")
                .hasSize(13);
        assertThat(subdivisions.of("AU"))
                .as("6 states and 2 territories")
                .hasSize(8);
    }

    @Test
    void aCodeIsFoundInItsOwnCountry() {
        assertThat(subdivisions.find("US", "co"))
                .as("Colorado in the US")
                .hasValue(new Subdivision("CO", "Colorado"));
        assertThat(subdivisions.find("AU", "WA"))
                .as("WA in Australia")
                .hasValue(new Subdivision("WA", "Western Australia"));
        assertThat(subdivisions.find("US", "WA"))
                .as("WA in the US")
                .hasValue(new Subdivision("WA", "Washington"));
    }

    @Test
    void aNameIsFoundInItsOwnCountryOnly() {
        assertThat(subdivisions.findByName("US", " georgia "))
                .as("Georgia in the US")
                .hasValue(new Subdivision("GA", "Georgia"));
        assertThat(subdivisions.findByName("CA", "Georgia"))
                .as("Georgia in Canada")
                .isEmpty();
        assertThat(subdivisions.findByName("US", "GA"))
                .as("A code, not a name")
                .isEmpty();
    }

    @Test
    void aCodeFromAnotherCountryOrANameIsNotFound() {
        assertThat(subdivisions.find("CA", "CO"))
                .as("CO in Canada")
                .isEmpty();
        assertThat(subdivisions.find("US", "Colorado"))
                .as("A name, not a code")
                .isEmpty();
        assertThat(subdivisions.find("DE", "BY"))
                .as("A country with no list")
                .isEmpty();
    }
}

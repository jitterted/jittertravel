package dev.ted.jittertravel.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UsStatesTest {

    private final UsStates usStates = new UsStates();

    @ParameterizedTest
    @ValueSource(strings = {"USA", "United States", "us", " U.S.A. ", "United States of America"})
    void everySpellingOfTheCountryInStoredDataIsTheUnitedStates(String country) {
        assertThat(usStates.isUnitedStates(country))
                .as("'" + country + "' names the United States")
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Canada", "UK", "", "Brussels"})
    void otherCountriesAreNot(String country) {
        assertThat(usStates.isUnitedStates(country))
                .as("'" + country + "' is not the United States")
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CO", "co", "Colorado", " colorado "})
    void aStateByNameOrCodeBecomesItsCode(String region) {
        assertThat(usStates.stateCode(region))
                .as("State code for '" + region + "'")
                .hasValue("CO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"District of Columbia", "DC"})
    void washingtonDcIsInTheTable(String region) {
        assertThat(usStates.stateCode(region))
                .as("State code for '" + region + "'")
                .hasValue("DC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Colorad", "Ontario", "Manhattan", ""})
    void anythingElseHasNoCode(String region) {
        assertThat(usStates.stateCode(region))
                .as("'" + region + "' is not a state")
                .isEmpty();
    }
}

package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class EnteredCountryTest {

    private static Address address(String region, String country) {
        return new Address("", "Somewhere", region, "", country, null);
    }

    @ParameterizedTest
    @CsvSource({
            "CO, US",
            "ON, CA",
            "NSW, AU",
            "Abingdon, GB",
            "'', DE",
            "'', ''"
    })
    void aPlaceInAKnownCountryWithAStateWhereOneIsNeededPasses(String region, String country) {
        assertThat(EnteredCountry.of(address(region, country)).problems(LocationRole.STAY))
                .as(region + ", " + country)
                .isEmpty();
    }

    @Test
    void aCountryThatIsNotACodeIsUnknown() {
        List<InvalidLocationEntry> problems =
                EnteredCountry.of(address("", "Germany")).problems(LocationRole.STAY);

        assertThat(problems)
                .extracting(InvalidLocationEntry::role, InvalidLocationEntry::field,
                            InvalidLocationEntry::getMessage)
                .containsExactly(tuple(LocationRole.STAY, LocationField.COUNTRY, "Unknown country"));
    }

    @ParameterizedTest
    @CsvSource({
            "US, State required for United States",
            "CA, Province required for Canada",
            "AU, State required for Australia"
    })
    void theStateListCountriesRequireARegion(String country, String message) {
        assertThat(EnteredCountry.of(address("", country)).problems(LocationRole.STAY))
                .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                .containsExactly(tuple(LocationField.REGION, message));
    }

    @ParameterizedTest
    @CsvSource({
            "Colorado, US, Not a state of United States",
            "CO, CA, Not a province of Canada",
            "Lower Downtown, US, Not a state of United States"
    })
    void aRegionThatIsNotOneOfTheCountrysCodesIsRefused(String region, String country, String message) {
        assertThat(EnteredCountry.of(address(region, country)).problems(LocationRole.STAY))
                .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                .containsExactly(tuple(LocationField.REGION, message));
    }

    @Test
    void aStationIsNeverAskedForARegion() {
        TrainStationAddress station = new TrainStationAddress("Denver Union Station", "Denver", "US", "");

        assertThat(EnteredCountry.of(station).problems(LocationRole.ARRIVAL))
                .as("a station has no region field to fill in")
                .isEmpty();
    }

    @Test
    void aStationsCountryIsStillChecked() {
        TrainStationAddress station = new TrainStationAddress("Didcot Parkway", "Didcot", "UK", "");

        assertThat(EnteredCountry.of(station).problems(LocationRole.DEPARTURE))
                .extracting(InvalidLocationEntry::role, InvalidLocationEntry::field)
                .containsExactly(tuple(LocationRole.DEPARTURE, LocationField.COUNTRY));
    }

    @Test
    void anAbsentAddressHasNothingToCheck() {
        assertThat(EnteredCountry.of((Address) null))
                .isEqualTo(EnteredCountry.NONE);
        assertThat(EnteredCountry.NONE.problems(LocationRole.STAY))
                .isEmpty();
    }

    @Test
    void checkThrowsEveryProblemTogether() {
        assertThatThrownBy(() -> EnteredCountry.of(address("", "US")).check(LocationRole.STAY))
                .isInstanceOfSatisfying(InvalidEnteredLocation.class, invalid ->
                        assertThat(invalid.problems())
                                .extracting(InvalidLocationEntry::field)
                                .containsExactly(LocationField.REGION));
    }

    @Test
    void checkPassesQuietlyWhenThereIsNothingWrong() {
        EnteredCountry.of(address("CO", "US")).check(LocationRole.STAY);
    }
}

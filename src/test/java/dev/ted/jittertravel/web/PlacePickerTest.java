package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.Country;
import dev.ted.jittertravel.domain.Subdivision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlacePickerTest {

    private final PlacePicker picker = new PlacePicker(
            List.of(new Country("DE", "Germany"), new Country("US", "United States")),
            country -> country.equals("US") ? List.of(new Subdivision("CO", "Colorado")) : List.of());

    @Test
    void theShortListIsWhatWasUsedAndEveryCountryIsStillOffered() {
        assertThat(picker.usedCountries())
                .extracting(Country::code)
                .containsExactly("DE", "US");
        assertThat(picker.allCountries())
                .hasSize(198);
        assertThat(picker.isUsed(" us "))
                .isTrue();
        assertThat(picker.isUsed("GB"))
                .isFalse();
    }

    @Test
    void aStoredValueThatIsNotACodeIsShownAsItIs() {
        assertThat(picker.isKnown("US"))
                .isTrue();
        assertThat(picker.isKnown("United States"))
                .isFalse();
        assertThat(picker.countryName("United States"))
                .isEqualTo("United States");
        assertThat(picker.countryName("GB"))
                .isEqualTo("United Kingdom");
    }

    @Test
    void canadaHasProvincesAndTheOthersStates() {
        assertThat(picker.hasStateList("CA"))
                .isTrue();
        assertThat(picker.stateLabel("CA"))
                .isEqualTo("Province");
        assertThat(picker.stateLabel("AU"))
                .isEqualTo("State");
        assertThat(picker.hasStateList("DE"))
                .isFalse();
    }

    @Test
    void theStatesUsedComeFromTheHistoryAndAreRecognised() {
        assertThat(picker.usedStates("us"))
                .containsExactly(new Subdivision("CO", "Colorado"));
        assertThat(picker.isUsedState("US", "co"))
                .isTrue();
        assertThat(picker.isUsedState("US", "NY"))
                .isFalse();
        assertThat(picker.isStateOf("US", "NY"))
                .isTrue();
        assertThat(picker.isStateOf("US", "Colorado"))
                .as("a name is not a code")
                .isFalse();
        assertThat(picker.states("AU"))
                .hasSize(8);
    }

    @Test
    void theScriptGetsEachStateListWithItsLabelNameAndUsedStates() {
        Map<String, Map<String, Object>> lists = picker.stateListsForScript();

        assertThat(lists)
                .containsOnlyKeys("US", "CA", "AU");
        assertThat(lists.get("US"))
                .containsEntry("label", "State")
                .containsEntry("name", "United States")
                .containsEntry("used", List.of(List.of("CO", "Colorado")));
        assertThat(lists.get("CA"))
                .containsEntry("label", "Province")
                .containsEntry("used", List.of());
        assertThat(lists.get("AU").get("all"))
                .asList()
                .contains(List.of("WA", "Western Australia"));
    }

    @Test
    void withNothingUsedEveryCountryIsBehindAnotherCountry() {
        PlacePicker empty = PlacePicker.withNothingUsed();

        assertThat(empty.usedCountries())
                .isEmpty();
        assertThat(empty.usedStates("US"))
                .isEmpty();
    }
}

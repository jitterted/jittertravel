package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CountriesTest {

    private final Countries countries = new Countries();

    @ParameterizedTest
    @CsvSource({
            "US, United States",
            "GB, United Kingdom",
            "DE, Germany",
            "CZ, Czechia",
            "TR, Türkiye",
            "' de ', Germany"
    })
    void aCodeNamesItsCountry(String code, String name) {
        assertThat(countries.name(code))
                .as("Name for '" + code + "'")
                .hasValue(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Germany", "UK", "USA", "XX", ""})
    void aNameOrAnUnassignedCodeIsNotACountryCode(String value) {
        assertThat(countries.isKnown(value))
                .as("'" + value + "' is a country code")
                .isFalse();
        assertThat(countries.name(value))
                .as("Name for '" + value + "'")
                .isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "United States, US",
            "' sweden ', SE",
            "Türkiye, TR"
    })
    void aNameAsTheTableSpellsItHasACode(String name, String code) {
        assertThat(countries.codeFor(name))
                .as("Code for '" + name + "'")
                .hasValue(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USA", "UK", "Turkey", ""})
    void anAbbreviationOrAnOldNameHasNoCode(String name) {
        assertThat(countries.codeFor(name))
                .as("Code for '" + name + "'")
                .isEmpty();
    }

    @Test
    void aNullCodeIsNotACountry() {
        assertThat(countries.isKnown(null))
                .as("null is a country code")
                .isFalse();
    }

    @Test
    void allIsEveryCountryAToZByName() {
        List<Country> all = countries.all();

        assertThat(all)
                .as("Every UN member plus Hong Kong, Taiwan, Puerto Rico, Palestine and Vatican City")
                .hasSize(198);
        assertThat(all.getFirst())
                .as("First by name")
                .isEqualTo(new Country("AF", "Afghanistan"));
        assertThat(all.getLast())
                .as("Last by name")
                .isEqualTo(new Country("ZW", "Zimbabwe"));
        assertThat(all)
                .as("Sorted by name, not by code")
                .containsSubsequence(new Country("DE", "Germany"), new Country("GH", "Ghana"));
    }
}

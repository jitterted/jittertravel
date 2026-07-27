package dev.ted.jittertravel.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomeCitiesTest {

    private static final HomeCities BAY_AREA =
            new HomeCities(List.of("San Francisco", "San Jose", "Oakland"));

    @Test
    void includesConfiguredCityIgnoringCase() {
        assertThat(BAY_AREA.includes("san francisco"))
                .as("'san francisco' is a home city regardless of case")
                .isTrue();
    }

    @Test
    void doesNotIncludeCityThatIsNotHome() {
        assertThat(BAY_AREA.includes("Amsterdam"))
                .as("Amsterdam is not a home city")
                .isFalse();
    }

    @Test
    void configuredNamesAreTrimmed() {
        HomeCities homeCities = new HomeCities(List.of(" San Francisco ", "", "  "));

        assertThat(homeCities.includes("San Francisco"))
                .as("surrounding whitespace in the configured name is ignored")
                .isTrue();
    }

    @Test
    void twoDifferentHomeCitiesAreTheSameLocation() {
        assertThat(BAY_AREA.sameLocation("San Francisco", "San Jose"))
                .as("all home cities are one place for travel purposes")
                .isTrue();
    }

    @Test
    void homeCityAndNonHomeCityAreDifferentLocations() {
        assertThat(BAY_AREA.sameLocation("San Francisco", "Los Angeles"))
                .as("a home city and a non-home city are different places")
                .isFalse();
    }

    @Test
    void sameNameIsSameLocationIgnoringCase() {
        assertThat(BAY_AREA.sameLocation("amsterdam", "Amsterdam"))
                .as("identical names differing only in case are the same place")
                .isTrue();
    }

    @Test
    void emptyHomeCitiesIncludesNothing() {
        HomeCities noHome = new HomeCities(List.of());

        assertThat(noHome.includes("San Francisco"))
                .as("with no home cities configured, nothing is home")
                .isFalse();
    }

    @Test
    void emptyHomeCitiesComparesNamesOnly() {
        HomeCities noHome = new HomeCities(List.of());

        assertThat(noHome.sameLocation("San Francisco", "San Jose"))
                .as("with no home cities configured, only matching names are the same place")
                .isFalse();
    }
}

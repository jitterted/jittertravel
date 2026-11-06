package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventLogFilterTest {

    @Test
    void nothingSubmittedIsTheEmptyFilter() {
        EventLogFilter filter = EventLogFilter.from(List.of(), "");

        assertThat(filter)
                .isEqualTo(EventLogFilter.none());
        assertThat(filter.isEmpty())
                .as("no types and no identifiers narrows nothing")
                .isTrue();
    }

    @Test
    void registeredTypeNamesAreKeptInTheOrderSubmitted() {
        EventLogFilter filter = EventLogFilter.from(List.of("FlightBooked", "FamilyNotified"), "");

        assertThat(filter.types())
                .containsExactly("FlightBooked", "FamilyNotified");
        assertThat(filter.isEmpty())
                .as("types alone make a filter")
                .isFalse();
    }

    @Test
    void anUnknownTypeNameIsDroppedAndTheRestKept() {
        EventLogFilter filter = EventLogFilter.from(List.of("NoSuchEvent", "FlightBooked"), "");

        assertThat(filter.types())
                .as("a bookmark naming a type since renamed still shows the others")
                .containsExactly("FlightBooked");
    }

    @Test
    void aStoredSpellingIsNotATypeNameThePageSpeaks() {
        EventLogFilter filter = EventLogFilter.from(
                List.of("dev.ted.jittertravel.domain.FlightBooked", "ConferenceTentativelyPlanned"), "");

        assertThat(filter.types())
                .as("the page offers logical names only, so an FQCN or a retired name is unknown to it")
                .isEmpty();
    }

    @Test
    void aTypeNamedTwiceOrWithStraySpacesIsOneType() {
        EventLogFilter filter = EventLogFilter.from(List.of("FlightBooked", " FlightBooked "), "");

        assertThat(filter.types())
                .containsExactly("FlightBooked");
    }

    @Test
    void identifiersAreSplitOnSpacesCommasAndLineBreaks() {
        EventLogFilter filter = EventLogFilter.from(List.of(), "aaa bbb,ccc ,  ddd\neee\tfff");

        assertThat(filter.identifiers())
                .containsExactly("aaa", "bbb", "ccc", "ddd", "eee", "fff");
        assertThat(filter.isEmpty())
                .as("identifiers alone make a filter")
                .isFalse();
    }

    @Test
    void separatorsWithNothingBetweenThemLeaveNoIdentifier() {
        assertThat(EventLogFilter.from(List.of(), " , ,, ").identifiers())
                .isEmpty();
        assertThat(EventLogFilter.from(List.of(), ", aaa ,").identifiers())
                .as("a leading or trailing separator adds no blank identifier")
                .containsExactly("aaa");
    }

    @Test
    void anIdentifierGivenTwiceIsLookedForOnce() {
        assertThat(EventLogFilter.from(List.of(), "aaa bbb aaa").identifiers())
                .containsExactly("aaa", "bbb");
    }
}

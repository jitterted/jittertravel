package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The normalization every address carries: no nulls, and no surrounding whitespace.
 * <p>
 * The whitespace half is not tidiness. {@code city}/{@code locationForMatching} are <em>compared</em>
 * — see {@link Place} and {@code HomeCities.sameLocation} — while HTML collapses a trailing space,
 * so an untrimmed city is a different city that looks like the same one. Production event 92
 * (2026-08-30) stored {@code "Hamburg "} from an iPhone keyboard and the schedule reported a
 * journey from Hamburg to Hamburg.
 */
class AddressTest {

    @Test
    void surroundingWhitespaceIsRemovedFromEveryField() {
        Address typedOnAPhone = new Address(" 26 Neuer Steinweg ", "Hamburg ", " HH",
                                            "20459 ", "Germany ", " Hamburg ");

        assertThat(typedOnAPhone)
                .isEqualTo(new Address("26 Neuer Steinweg", "Hamburg", "HH",
                                       "20459", "Germany", "Hamburg"));
    }

    @Test
    void aCityTypedWithATrailingSpaceIsTheSamePlaceAsOneWithout() {
        Address hangout = new Address("", "Hamburg ", "", "", "Germany ", "Hamburg ");
        Address hotel = new Address("26 Neuer Steinweg", "Hamburg", "", "20459", "Germany", "Hamburg");

        assertThat(Place.of(hangout).matches(Place.of(hotel)))
                .as("a stray space is invisible in the markup and must be invisible to matching")
                .isTrue();
    }

    @Test
    void everyAbsentFieldBecomesTheBlankString() {
        Address nothingTyped = new Address(null, null, null, null, null, null);

        assertThat(nothingTyped)
                .isEqualTo(new Address("", "", "", "", "", ""));
    }

    @Test
    void absentMatchingLocationFallsBackToTheTrimmedCity() {
        Address plainAddress = new Address("1 Market St", " San Francisco ", "CA", "94105", "US", null);

        assertThat(plainAddress.locationForMatching())
                .isEqualTo("San Francisco");
    }

    /**
     * A field holding only spaces is nothing typed, not a place named with whitespace — so it falls
     * back to the city exactly as an absent one does. Checked after trimming, which is what makes
     * the two cases the same case.
     */
    @Test
    void whitespaceOnlyMatchingLocationFallsBackToTheCity() {
        Address plainAddress = new Address("1 Market St", "San Francisco", "CA", "94105", "US", "   ");

        assertThat(plainAddress.locationForMatching())
                .isEqualTo("San Francisco");
    }

    @Test
    void aMatchingLocationThatDiffersFromTheCityIsKept() {
        Address venueInAHamlet = new Address("Schlossweg 1", "Rückersbach", "BY", "63867",
                                             "DE", " Johannesberg ");

        assertThat(venueInAHamlet.locationForMatching())
                .isEqualTo("Johannesberg");
    }
}

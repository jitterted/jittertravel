package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The cases below ask {@link EnteredLocation#problems} directly rather than catching what
 * {@link EnteredLocation#check} throws: the list is the answer, and asserting on it says which
 * field and how many in one flat statement. {@code check}'s own contract — that it wraps the same
 * list in an {@link InvalidEnteredLocation}, and stays quiet when there is nothing to say — is
 * pinned in {@link Checking} at the bottom, once, rather than restated by every case.
 */
class EnteredLocationTest {

    @Nested
    class MissingValues {

        @Test
        void blankVenueNameIsRejectedAgainstTheNameField() {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("", "Frankfurt").problems(LocationRole.DEPARTURE);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field, InvalidLocationEntry::role,
                                InvalidLocationEntry::getMessage)
                    .containsExactly(tuple(LocationField.VENUE_NAME, LocationRole.DEPARTURE,
                                           "Name is required"));
        }

        @Test
        void venueNameOfOnlySpacesCountsAsMissing() {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("   ", "Frankfurt").problems(LocationRole.ARRIVAL);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field)
                    .containsExactly(LocationField.VENUE_NAME);
        }

        @Test
        void blankCityIsRejectedAgainstTheCityField() {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("Frankfurt (Main) Hbf", "").problems(LocationRole.ARRIVAL);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field, InvalidLocationEntry::role,
                                InvalidLocationEntry::getMessage)
                    .containsExactly(tuple(LocationField.CITY, LocationRole.ARRIVAL,
                                           "City is required"));
        }
    }

    /**
     * <strong>The reason {@code problems} returns a list.</strong> Both fields blank is one submit
     * with two mistakes, and it is the ordinary way a form arrives once the browser's
     * {@code required} is gone — which is how it reached the hotel forms on 2026-09-20. Reporting
     * only the name would mean fixing it, submitting again, and meeting a fresh error that on
     * screen is indistinguishable from the first fix having done nothing.
     */
    @Nested
    class EveryProblemAtOnce {

        @Test
        void aBlankNameAndABlankCityAreBothReported() {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("", "").problems(LocationRole.STAY);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                    .containsExactly(
                            tuple(LocationField.VENUE_NAME, "Name is required"),
                            tuple(LocationField.CITY, "City is required"));
        }

        @Test
        void aBlankNameDoesNotSuppressTheCityLookingLikeAVenue() {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("", "Frankfurt (Main) Hbf").problems(LocationRole.DEPARTURE);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                    .containsExactly(
                            tuple(LocationField.VENUE_NAME, "Name is required"),
                            tuple(LocationField.CITY, "Venue name, not a city"));
        }

        @Test
        void aBlankCityIsReportedAsBlankAndNotAlsoAskedWhetherItLooksLikeABuilding() {
            // One input never collects two messages — it has one <span class="error"> to put them
            // in. This case records that, but it cannot prove the else in problems() is load-
            // bearing: a blank city has no brackets, no digit and no word, so it passes with a
            // plain if too (mutation-checked). It is here for the rule a new city rule would break.
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("Grand Hotel", "  ").problems(LocationRole.STAY);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                    .containsExactly(tuple(LocationField.CITY, "City is required"));
        }

        @Test
        void nullsAreTreatedAsMissingRatherThanThrowingNullPointer() {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation(null, null).problems(LocationRole.STAY);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field)
                    .containsExactly(LocationField.VENUE_NAME, LocationField.CITY);
        }

        @Test
        void aLocationThatNamesAPlaceHasNoProblems() {
            assertThat(new EnteredLocation("Grand Hotel", "Berlin").problems(LocationRole.STAY))
                    .isEmpty();
        }
    }

    @Nested
    class TheWholeLinePastedIntoBothFields {

        @Test
        void isRejectedAgainstTheCityField() {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("Frankfurt (Main) Hbf", "Frankfurt (Main) Hbf")
                            .problems(LocationRole.DEPARTURE);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field)
                    .containsExactly(LocationField.CITY);
        }

        @Test
        void butAStationNamedForItsOwnTownIsNotAPaste() {
            // Gembloux is a real station named exactly for its town, and it is in the production
            // log twice. A rule rejecting "city repeats the name" was removed because of it.
            assertThat(new EnteredLocation("Gembloux", "Gembloux").problems(LocationRole.ARRIVAL))
                    .isEmpty();
        }

        @Test
        void norIsAnEventHeldInATownWithNoParticularVenue() {
            assertThat(new EnteredLocation("Hamburg", "Hamburg").problems(LocationRole.STAY))
                    .isEmpty();
        }
    }

    @Nested
    class CityShapedLikeAVenue {

        @ParameterizedTest
        @ValueSource(strings = {
                "Frankfurt (Main)",          // brackets: the shape of a pasted station line
                "Frankfurt(M) Flughafen",
                "Terminal 2",                // a digit belongs to a platform, never to a city
                "Frankfurt Hbf",
                "Berlin Hauptbahnhof",
                "Wien Bhf",
                "Amsterdam Centraal",
                "Milano Centrale",
                "Roma Termini",
                "Gare du Nord",
                "London St Pancras Station",
                "Denver Airport",
                "Grand Hotel"
        })
        void isRejectedAgainstTheCityField(String city) {
            List<InvalidLocationEntry> problems =
                    new EnteredLocation("Some Station", city).problems(LocationRole.DEPARTURE);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field, InvalidLocationEntry::getMessage)
                    .containsExactly(tuple(LocationField.CITY, "Venue name, not a city"));
        }

        @Test
        void aVenueWordInsideALongerWordDoesNotTripTheRule() {
            assertThat(new EnteredLocation("Frankfurt (Main) Hbf", "Frankfurter Berg")
                    .problems(LocationRole.DEPARTURE))
                    .isEmpty();
        }
    }

    @Nested
    class RealCitiesPass {

        @ParameterizedTest
        @ValueSource(strings = {
                "Frankfurt",
                "London",
                "New York",
                "Sankt Pölten",
                "Bad Homburg vor der Höhe",
                "Wasserburg am Inn",         // why "inn" is not in the word list
                "'s-Hertogenbosch",
                "Stoke-on-Trent"
        })
        void areAcceptedUnchanged(String city) {
            assertThat(new EnteredLocation("Some Station", city).problems(LocationRole.ARRIVAL))
                    .isEmpty();
        }
    }

    /** What {@code check} adds on top of {@code problems}: throw the whole list, or say nothing. */
    @Nested
    class Checking {

        @Test
        void throwsEveryProblemTogetherRatherThanTheFirst() {
            Throwable thrown =
                    catchThrowable(() -> new EnteredLocation("", "").check(LocationRole.STAY));

            assertThat(thrown)
                    .as("check reports through the carrier, never a bare entry")
                    .isInstanceOf(InvalidEnteredLocation.class);
            InvalidEnteredLocation invalid = (InvalidEnteredLocation) thrown;
            assertThat(invalid.problems())
                    .extracting(InvalidLocationEntry::field)
                    .containsExactly(LocationField.VENUE_NAME, LocationField.CITY);
        }

        @Test
        void theMessageNamesEveryProblem() {
            assertThatThrownBy(() -> new EnteredLocation("", "").check(LocationRole.STAY))
                    .hasMessage("Name is required City is required");
        }

        @Test
        void aLocationThatNamesAPlaceThrowsNothing() {
            assertThatCode(() -> new EnteredLocation("Grand Hotel", "Berlin").check(LocationRole.STAY))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class Factories {

        @Test
        void aStationContributesItsOwnNameAndItsCity() {
            TrainStationAddress station =
                    new TrainStationAddress("Frankfurt (Main) Hbf", "Frankfurt", "DE", "");

            assertThat(EnteredLocation.of(station))
                    .isEqualTo(new EnteredLocation("Frankfurt (Main) Hbf", "Frankfurt"));
        }

        @Test
        void aHotelContributesItsNameAndItsAddressCity() {
            Address address = new Address("123 Main St", "Springfield", "IL", "62701", "US", null);

            assertThat(EnteredLocation.of("Grand Hotel", address))
                    .isEqualTo(new EnteredLocation("Grand Hotel", "Springfield"));
        }

        @Test
        void anAbsentAddressReadsAsAMissingCityRatherThanThrowingNullPointer() {
            List<InvalidLocationEntry> problems =
                    EnteredLocation.of("Grand Hotel", null).problems(LocationRole.STAY);

            assertThat(problems)
                    .extracting(InvalidLocationEntry::field)
                    .containsExactly(LocationField.CITY);
        }
    }
}

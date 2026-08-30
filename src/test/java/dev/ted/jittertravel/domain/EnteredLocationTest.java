package dev.ted.jittertravel.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnteredLocationTest {

    @Nested
    class MissingValues {

        @Test
        void blankVenueNameIsRejectedAgainstTheNameField() {
            assertThatThrownBy(() -> new EnteredLocation("", "Frankfurt").check(LocationRole.DEPARTURE))
                    .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid -> {
                        assertThat(invalid.field())
                                .isEqualTo(LocationField.VENUE_NAME);
                        assertThat(invalid.role())
                                .isEqualTo(LocationRole.DEPARTURE);
                    });
        }

        @Test
        void venueNameOfOnlySpacesCountsAsMissing() {
            assertThatThrownBy(() -> new EnteredLocation("   ", "Frankfurt").check(LocationRole.ARRIVAL))
                    .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid ->
                            assertThat(invalid.field())
                                    .isEqualTo(LocationField.VENUE_NAME));
        }

        @Test
        void blankCityIsRejectedAgainstTheCityField() {
            assertThatThrownBy(() -> new EnteredLocation("Frankfurt (Main) Hbf", "").check(LocationRole.ARRIVAL))
                    .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid -> {
                        assertThat(invalid.field())
                                .isEqualTo(LocationField.CITY);
                        assertThat(invalid.role())
                                .isEqualTo(LocationRole.ARRIVAL);
                    });
        }

        @Test
        void nullsAreTreatedAsMissingRatherThanThrowingNullPointer() {
            assertThatThrownBy(() -> new EnteredLocation(null, null).check(LocationRole.STAY))
                    .isInstanceOf(InvalidLocationEntry.class);
        }
    }

    @Nested
    class TheWholeLinePastedIntoBothFields {

        @Test
        void isRejectedAgainstTheCityField() {
            assertThatThrownBy(() -> new EnteredLocation("Frankfurt (Main) Hbf", "Frankfurt (Main) Hbf")
                    .check(LocationRole.DEPARTURE))
                    .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid ->
                            assertThat(invalid.field())
                                    .isEqualTo(LocationField.CITY));
        }

        @Test
        void butAStationNamedForItsOwnTownIsNotAPaste() {
            // Gembloux is a real station named exactly for its town, and it is in the production
            // log twice. A rule rejecting "city repeats the name" was removed because of it.
            assertThatCode(() -> new EnteredLocation("Gembloux", "Gembloux").check(LocationRole.ARRIVAL))
                    .doesNotThrowAnyException();
        }

        @Test
        void norIsAnEventHeldInATownWithNoParticularVenue() {
            assertThatCode(() -> new EnteredLocation("Hamburg", "Hamburg").check(LocationRole.STAY))
                    .doesNotThrowAnyException();
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
            assertThatThrownBy(() -> new EnteredLocation("Some Station", city).check(LocationRole.DEPARTURE))
                    .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid ->
                            assertThat(invalid.field())
                                    .isEqualTo(LocationField.CITY));
        }

        @Test
        void aVenueWordInsideALongerWordDoesNotTripTheRule() {
            assertThatCode(() -> new EnteredLocation("Frankfurt (Main) Hbf", "Frankfurter Berg")
                    .check(LocationRole.DEPARTURE))
                    .doesNotThrowAnyException();
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
            assertThatCode(() -> new EnteredLocation("Some Station", city).check(LocationRole.ARRIVAL))
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
            assertThatThrownBy(() -> EnteredLocation.of("Grand Hotel", null).check(LocationRole.STAY))
                    .isInstanceOfSatisfying(InvalidLocationEntry.class, invalid ->
                            assertThat(invalid.field())
                                    .isEqualTo(LocationField.CITY));
        }
    }
}

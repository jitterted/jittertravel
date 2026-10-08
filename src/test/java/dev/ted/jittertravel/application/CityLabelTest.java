package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Places as they are stored since the location-codes migration: an ISO country code, and a US state
 * as its postal code or nothing.
 */
class CityLabelTest {

    private final CityLabel cityLabel = new CityLabel();

    @ParameterizedTest(name = "{0}, {1}, {2} → {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Denver     | CO                  | US | Denver, CO
            Atlanta    | ''                  | US | Atlanta, USA
            Vienna     | ''                  | AT | Vienna, Austria
            Toronto    | ON                  | CA | Toronto, Canada
            London     | Westminster Borough | GB | London, United Kingdom
            Steventon  | Abingdon            | GB | Steventon, United Kingdom
            Denver     | ''                  | '' | Denver
            ''         | ''                  | DE | Germany
            """)
    void label(String city, String region, String country, String expected) {
        assertThat(cityLabel.label(address(city, region, country)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}, {1}, {2} → {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Denver     | CO      | US | CO
            Atlanta    | ''      | US | USA
            Toronto    | ON      | CA | Canada
            Aachen     | NRW     | DE | Germany
            Denver     | ''      | '' | ''
            """)
    void qualifierIsWhatFollowsTheCity(String city, String region, String country, String expected) {
        assertThat(cityLabel.qualifier(address(city, region, country)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            DE      | Germany
            gb      | United Kingdom
            XX      | XX
            ''      | ''
            """)
    void aCodeReadsAsItsNameAndAnythingElseAsStored(String country, String expected) {
        assertThat(cityLabel.countryName(country))
                .isEqualTo(expected);
    }

    private static Address address(String city, String region, String country) {
        return new Address("1 Main St", city, region, "", country, "");
    }
}

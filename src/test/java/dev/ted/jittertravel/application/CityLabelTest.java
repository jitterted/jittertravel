package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CityLabelTest {

    private final CityLabel cityLabel = new CityLabel();

    @ParameterizedTest(name = "{0}, {1}, {2} → {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Denver     | CO                  | USA           | Denver, CO
            Lone Tree  | Colorado            | United States | Lone Tree, CO
            Dallas     | texas               | us            | Dallas, TX
            Atlanta    | ''                  | United States | Atlanta, USA
            Denver     | Colorad             | USA           | Denver, Colorad
            Vienna     | ''                  | Austria       | Vienna, Austria
            Toronto    | Ontario             | Canada        | Toronto, Canada
            London     | Westminster Borough | UK            | London, UK
            Denver     | ''                  | ''            | Denver
            Lone Tree  | CO                  | US            | Lone Tree, CO
            Aachen     | ''                  | DE            | Aachen, Germany
            Steventon  | Abingdon            | GB            | Steventon, United Kingdom
            Toronto    | ON                  | CA            | Toronto, Canada
            ''         | ''                  | DE            | Germany
            """)
    void ownerLabel(String city, String region, String country, String expected) {
        assertThat(cityLabel.label(address(city, region, country)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}, {1}, {2} → {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Denver     | CO                  | USA           | CO
            Atlanta    | ''                  | United States | USA
            Denver     | Colorad             | USA           | Colorad
            Toronto    | Ontario             | Canada        | Canada
            Denver     | ''                  | ''            | ''
            """)
    void qualifierIsWhatFollowsTheCity(String city, String region, String country, String expected) {
        assertThat(cityLabel.qualifier(address(city, region, country)))
                .isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            DE      | Germany
            gb      | United Kingdom
            Germany | Germany
            UK      | UK
            ''      | ''
            """)
    void aPickedCodeReadsAsItsNameAndAnythingElseAsStored(String country, String expected) {
        assertThat(cityLabel.countryName(country))
                .isEqualTo(expected);
    }

    @Test
    void publicLabelNeverPublishesAUsRegionThatIsNotAState() {
        Address neighbourhood = address("New York", "Manhattan", "USA");

        assertThat(cityLabel.publicLabel(neighbourhood))
                .isEqualTo("New York, USA");
    }

    @ParameterizedTest(name = "{0}, {1}, {2} → {3}")
    @CsvSource(delimiter = '|', textBlock = """
            Denver     | Colorado            | United States | Denver, CO
            Atlanta    | ''                  | United States | Atlanta, USA
            London     | Westminster Borough | UK            | London, UK
            """)
    void publicLabelOtherwiseMatchesTheOwnerLabel(String city, String region, String country,
                                                 String expected) {
        assertThat(cityLabel.publicLabel(address(city, region, country)))
                .isEqualTo(expected);
    }

    private static Address address(String city, String region, String country) {
        return new Address("1 Main St", city, region, "", country, "");
    }
}

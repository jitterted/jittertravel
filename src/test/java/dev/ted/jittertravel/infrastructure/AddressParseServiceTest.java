package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.infrastructure.AddressParseService.ParsedAddress;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What Parse ▶ fills in from a Nominatim answer. The country and, in the US, Canada and Australia,
 * the state arrive as codes, because those inputs are selects of codes; Nominatim's full names
 * were one half of every two-spellings pair in the log (docs/LocationDataCleanupPlan.md).
 */
class AddressParseServiceTest {

    private final AddressParseService service =
            new AddressParseService(RestClient.builder(), JsonMapper.builder().build());

    @Test
    void aUsAddressFillsTheCountryAndStateCodes() {
        String json = """
                [{"address": {
                    "house_number": "10345", "road": "Park Meadows Drive", "town": "Lone Tree",
                    "county": "Douglas County", "state": "Colorado", "ISO3166-2-lvl4": "US-CO",
                    "postcode": "80124", "country": "United States", "country_code": "us"}}]
                """;

        assertThat(service.parseNominatimResponse(json))
                .as("Parsed Lone Tree hotel")
                .hasValue(new ParsedAddress("10345 Park Meadows Drive", "Lone Tree", "CO",
                                            "80124", "US", "Lone Tree"));
    }

    @Test
    void anAustralianStateCodeOfThreeLettersIsKept() {
        String json = """
                [{"address": {"city": "Sydney", "state": "New South Wales",
                    "ISO3166-2-lvl4": "AU-NSW", "country": "Australia", "country_code": "au"}}]
                """;

        assertThat(service.parseNominatimResponse(json))
                .as("Parsed Sydney")
                .map(ParsedAddress::region)
                .hasValue("NSW");
    }

    @Test
    void elsewhereTheRegionIsTheStateNameAsBefore() {
        String json = """
                [{"address": {
                    "house_number": "1", "road": "Kolpingstraße", "village": "Johannesberg",
                    "state": "Bavaria", "ISO3166-2-lvl4": "DE-BY", "postcode": "63867",
                    "country": "Germany", "country_code": "de"}}]
                """;

        assertThat(service.parseNominatimResponse(json))
                .as("Parsed SeminarZentrum Rückersbach")
                .hasValue(new ParsedAddress("1 Kolpingstraße", "Johannesberg", "Bavaria",
                                            "63867", "DE", "Johannesberg"));
    }

    @Test
    void aUsAddressWithoutAStateCodeLeavesTheStateForTheFormToAskFor() {
        String json = """
                [{"address": {"city": "Atlanta", "state": "Georgia",
                    "country": "United States", "country_code": "us"}}]
                """;

        assertThat(service.parseNominatimResponse(json))
                .as("Parsed Atlanta with no ISO 3166-2 field")
                .map(ParsedAddress::region)
                .hasValue("");
    }

    @Test
    void aStateCodeFromAnotherCountryIsNotTaken() {
        String json = """
                [{"address": {"city": "Toronto", "ISO3166-2-lvl4": "US-CO",
                    "country": "Canada", "country_code": "ca"}}]
                """;

        assertThat(service.parseNominatimResponse(json))
                .as("Parsed Toronto carrying a mismatched code")
                .map(ParsedAddress::region)
                .hasValue("");
    }

    @Test
    void noCountryCodeLeavesTheCountryBlank() {
        String json = """
                [{"address": {"city": "Nowhere", "country": "Somewhere"}}]
                """;

        assertThat(service.parseNominatimResponse(json))
                .as("Parsed an answer with no country_code")
                .map(ParsedAddress::country)
                .hasValue("");
    }
}

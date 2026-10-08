package dev.ted.jittertravel.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The location-codes rung: country names become ISO codes and US/CA/AU region names their postal
 * codes, every other region is left as typed, and a name it cannot map stops the climb by name.
 * Only the place fields matter to it, so the payloads here carry nothing else.
 */
class LocationCodesUpcasterTest {

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final LocationCodesUpcaster upcaster = new LocationCodesUpcaster();

    @ParameterizedTest
    @CsvSource({
            "HotelBooked, 2", "HotelChanged, 2", "TrainBooked, 2", "TrainChanged, 2",
            "GatheringPlanned, 2", "GatheringChanged, 2", "ConferencePlanned, 3",
            "GroundTransferPlanned, 1", "PrivateEventPlanned, 1"})
    void handlesEachAddressBearingTypeAtItsOwnVersionOnly(String type, int version) {
        assertThat(upcaster.canHandle(type, version))
                .as("%s leaves through this rung from v%d", type, version)
                .isTrue();
        assertThat(upcaster.canHandle(type, version - 1))
                .as("%s at v%d belongs to an earlier rung", type, version - 1)
                .isFalse();
        assertThat(upcaster.canHandle(type, version + 1))
                .as("%s at v%d is already past it", type, version + 1)
                .isFalse();
    }

    @Test
    void aTypeWithNoAddressIsNotHandled() {
        assertThat(upcaster.canHandle("FlightBooked", 2))
                .isFalse();
        assertThat(upcaster.canHandle("PrivateEventMatchingLocationChanged", 1))
                .as("it carries a locationForMatching string, no address")
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "Germany, DE",
            "'Germany ', DE",
            "United States, US",
            "USA, US",
            "UK, GB",
            "Canada, CA",
            "Brussels, BE",
            "netherlands, NL"})
    void aCountryNameBecomesItsCode(String stored, String code) {
        ObjectNode payload = hotelIn("Somewhere", "", stored);

        upcaster.upcast(payload, "HotelBooked");

        assertThat(text(payload, "address", "country"))
                .isEqualTo(code);
    }

    @Test
    void aCodeAlreadyStoredPassesThrough() {
        // Event 172, Dallas, was written as US/TX before this rung existed.
        ObjectNode payload = hotelIn("Dallas", "TX", "US");
        JsonNode before = payload.deepCopy();

        upcaster.upcast(payload, "HotelBooked");

        assertThat(payload)
                .isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({
            "United States, Colorado, CO",
            "USA, CO, CO",
            "Canada, Ontario, ON",
            "Canada, British Columbia, BC",
            "Canada, Quebec, QC"})
    void aUsOrCanadianRegionBecomesItsPostalCode(String country, String region, String code) {
        ObjectNode payload = hotelIn("Somewhere", region, country);

        upcaster.upcast(payload, "HotelBooked");

        assertThat(text(payload, "address", "region"))
                .isEqualTo(code);
    }

    @Test
    void aRegionOutsideTheUsCanadaAndAustraliaIsNeverRewritten() {
        ObjectNode payload = hotelIn("Hamburg", "Altona", "Germany");

        upcaster.upcast(payload, "HotelBooked");

        assertThat(text(payload, "address", "region"))
                .isEqualTo("Altona");
    }

    @Test
    void aBlankCountryStaysBlankForTheMigrationToFillIn() {
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {"origin": {"city": "Denver", "region": "", "country": ""},
                 "destination": {"city": "Lone Tree", "region": "Colorado", "country": "United States"}}
                """);

        upcaster.upcast(payload, "GroundTransferPlanned");

        assertThat(text(payload, "origin", "country"))
                .isEmpty();
        assertThat(text(payload, "origin", "region"))
                .isEmpty();
        assertThat(text(payload, "destination", "country"))
                .as("the other end of the same transfer is still converted")
                .isEqualTo("US");
        assertThat(text(payload, "destination", "region"))
                .isEqualTo("CO");
    }

    @Test
    void bothStationsOfATrainAreConvertedAndGainNoRegion() {
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {"departureStation": {"name": "London St Pancras", "city": "London", "country": "UK"},
                 "arrivalStation": {"name": "Bruxelles-Midi", "city": "Brussels", "country": "Belgium"}}
                """);

        upcaster.upcast(payload, "TrainBooked");

        assertThat(text(payload, "departureStation", "country"))
                .isEqualTo("GB");
        assertThat(text(payload, "arrivalStation", "country"))
                .isEqualTo("BE");
        assertThat(payload.get("departureStation").has("region"))
                .as("a station has no region, and the rung does not invent one")
                .isFalse();
    }

    @Test
    void aCountryItCannotNameFailsLoudNamingTheValue() {
        ObjectNode payload = hotelIn("Aachen", "", "Deutschland");

        assertThatThrownBy(() -> upcaster.upcast(payload, "HotelBooked"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No country code for 'Deutschland'");
    }

    @Test
    void aUsRegionItCannotNameFailsLoudNamingTheValue() {
        ObjectNode payload = hotelIn("Denver", "Colorodo", "USA");

        assertThatThrownBy(() -> upcaster.upcast(payload, "HotelBooked"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No region of US named 'Colorodo'");
    }

    @Test
    void runningItTwiceChangesNothingTheSecondTime() {
        ObjectNode payload = hotelIn("Toronto", "Ontario", "Canada");
        upcaster.upcast(payload, "HotelBooked");
        JsonNode once = payload.deepCopy();

        upcaster.upcast(payload, "HotelBooked");

        assertThat(payload)
                .isEqualTo(once);
    }

    private ObjectNode hotelIn(String city, String region, String country) {
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode address = payload.putObject("address");
        address.put("city", city);
        address.put("region", region);
        address.put("country", country);
        return payload;
    }

    private static String text(JsonNode payload, String place, String field) {
        return payload.get(place).get(field).asString();
    }
}

package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.StaticAirportCityResolver;
import dev.ted.jittertravel.infrastructure.EventJsonMapperFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The approved location corrections run by the eager migration: each named one is compare-and-set
 * against the value it was approved for, and an airport transfer end with no country is completed
 * from the airport table. Payloads arrive already upcast, so countries here are codes.
 */
class LocationDataCorrectionsTest {

    // Event 58 in production: Best Western Plus St. Raphael, city and region swapped.
    private static final UUID ST_RAPHAEL = UUID.fromString("c1598660-df8b-4139-af16-2bdc49dca1e8");

    private final JsonMapper mapper = EventJsonMapperFactory.create();
    private final LocationDataCorrections corrections =
            new LocationDataCorrections(new StaticAirportCityResolver());

    @Test
    void anApprovedCorrectionChangesEveryFieldItNames() {
        ObjectNode payload = hotel("St. Georg", "Hamburg");

        corrections.apply(ST_RAPHAEL, "HotelBooked", payload);

        assertThat(text(payload, "address", "city"))
                .isEqualTo("Hamburg");
        assertThat(text(payload, "address", "region"))
                .isEqualTo("St. Georg");
    }

    @Test
    void aCorrectionWhoseValuesHaveSinceChangedIsLeftAlone() {
        // Only one of the swap's two fields still holds its approved value: applying half a swap would
        // give city and region the same value, so neither moves.
        ObjectNode payload = hotel("St. Georg", "Altona");
        JsonNode before = payload.deepCopy();

        corrections.apply(ST_RAPHAEL, "HotelBooked", payload);

        assertThat(payload)
                .isEqualTo(before);
    }

    @Test
    void anotherEventWithTheSameValuesIsLeftAlone() {
        ObjectNode payload = hotel("St. Georg", "Hamburg");
        JsonNode before = payload.deepCopy();

        corrections.apply(UUID.fromString("00000000-0000-0000-0000-000000000058"), "HotelBooked", payload);

        assertThat(payload)
                .isEqualTo(before);
    }

    @Test
    void applyingItAgainChangesNothing() {
        ObjectNode payload = hotel("St. Georg", "Hamburg");
        corrections.apply(ST_RAPHAEL, "HotelBooked", payload);
        JsonNode once = payload.deepCopy();

        corrections.apply(ST_RAPHAEL, "HotelBooked", payload);

        assertThat(payload)
                .isEqualTo(once);
    }

    @Test
    void aTopLevelFieldIsCorrectedAlongsideTheNestedOnes() {
        // Event 112: a transfer from the misspelt station.
        ObjectNode payload = (ObjectNode) mapper.readTree("""
                {"origin": {"city": "Aschaffenberg", "region": "", "country": "DE",
                            "locationForMatching": "Aschaffenberg"},
                 "originName": "Aschaffenberg Hbf", "originAirportCode": "", "destinationAirportCode": ""}
                """);

        corrections.apply(UUID.fromString("d5ef4b77-fe2a-4c8d-a4e0-a263b4212ab8"), "GroundTransferPlanned",
                          payload);

        assertThat(text(payload, "origin", "city"))
                .isEqualTo("Aschaffenburg");
        assertThat(text(payload, "origin", "locationForMatching"))
                .isEqualTo("Aschaffenburg");
        assertThat(payload.get("originName").asString())
                .isEqualTo("Aschaffenburg Hbf");
    }

    @Test
    void anAirportEndWithNoCountryIsCompletedFromItsAirportCode() {
        // Event 162: Burleigh Falls Inn to Ottawa airport, the airport end written with a city only.
        ObjectNode payload = transfer("""
                {"city": "North Kawartha", "region": "ON", "country": "CA"}""", "",
                                      """
                {"city": "Ottawa", "region": "", "country": ""}""", "YOW");

        corrections.apply(UUID.randomUUID(), "GroundTransferPlanned", payload);

        assertThat(text(payload, "destination", "country"))
                .isEqualTo("CA");
        assertThat(text(payload, "destination", "region"))
                .isEqualTo("ON");
        assertThat(text(payload, "destination", "city"))
                .as("the city it already had is kept")
                .isEqualTo("Ottawa");
    }

    @Test
    void anEndWithoutAnAirportCodeIsNeverTouched() {
        ObjectNode payload = transfer("""
                {"city": "Denver", "region": "", "country": ""}""", "",
                                      """
                {"city": "Lone Tree", "region": "CO", "country": "US"}""", "");
        JsonNode before = payload.deepCopy();

        corrections.apply(UUID.randomUUID(), "GroundTransferPlanned", payload);

        assertThat(payload)
                .isEqualTo(before);
    }

    @Test
    void anAirportEndThatAlreadyHasACountryIsNeverTouched() {
        ObjectNode payload = transfer("""
                {"city": "Denver", "region": "", "country": "US"}""", "DEN",
                                      """
                {"city": "Lone Tree", "region": "CO", "country": "US"}""", "");
        JsonNode before = payload.deepCopy();

        corrections.apply(UUID.randomUUID(), "GroundTransferPlanned", payload);

        assertThat(payload)
                .isEqualTo(before);
    }

    @Test
    void anAirportCodeOnAnotherTypeIsNotTheAirportRule() {
        ObjectNode payload = transfer("""
                {"city": "Denver", "region": "", "country": ""}""", "DEN",
                                      """
                {"city": "Lone Tree", "region": "CO", "country": "US"}""", "");
        JsonNode before = payload.deepCopy();

        corrections.apply(UUID.randomUUID(), "GatheringPlanned", payload);

        assertThat(payload)
                .isEqualTo(before);
    }

    private ObjectNode hotel(String city, String region) {
        return (ObjectNode) mapper.readTree("""
                {"address": {"city": "%s", "region": "%s", "country": "DE"}}
                """.formatted(city, region));
    }

    private ObjectNode transfer(String origin, String originAirport, String destination, String destinationAirport) {
        return (ObjectNode) mapper.readTree("""
                {"origin": %s, "originAirportCode": "%s",
                 "destination": %s, "destinationAirportCode": "%s"}
                """.formatted(origin, originAirport, destination, destinationAirport));
    }

    private static String text(JsonNode payload, String place, String field) {
        return payload.get(place).get(field).asString();
    }
}

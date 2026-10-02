package dev.ted.jittertravel.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BookFlightItineraryRequestTest {

    @Test
    void aFreshRequestHasAnEmptyPasteAndNoZonePicks() {
        BookFlightItineraryRequest request = new BookFlightItineraryRequest();

        assertThat(request.getPasted())
                .isEmpty();
        assertThat(request.getAirportZones())
                .isEmpty();
        assertThat(request.getItineraryId())
                .isNull();
    }

    @Test
    void whatWasBoundIsWhatIsRead() {
        BookFlightItineraryRequest request = new BookFlightItineraryRequest();
        request.setItineraryId("abc");
        request.setPasted("text");
        request.setAirportZones(Map.of("YQB", "CANADA_EASTERN"));

        assertThat(request.getItineraryId())
                .isEqualTo("abc");
        assertThat(request.getPasted())
                .isEqualTo("text");
        assertThat(request.getAirportZones())
                .isEqualTo(Map.of("YQB", "CANADA_EASTERN"));
    }

    @Test
    void aNullPasteOrNullPicksBecomeEmptyNotNull() {
        BookFlightItineraryRequest request = new BookFlightItineraryRequest();
        request.setPasted(null);
        request.setAirportZones(null);

        assertThat(request.getPasted())
                .isEmpty();
        assertThat(request.getAirportZones())
                .isEmpty();
    }

    /**
     * {@code CommandExecutor} puts the request's {@code toString} into a read-only refusal's message,
     * and so into the logs. The pasted email holds an eTicket number, a frequent-flyer number and a
     * legal name; none of it may go there.
     */
    @Test
    void itsStringFormLeavesThePastedEmailOut() {
        BookFlightItineraryRequest request = new BookFlightItineraryRequest();
        request.setItineraryId("99999999-9999-9999-9999-999999999999");
        request.setPasted("eTicket number: 0000000000000\nTRAVELER/SAMPLE");

        assertThat(request.toString())
                .contains("99999999-9999-9999-9999-999999999999")
                .doesNotContain("0000000000000")
                .doesNotContain("TRAVELER/SAMPLE");
    }
}

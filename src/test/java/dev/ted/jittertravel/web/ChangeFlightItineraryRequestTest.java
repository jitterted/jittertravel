package dev.ted.jittertravel.web;

import dev.ted.jittertravel.domain.ItineraryChangeKind;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeFlightItineraryRequestTest {

    /** The paste holds an eTicket number and a legal name, and this object reaches the logs. */
    @Test
    void itsTextLeavesThePasteOut() {
        ChangeFlightItineraryRequest request = new ChangeFlightItineraryRequest();
        request.setKind("REBOOKING");
        request.setPasted("eTicket number: 0000000000000");

        assertThat(request.toString())
                .isEqualTo("ChangeFlightItineraryRequest {kind='REBOOKING', pasted=<29 chars, not shown>, airportZones={}}")
                .doesNotContain("0000000000000");
    }

    @Test
    void aMissingValueBindsAsEmptyNeverNull() {
        ChangeFlightItineraryRequest request = new ChangeFlightItineraryRequest();
        request.setKind(null);
        request.setPasted(null);
        request.setAirportZones(null);

        assertThat(request.getKind())
                .isEmpty();
        assertThat(request.getPasted())
                .isEmpty();
        assertThat(request.getAirportZones())
                .isEmpty();
        assertThat(request.kindPicked())
                .isNull();
    }

    @Test
    void eachRadioValueIsItsKind() {
        ChangeFlightItineraryRequest request = new ChangeFlightItineraryRequest();
        request.setAirportZones(Map.of("YQB", "CANADA_EASTERN"));

        request.setKind("SCHEDULE_CHANGE");
        assertThat(request.kindPicked())
                .isEqualTo(ItineraryChangeKind.SCHEDULE_CHANGE);
        request.setKind("REBOOKING");
        assertThat(request.kindPicked())
                .isEqualTo(ItineraryChangeKind.REBOOKING);
        assertThat(request.getAirportZones())
                .containsEntry("YQB", "CANADA_EASTERN");
    }
}

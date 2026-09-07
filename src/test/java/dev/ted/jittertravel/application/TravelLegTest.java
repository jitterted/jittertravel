package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.FlightId;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.TrainTripId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A leg's two strings answer different questions, and only one of them is unique.
 */
class TravelLegTest {

    @Test
    void twoTransfersShareAPageButNotAnIdentity() {
        // Why ProblemKey and the leg sort use identity(). Every ground transfer's page is
        // /itinerary, so keying a problem on the path made every transfer interchangeable: two
        // different overlaps got one key, and a fix link resolved to whichever came first.
        TravelLeg first = new TravelLeg.Transfer(GroundTransferId.random(), "Hotel → Hbf");
        TravelLeg second = new TravelLeg.Transfer(GroundTransferId.random(), "Hotel → Hbf");

        assertThat(first.detailsPath())
                .isEqualTo(second.detailsPath())
                .isEqualTo("/itinerary");
        assertThat(first.identity())
                .isNotEqualTo(second.identity());
    }

    @Test
    void identityNamesTheKindSoTwoKindsCannotShareOne() {
        // The kind prefix, not decoration: the three id types are all UUIDs, and nothing stops a
        // flight and a train being minted with the same one.
        UUID shared = UUID.randomUUID();

        assertThat(new TravelLeg.Flight(FlightId.of(shared), "LH 402").identity())
                .isEqualTo("flight:" + shared)
                .isNotEqualTo(new TravelLeg.Train(TrainTripId.of(shared), "ICE 597").identity());
    }
}

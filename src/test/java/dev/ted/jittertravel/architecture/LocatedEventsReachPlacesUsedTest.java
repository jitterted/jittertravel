package dev.ted.jittertravel.architecture;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.TrainStationAddress;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guard: every event that names a country must reach {@code PlacesUsedProjector}, or a
 * country Ted has been to never joins the short list the address forms offer first, and he goes
 * looking for it in "Another country…" every time (docs/LocationDataCleanupPlan.md D1).
 * <p>
 * Airports are not a location type here: an airport code says nothing the form's country picker
 * can use, and the transfer that leaves one carries a full address of its own.
 */
class LocatedEventsReachPlacesUsedTest {

    private static final Path PROJECTOR =
            Path.of("src/main/java/dev/ted/jittertravel/application/PlacesUsedProjector.java");

    private final LocatedEvents locatedEvents =
            new LocatedEvents(Set.of(Address.class, TrainStationAddress.class));

    @Test
    void everyEventCarryingAnAddressOrAStationIsHandledByTheProjector() {
        assertThat(locatedEvents.notHandledIn(PROJECTOR))
                .as("These events name a country but never reach PlacesUsedProjector. "
                    + "Add a case to its switch that records each address or station.")
                .isEmpty();
    }

    @Test
    void theScanFindsTheEventsItIsMeantToCover() {
        assertThat(locatedEvents.classes())
                .extracting(Class::getSimpleName)
                .contains("HotelBooked", "TrainBooked", "GroundTransferPlanned",
                          "ConferencePlanned", "GatheringChanged", "PrivateEventPlanned");
    }
}

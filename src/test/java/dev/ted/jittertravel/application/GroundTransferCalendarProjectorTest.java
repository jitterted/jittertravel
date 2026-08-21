package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.GroundTransferId;
import dev.ted.jittertravel.domain.GroundTransferPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.infrastructure.StoredEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GroundTransferCalendarProjectorTest {

    private static final ZoneId DENVER = ZoneId.of("America/Denver");
    private static final ZonedTimestamp DEPARTS = at(12, 0);
    private static final ZonedTimestamp ARRIVES = at(12, 45);
    private static final Address AIRPORT = new Address("", "Denver", "", "", "", "Denver");
    private static final Address HOTEL = new Address("10345 Park Meadows Dr", "Lone Tree", "CO",
                                                     "80124", "US", "Lone Tree");

    private final GroundTransferCalendarProjector projector = new GroundTransferCalendarProjector();

    @Test
    void anAirportToHotelTransferBecomesOneEntryOnTheTransferDay() {
        projector.handle(Stream.of(stored(airportToHotel())));

        assertThat(projector.entries())
                .singleElement()
                .extracting(CalendarEntry::kind, CalendarEntry::start, CalendarEntry::end)
                .containsExactly(EntryKind.GROUND_TRANSFER,
                                 LocalDateTime.of(2026, 9, 14, 12, 0),
                                 LocalDateTime.of(2026, 9, 14, 12, 45));
    }

    @Test
    void theOwnerTitleLeadsWithTheTaxiAndNamesBothEndsAsTedKnowsThem() {
        projector.handle(Stream.of(stored(airportToHotel())));

        assertThat(projector.entries().getFirst().mainTitle())
                .as("the taxi leads the title as the plane leads a flight's")
                .isEqualTo("\uD83D\uDE95 DEN → Marriott Lone Tree");
    }

    /**
     * The owner's whole subtitle is the times. Naming the same journey a second time as cities was
     * noise on the entry (Ted, 2026-08-20) — the title already says where it goes.
     */
    @Test
    void theOwnerSubtitleIsJustTheTimes() {
        projector.handle(Stream.of(stored(airportToHotel())));

        assertThat(projector.entries().getFirst().subTitle())
                .containsExactly(new SubtitleLine.Range(DEPARTS, ARRIVES));
    }

    /**
     * The publishable route still has to exist, because {@code CalendarEntryRedactor} cannot derive
     * a city from a hotel name — but it rides in a field no renderer reads, so the owner never sees
     * it. The hotel's name must not appear in it.
     */
    @Test
    void thePublishableRouteIsCarriedSeparatelyAndNamesNoHotel() {
        projector.handle(Stream.of(stored(airportToHotel())));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.publicRoute())
                .as("the icon leads the title only; a subtitle route carries none, as on a flight")
                .isEqualTo("DEN → Lone Tree, CO, US");
        assertThat(entry.publicRoute())
                .doesNotContain("Marriott Lone Tree");
        assertThat(entry.subTitle())
                .as("and it is not in the owner's subtitle, which is what Ted actually reads")
                .doesNotContain(new SubtitleLine.Text("DEN → Lone Tree, CO, US"));
    }

    @Test
    void aHotelToAirportTransferReadsTheOtherWayRound() {
        projector.handle(Stream.of(stored(new GroundTransferPlanned(GroundTransferId.random(),
                "", "Marriott Lone Tree", HOTEL,
                "DEN", "", AIRPORT,
                DEPARTS, ARRIVES))));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mainTitle())
                .isEqualTo("\uD83D\uDE95 Marriott Lone Tree → DEN");
        assertThat(entry.publicRoute())
                .isEqualTo("Lone Tree, CO, US → DEN");
    }

    @Test
    void aTransferCarriesNoMapsUrlNoEditLinkAndNoContinuation() {
        projector.handle(Stream.of(stored(airportToHotel())));

        CalendarEntry entry = projector.entries().getFirst();
        assertThat(entry.mapsUrl()).isNull();
        assertThat(entry.editPath()).isNull();
        assertThat(entry.continuationTitle()).isNull();
        assertThat(entry.continuationSubTitle()).isNull();
    }

    /**
     * A transfer has nothing to edit — correcting one means removing it and entering it again — so
     * the owner action the calendar entry carries is a cancel, keyed to this transfer.
     */
    @Test
    void theOwnerActionIsACancelLinkForThisTransfer() {
        GroundTransferId transferId = GroundTransferId.of(
                UUID.fromString("11111111-2222-3333-4444-555555555555"));

        projector.handle(Stream.of(stored(new GroundTransferPlanned(transferId,
                "DEN", "", AIRPORT,
                "", "Marriott Lone Tree", HOTEL,
                DEPARTS, ARRIVES))));

        assertThat(projector.entries().getFirst().cancelPath())
                .isEqualTo("/ground-transfers/11111111-2222-3333-4444-555555555555/cancel");
    }

    private static GroundTransferPlanned airportToHotel() {
        return new GroundTransferPlanned(GroundTransferId.random(),
                "DEN", "", AIRPORT,
                "", "Marriott Lone Tree", HOTEL,
                DEPARTS, ARRIVES);
    }

    private static ZonedTimestamp at(int hour, int minute) {
        return ZonedTimestamp.fromLocal(LocalDateTime.of(2026, 9, 14, hour, minute), DENVER);
    }

    private static StoredEvent stored(Event event) {
        return new StoredEvent(1, event.getClass(), UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"), event, UUID.randomUUID());
    }
}

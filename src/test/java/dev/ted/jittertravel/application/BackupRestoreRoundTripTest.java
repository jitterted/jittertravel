package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupCommandRow;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupEventRow;
import dev.ted.jittertravel.web.BookFlightRequest;
import dev.ted.jittertravel.web.BookHotelRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property the old command-replay round trip could not assert: a restored {@code event_log} is
 * <em>byte-for-byte identical</em> to the original — same sequence, event id, timestamp and payload
 * — because events are restored verbatim rather than recomputed. Seeds real events through the live
 * path, manufactures a command of every non-SUCCEEDED status, backs up, wipes, restores, and
 * compares the whole command and event logs.
 */
@SpringBootTest
class BackupRestoreRoundTripTest extends AbstractTestcontainerIntegrationTest {

    private static final LocalDate FUTURE = LocalDate.now().plusMonths(3);

    @Autowired FlightBooking flightBooking;
    @Autowired HotelBooking hotelBooking;
    @Autowired BackupService backupService;
    @Autowired PostgresPersister persister;

    @Test
    void eventLogAndEveryCommandStatusRoundTripVerbatim() {
        persister.truncateAllTables();

        // live path → real SUCCEEDED commands, each with its events
        flightBooking.bookFlight(bookFlight(UUID.randomUUID().toString()), Instant.now());
        hotelBooking.bookHotel(bookHotel(UUID.randomUUID().toString()), Instant.now());

        // one command of every non-SUCCEEDED status, so "all statuses" is exercised
        markStatus("FAILED_DOMAIN");
        markStatus("FAILED_PERSIST");
        UUID pending = UUID.randomUUID();
        persister.saveCommand(pending, bookFlight(UUID.randomUUID().toString()));   // left PENDING
        UUID abandoned = UUID.randomUUID();
        persister.saveCommand(abandoned, bookFlight(UUID.randomUUID().toString()));
        persister.abandonCommand(abandoned);

        List<BackupEventRow> eventsBefore = persister.findAllEventsForBackup();
        List<BackupCommandRow> commandsBefore = persister.findAllCommandsForBackup();
        assertThat(eventsBefore)
                .as("sanity: the live path produced events to round-trip")
                .isNotEmpty();
        assertThat(commandsBefore)
                .extracting(BackupCommandRow::status)
                .as("sanity: all five statuses are present to prove they round-trip")
                .contains("SUCCEEDED", "FAILED_DOMAIN", "FAILED_PERSIST", "PENDING", "ABANDONED");

        String backup = backupService.backupJson(OffsetDateTime.parse("2026-08-11T14:30:00Z"), "local");

        persister.truncateAllTables();
        assertThat(persister.findAllEventsForBackup())
                .as("database cleared before restore")
                .isEmpty();

        BackupService.RestoreResult result = backupService.restoreJson(backup);

        assertThat(result.hasErrors())
                .as("restore errors: %s", result.errors())
                .isFalse();
        assertThat(result.restoredCommands()).isEqualTo(commandsBefore.size());
        assertThat(result.restoredEvents()).isEqualTo(eventsBefore.size());
        assertThat(persister.findAllEventsForBackup())
                .as("event_log restored byte-for-byte: same sequence, event_id, timestamp, payload")
                .isEqualTo(eventsBefore);
        assertThat(persister.findAllCommandsForBackup())
                .as("command_log restored verbatim across all statuses, with event_ids/status/error")
                .isEqualTo(commandsBefore);
    }

    private void markStatus(String status) {
        UUID commandId = UUID.randomUUID();
        persister.saveCommand(commandId, bookFlight(UUID.randomUUID().toString()));
        persister.markCommandFailed(commandId, status, status + " for round-trip");
    }

    private static BookFlightRequest bookFlight(String flightId) {
        BookFlightRequest r = new BookFlightRequest();
        r.setFlightId(flightId);
        r.setAirline("United");
        r.setFlightNumber("UA59");
        r.setDepartureAirport("SFO");
        r.setDepartureDateTime(FUTURE.atTime(9, 0));
        r.setArrivalAirport("FRA");
        r.setArrivalDateTime(FUTURE.plusDays(1).atTime(9, 45));
        return r;
    }

    private static BookHotelRequest bookHotel(String hotelBookingId) {
        BookHotelRequest r = new BookHotelRequest();
        r.setHotelBookingId(hotelBookingId);
        r.setHotelName("Marriott Downtown");
        r.setStreet("742 Evergreen Terrace");
        r.setCity("San Francisco");
        r.setRegion("CA");
        r.setCountry("USA");
        r.setPostalCode("94103");
        r.setLocationForMatching("San Francisco");
        r.setMapsUrl("");
        r.setCheckIn(FUTURE.atTime(15, 0));
        r.setCheckOut(FUTURE.plusDays(2).atTime(11, 0));
        r.setBookingIntent(BookingIntent.FINAL);
        return r;
    }
}

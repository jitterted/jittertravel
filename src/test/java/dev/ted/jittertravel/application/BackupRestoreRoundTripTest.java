package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.EventJsonMapperFactory;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupCommandRow;
import dev.ted.jittertravel.infrastructure.PostgresPersister.BackupEventRow;
import dev.ted.jittertravel.web.BookFlightRequest;
import dev.ted.jittertravel.web.BookHotelRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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

    @Test
    void restoresAPreStampVersion2BackupLeavingSchemaVersionNull() {
        persister.truncateAllTables();
        flightBooking.bookFlight(bookFlight(UUID.randomUUID().toString()), Instant.now());

        // Simulate an old backup file taken before the schemaVersion stamp existed: version 2, and no
        // schemaVersion key on any event. New code must still restore it (the stamp is simply absent).
        String v3Backup = backupService.backupJson(OffsetDateTime.parse("2026-08-11T14:30:00Z"), "local");
        String v2Backup = downgradeToPreStampVersion2(v3Backup);

        persister.truncateAllTables();
        BackupService.RestoreResult result = backupService.restoreJson(v2Backup);

        assertThat(result.hasErrors())
                .as("restore errors: %s", result.errors())
                .isFalse();
        assertThat(persister.findAllEventsForBackup())
                .as("a pre-stamp backup restores with a null schema_version, exactly like a legacy row")
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.schemaVersion()).isNull());
    }

    private String downgradeToPreStampVersion2(String v3Backup) {
        JsonMapper mapper = EventJsonMapperFactory.create();
        ObjectNode root = (ObjectNode) mapper.readTree(v3Backup);
        root.put("version", 2);
        for (JsonNode event : root.get("events")) {
            ((ObjectNode) event).remove("schemaVersion");
        }
        return root.toString();
    }

    private void markStatus(String status) {
        UUID commandId = UUID.randomUUID();
        persister.saveCommand(commandId, bookFlight(UUID.randomUUID().toString()));
        persister.markCommandFailed(commandId, status, status + " for round-trip");
    }

    /**
     * Every booking gets its <strong>own</strong> window, derived from its own id. This was
     * load-bearing until 2026-09-12 and is now belt-and-braces; it stays because it costs nothing.
     * <p>
     * It was written when nothing returned {@code EventStore}'s in-memory event list to a known
     * state: the list is filled at boot and only ever appended to, while the {@code @Sql}
     * truncation clears the database alone. So a flight booked by one method stayed visible to the
     * next, and — because the container is {@code withReuse(true)} and the next run replays the
     * database at boot — to the next <em>run</em> as well. Under the overlapping-leg rule
     * (2026-09-06) either one refuses the booking, and a fixed window failed roughly half the time.
     * Deriving the offset from the flight id makes every booking unique, so neither a sibling
     * method nor a replayed leg from a previous run can occupy the same window.
     * <p>
     * What actually keeps this test green now is
     * {@code AbstractTestcontainerIntegrationTest.returnTheEventStoreToAKnownState()}, which
     * reloads the store and asserts it is empty before every method — see "Test isolation: the
     * test half is enforced, the production half is not" in {@code docs/Cleanup_Tasks.md}.
     */
    private static BookFlightRequest bookFlight(String flightId) {
        return bookFlight(flightId, Math.floorMod(flightId.hashCode(), 3000));
    }

    private static BookFlightRequest bookFlight(String flightId, int dayOffset) {
        BookFlightRequest r = new BookFlightRequest();
        r.setFlightId(flightId);
        r.setAirline("United");
        r.setFlightNumber("UA59");
        r.setDepartureAirport("SFO");
        r.setDepartureDateTime(FUTURE.plusDays(dayOffset).atTime(9, 0));
        r.setArrivalAirport("FRA");
        r.setArrivalDateTime(FUTURE.plusDays(dayOffset + 1).atTime(9, 45));
        return r;
    }

    private static BookHotelRequest bookHotel(String hotelBookingId) {
        return new BookHotelRequest(
                hotelBookingId, "Marriott Downtown",
                "742 Evergreen Terrace", "San Francisco", "CA", "USA", "94103",
                "San Francisco", "", null,
                FUTURE.atTime(15, 0), FUTURE.plusDays(2).atTime(11, 0), null,
                BookingIntent.FINAL);
    }
}

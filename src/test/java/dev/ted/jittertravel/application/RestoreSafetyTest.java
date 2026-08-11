package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.BookHotelRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Restore must be validate-then-apply: a backup with a bad event writes <em>nothing</em> and reports
 * every problem at once, and a referential problem is caught in pass one rather than exploding as a
 * foreign-key error mid-write. Re-running a partly-applied file resumes by skipping rows already
 * present. Together those make "wipe and restore" safe and re-runnable.
 */
@SpringBootTest
class RestoreSafetyTest extends AbstractTestcontainerIntegrationTest {

    private static final LocalDate FUTURE = LocalDate.now().plusMonths(3);

    @Autowired BackupService backupService;
    @Autowired HotelBooking hotelBooking;
    @Autowired PostgresPersister persister;

    @Test
    void eventWithUnbindablePayloadAbortsRestoreBeforeAnythingIsWritten() {
        String file = """
                {
                  "version": 2,
                  "commands": [
                    {"commandId": "11111111-1111-1111-1111-111111111111",
                     "timestamp": "2026-07-01T14:00:00Z", "type": "SomeCommand",
                     "payload": {}, "eventIds": ["22222222-2222-2222-2222-222222222222"],
                     "status": "SUCCEEDED", "error": null}
                  ],
                  "events": [
                    {"sequence": 999001, "eventId": "22222222-2222-2222-2222-222222222222",
                     "commandId": "11111111-1111-1111-1111-111111111111",
                     "timestamp": "2026-07-01T14:00:00.100Z", "type": "NoSuchEvent", "payload": {}}
                  ]
                }
                """;
        int commandsBefore = persister.countCommands("");
        int eventsBefore = persister.countEvents();

        BackupService.RestoreResult result = backupService.restoreJson(file);

        assertThat(result.errors())
                .as("the un-bindable event is reported")
                .anyMatch(e -> e.contains("NoSuchEvent"));
        assertThat(result.restoredCommands()).isZero();
        assertThat(result.restoredEvents()).isZero();
        assertThat(persister.countCommands(""))
                .as("a failed validation writes no commands")
                .isEqualTo(commandsBefore);
        assertThat(persister.countEvents())
                .as("a failed validation writes no events")
                .isEqualTo(eventsBefore);
    }

    @Test
    void eventReferencingAnAbsentCommandIsRejectedInPassOneNotAsAnFkError() {
        // The event's commandId is not in commands. Caught in pass one, so the event_log→command_log
        // FK is never given the chance to fail mid-restore.
        String file = """
                {
                  "version": 2,
                  "commands": [],
                  "events": [
                    {"sequence": 999002, "eventId": "22222222-2222-2222-2222-222222222222",
                     "commandId": "99999999-9999-9999-9999-999999999999",
                     "timestamp": "2026-07-01T14:00:00.100Z", "type": "NoSuchEvent", "payload": {}}
                  ]
                }
                """;
        int eventsBefore = persister.countEvents();

        BackupService.RestoreResult result = backupService.restoreJson(file);

        assertThat(result.errors())
                .as("the dangling command reference is reported, not thrown as an FK violation")
                .anyMatch(e -> e.contains("not in the backup"));
        assertThat(persister.countEvents())
                .isEqualTo(eventsBefore);
    }

    @Test
    void wrongOrMissingVersionIsRejected() {
        // A v1 body is structurally a clean {commands, events} document — only the version guard
        // stops it, so this is what proves the guard (not an incidental parse failure) does the work.
        assertThat(backupService.restoreJson("{\"version\": 1, \"commands\": [], \"events\": []}").errors())
                .as("an old command-only (v1) backup is rejected")
                .anyMatch(e -> e.contains("version"));
        assertThat(backupService.restoreJson("{\"commands\": [], \"events\": []}").errors())
                .as("a file with no version is rejected")
                .anyMatch(e -> e.contains("version"));
    }

    @Test
    void unparseableJsonIsReportedRatherThanThrowing() {
        BackupService.RestoreResult result = backupService.restoreJson("{ not json");

        assertThat(result.errors())
                .singleElement()
                .satisfies(e -> assertThat(e).contains("Failed to parse JSON"));
    }

    @Test
    void dryRunReportsProblemsWithoutWriting() {
        String file = """
                {
                  "version": 2,
                  "commands": [
                    {"commandId": "11111111-1111-1111-1111-111111111111",
                     "timestamp": "2026-07-01T14:00:00Z", "type": "SomeCommand",
                     "payload": {}, "eventIds": ["22222222-2222-2222-2222-222222222222"],
                     "status": "SUCCEEDED", "error": null}
                  ],
                  "events": [
                    {"sequence": 999003, "eventId": "22222222-2222-2222-2222-222222222222",
                     "commandId": "11111111-1111-1111-1111-111111111111",
                     "timestamp": "2026-07-01T14:00:00.100Z", "type": "NoSuchEvent", "payload": {}}
                  ]
                }
                """;
        int eventsBefore = persister.countEvents();

        BackupService.ValidationReport report = backupService.validateJson(file);

        assertThat(report.hasErrors()).isTrue();
        assertThat(persister.countEvents())
                .as("a dry run writes nothing")
                .isEqualTo(eventsBefore);
    }

    @Test
    void rerunningRestoreResumesBySkippingRowsAlreadyPresent() {
        persister.truncateAllTables();
        hotelBooking.bookHotel(bookHotel(UUID.randomUUID().toString()), Instant.now());
        String backup = backupService.backupJson(OffsetDateTime.parse("2026-08-11T14:30:00Z"), "local");
        int commandCount = persister.countCommands("");
        int eventCount = persister.countEvents();
        persister.truncateAllTables();

        BackupService.RestoreResult first = backupService.restoreJson(backup);
        BackupService.RestoreResult second = backupService.restoreJson(backup);

        assertThat(first.hasErrors())
                .as("first restore errors: %s", first.errors())
                .isFalse();
        assertThat(first.restoredCommands()).isEqualTo(commandCount);
        assertThat(first.restoredEvents()).isEqualTo(eventCount);

        assertThat(second.hasErrors())
                .as("re-running the same file must not collide: %s", second.errors())
                .isFalse();
        assertThat(second.restoredCommands()).isZero();
        assertThat(second.skippedCommands()).isEqualTo(commandCount);
        assertThat(second.restoredEvents()).isZero();
        assertThat(second.skippedEvents()).isEqualTo(eventCount);
        assertThat(persister.countEvents())
                .as("skipped rows are not duplicated")
                .isEqualTo(eventCount);
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

package dev.ted.jittertravel.application;

import dev.ted.jittertravel.domain.BookingIntent;
import dev.ted.jittertravel.infrastructure.AbstractTestcontainerIntegrationTest;
import dev.ted.jittertravel.infrastructure.PostgresPersister;
import dev.ted.jittertravel.web.BookHotelRequest;
import dev.ted.jittertravel.web.ImportableCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import must be all-or-nothing and re-runnable: a backup with a bad entry (an address whose time
 * zone doesn't resolve, say) must write <em>nothing</em>, and re-running a file must skip what is
 * already in the log rather than colliding on the command id. Together those turn "wipe the
 * database and start over" into "fix the file and run it again".
 */
@SpringBootTest
class CommandImportSafetyTest extends AbstractTestcontainerIntegrationTest {

    private static final LocalDate FUTURE = LocalDate.now().plusMonths(3);

    @Autowired CommandImporter commandImporter;
    @Autowired PostgresPersister persister;
    @Autowired JsonMapper jsonMapper;

    @Test
    void entryWithUnresolvableZoneAbortsImportBeforeAnythingIsWritten() {
        UUID goodId = UUID.randomUUID();
        UUID badId = UUID.randomUUID();
        String file = importFileOf(new Entry("BookHotel", bookHotel(goodId, "San Francisco", "USA")),
                                   new Entry("BookHotel", bookHotel(badId, "Springfield", "Atlantis")));

        CommandImporter.ImportResult result = commandImporter.importJson(file);

        assertThat(result.errors())
                .hasSize(1);
        assertThat(result.errors().getFirst())
                .contains("Entry 1 (BookHotel)");
        assertThat(result.importedCount())
                .isZero();
        assertThat(persister.existingCommandIds(List.of(goodId, badId)))
                .as("the valid entry preceding the bad one must not have been written")
                .isEmpty();
    }

    @Test
    void everyBadEntryIsReportedInOneRun() {
        String file = importFileOf(new Entry("BookHotel", bookHotel(UUID.randomUUID(), "Springfield", "Atlantis")),
                                   new Entry("NoSuchCommand", bookHotel(UUID.randomUUID(), "San Francisco", "USA")),
                                   new Entry("BookHotel", bookHotel(UUID.randomUUID(), "Gotham", "Ruritania")));

        CommandImporter.ImportResult result = commandImporter.importJson(file);

        assertThat(result.errors())
                .as("one editing round should be able to fix the whole file")
                .hasSize(3);
    }

    @Test
    void dryRunReportsEveryProblemWithoutWritingAnything() {
        UUID goodId = UUID.randomUUID();
        UUID badId = UUID.randomUUID();
        String file = importFileOf(new Entry("BookHotel", bookHotel(goodId, "San Francisco", "USA")),
                                   new Entry("BookHotel", bookHotel(badId, "Springfield", "Atlantis")));

        CommandImporter.ValidationReport report = commandImporter.validateJson(file);

        assertThat(report.errors())
                .hasSize(1);
        assertThat(report.errors().getFirst())
                .as("a dry run must name the offending entry the same way a real import does")
                .contains("Entry 1 (BookHotel)");
        assertThat(report.validCount())
                .as("the entries that would import are counted")
                .isEqualTo(1);
        assertThat(persister.existingCommandIds(List.of(goodId, badId)))
                .isEmpty();
    }

    @Test
    void dryRunOfACleanFileWritesNothingEither() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String file = importFileOf(new Entry("BookHotel", bookHotel(firstId, "San Francisco", "USA")),
                                   new Entry("BookHotel", bookHotel(secondId, "London", "UK")));

        CommandImporter.ValidationReport report = commandImporter.validateJson(file);

        assertThat(report.hasErrors())
                .as("a clean file reports no problems: %s", report.errors())
                .isFalse();
        assertThat(report.validCount())
                .isEqualTo(2);
        assertThat(persister.countEvents())
                .as("validating a file that *would* import must still write nothing — that is the "
                    + "whole point of a dry run")
                .isZero();
        assertThat(persister.existingCommandIds(List.of(firstId, secondId)))
                .isEmpty();
    }

    @Test
    void dryRunReportsUnparseableJsonRatherThanThrowing() {
        CommandImporter.ValidationReport report = commandImporter.validateJson("{ not json");

        assertThat(report.errors())
                .hasSize(1);
        assertThat(report.errors().getFirst())
                .contains("Failed to parse JSON");
    }

    @Test
    void duplicateCommandIdsWithinTheFileAreRejected() {
        UUID reusedId = UUID.randomUUID();
        String file = importFileOf(new Entry("BookHotel", bookHotel(reusedId, "San Francisco", "USA")),
                                   new Entry("BookHotel", bookHotel(reusedId, "London", "UK")));

        CommandImporter.ImportResult result = commandImporter.importJson(file);

        assertThat(result.errors())
                .hasSize(1);
        assertThat(result.errors().getFirst())
                .contains("Entry 1 (BookHotel)", "already used by entry 0");
        assertThat(persister.existingCommandIds(List.of(reusedId)))
                .isEmpty();
    }

    @Test
    void rerunningTheSameFileSkipsAlreadyImportedCommands() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String file = importFileOf(new Entry("BookHotel", bookHotel(firstId, "San Francisco", "USA")),
                                   new Entry("BookHotel", bookHotel(secondId, "London", "UK")));

        CommandImporter.ImportResult first = commandImporter.importJson(file);
        int eventsAfterFirstRun = persister.countEvents();
        CommandImporter.ImportResult second = commandImporter.importJson(file);

        assertThat(first.importedCount())
                .isEqualTo(2);
        assertThat(second.hasErrors())
                .as("re-running a file must not collide on the command id: %s", second.errors())
                .isFalse();
        assertThat(second.importedCount())
                .isZero();
        assertThat(second.skippedCount())
                .isEqualTo(2);
        assertThat(persister.countEvents())
                .as("skipped commands must not re-append their events")
                .isEqualTo(eventsAfterFirstRun);
    }

    @Test
    void fixedFileResumesImportingOnlyTheMissingCommands() {
        UUID goodId = UUID.randomUUID();
        UUID fixedId = UUID.randomUUID();
        commandImporter.importJson(importFileOf(new Entry("BookHotel", bookHotel(goodId, "San Francisco", "USA"))));

        CommandImporter.ImportResult result = commandImporter.importJson(
                importFileOf(new Entry("BookHotel", bookHotel(goodId, "San Francisco", "USA")),
                             new Entry("BookHotel", bookHotel(fixedId, "London", "UK"))));

        assertThat(result.importedCount())
                .isEqualTo(1);
        assertThat(result.skippedCount())
                .isEqualTo(1);
        assertThat(persister.existingCommandIds(List.of(goodId, fixedId)))
                .containsExactlyInAnyOrder(goodId, fixedId);
    }

    private record Entry(String type, ImportableCommand payload) {}

    /** The same shape {@code exportJson} writes: a list of {@code {type, payload}} objects. */
    private String importFileOf(Entry... entries) {
        List<Map<String, Object>> file = Arrays.stream(entries)
                .map(entry -> Map.<String, Object>of("type", entry.type(), "payload", entry.payload()))
                .toList();
        return jsonMapper.writeValueAsString(file);
    }

    private static BookHotelRequest bookHotel(UUID hotelBookingId, String city, String country) {
        BookHotelRequest request = new BookHotelRequest();
        request.setHotelBookingId(hotelBookingId.toString());
        request.setHotelName("Marriott Downtown");
        request.setStreet("742 Evergreen Terrace");
        request.setCity(city);
        request.setRegion("");
        request.setCountry(country);
        request.setPostalCode("94103");
        request.setLocationForMatching(city);
        request.setMapsUrl("");
        request.setCheckIn(FUTURE.atTime(15, 0));
        request.setCheckOut(FUTURE.plusDays(2).atTime(11, 0));
        request.setBookingIntent(BookingIntent.FINAL);
        return request;
    }
}

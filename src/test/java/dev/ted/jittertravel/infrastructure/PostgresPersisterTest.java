package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.application.AirportZoneResolver;
import dev.ted.jittertravel.application.LocationZoneResolver;
import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferenceTentativelyPlanned;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.PlanTentativeConferenceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresPersister.class)
class PostgresPersisterTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private PostgresPersister persister;

    @TestConfiguration
    static class TestConfig {
        // needed for the instantiation of the PostgresPersister; use the pinned production config
        @Bean
        JsonMapper jsonMapper() {
            return EventJsonMapperFactory.create();
        }

        @Bean
        EventPayloadUpcaster eventPayloadUpcaster(JsonMapper jsonMapper) {
            return new EventPayloadUpcaster(new LocationZoneResolver(),
                    new AirportZoneResolver(), jsonMapper);
        }
    }

    @Test
    void loadTimelinePageReflectsCommandStatus() {
        // command #1 — succeeds, produces one event
        UUID cmd1 = UUID.randomUUID();
        PlanTentativeConferenceRequest req1 = newRequest(cmd1, "Conf One");
        persister.saveCommand(cmd1, req1);
        persister.appendEvents(List.of(storedEvent(1L, cmd1, "Conf One", req1)), cmd1);

        // command #2 — domain failure: saved, marked failed, no events
        UUID cmd2 = UUID.randomUUID();
        PlanTentativeConferenceRequest req2 = newRequest(cmd2, "Conf Two (failed)");
        persister.saveCommand(cmd2, req2);
        persister.markCommandFailed(cmd2, "FAILED_DOMAIN", "rejected by domain");

        // command #3 — still pending: saved but never completed
        UUID cmd3 = UUID.randomUUID();
        PlanTentativeConferenceRequest req3 = newRequest(cmd3, "Conf Three (pending)");
        persister.saveCommand(cmd3, req3);

        // command #4 — succeeds, produces two events
        UUID cmd4 = UUID.randomUUID();
        PlanTentativeConferenceRequest req4 = newRequest(cmd4, "Conf Four");
        persister.saveCommand(cmd4, req4);
        persister.appendEvents(
                List.of(storedEvent(2L, cmd4, "Conf Four", req4),
                        storedEvent(3L, cmd4, "Conf Four", req4)),
                cmd4
        );

        assertThat(persister.countCommands(""))
                .isEqualTo(4);

        List<TimelineEntry> page = persister.loadTimelinePage(0, 50, "");

        assertThat(page)
                .hasSize(4);

        // #1 succeeded
        assertThat(page.get(0).command().commandId()).isEqualTo(cmd1);
        assertThat(page.get(0).events()).hasSize(1);
        assertThat(page.get(0).failed()).isFalse();
        assertThat(page.get(0).command().succeeded())
                .as("command with events is SUCCEEDED")
                .isTrue();

        // #2 failed (domain)
        assertThat(page.get(1).command().commandId()).isEqualTo(cmd2);
        assertThat(page.get(1).events()).isEmpty();
        assertThat(page.get(1).failed()).isTrue();
        assertThat(page.get(1).command().statusLabel()).isEqualTo("Failed: domain");

        // #3 pending (saved, no events, not marked failed) — not flagged failed
        assertThat(page.get(2).command().commandId()).isEqualTo(cmd3);
        assertThat(page.get(2).events()).isEmpty();
        assertThat(page.get(2).failed()).isFalse();
        assertThat(page.get(2).command().pending())
                .as("saved-but-incomplete command is PENDING, not failed")
                .isTrue();

        // #4 succeeded with two events
        assertThat(page.get(3).command().commandId()).isEqualTo(cmd4);
        assertThat(page.get(3).events()).hasSize(2);
        assertThat(page.get(3).events().get(0).sequence()).isEqualTo(2L);
        assertThat(page.get(3).events().get(1).sequence()).isEqualTo(3L);

        // payloads should be pretty-printed JSON (multi-line)
        assertThat(page.get(0).command().payloadJson()).contains("\n");
        assertThat(page.get(0).events().getFirst().payloadJson()).contains("\n");

        // none are out-of-order in this happy path
        assertThat(page).allSatisfy(entry -> assertThat(entry.outOfOrder()).isFalse());
    }

    private PlanTentativeConferenceRequest newRequest(UUID id, String name) {
        PlanTentativeConferenceRequest r = new PlanTentativeConferenceRequest();
        r.setConferenceId(id.toString());
        r.setName(name);
        r.setStartDate(LocalDateTime.now().plusDays(10));
        r.setEndDate(LocalDateTime.now().plusDays(12));
        r.setVenueName("Venue");
        r.setVenueStreet("Street");
        r.setVenueCity("City");
        r.setVenueCountry("Country");
        r.setVenuePostalCode("12345");
        return r;
    }

    private StoredEvent storedEvent(long sequence, UUID commandId, String name, PlanTentativeConferenceRequest req) {
        return new StoredEvent(
                sequence,
                ConferenceTentativelyPlanned.class,
                UUID.randomUUID(),
                Instant.now(),
                new ConferenceTentativelyPlanned(
                        ConferenceId.of(commandId),
                        name,
                        ZonedTimestamp.fromLocal(req.getStartDate(), ZoneId.of("UTC")),
                        ZonedTimestamp.fromLocal(req.getEndDate(), ZoneId.of("UTC")),
                        "Venue",
                        new Address("Street", "City", null, "12345", "Country", null)
                ),
                commandId
        );
    }

    @Test
    void pendingCommandsAreCountedListedAndCanBeAbandoned() {
        // a still-pending command (saved, no events)
        UUID pending = UUID.randomUUID();
        persister.saveCommand(pending, newRequest(pending, "Pending Conf"));

        // a succeeded command (saved + events)
        UUID succeeded = UUID.randomUUID();
        PlanTentativeConferenceRequest succeededReq = newRequest(succeeded, "Done Conf");
        persister.saveCommand(succeeded, succeededReq);
        persister.appendEvents(List.of(storedEvent(1L, succeeded, "Done Conf", succeededReq)), succeeded);

        assertThat(persister.countPendingCommands())
                .isEqualTo(1);
        assertThat(persister.findPendingCommands())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.commandId()).isEqualTo(pending);
                    assertThat(c.pending())
                            .as("listed command is PENDING")
                            .isTrue();
                    assertThat(c.payloadJson()).contains("Pending Conf");
                });

        persister.abandonCommand(pending);

        assertThat(persister.countPendingCommands())
                .as("abandoned command is no longer pending")
                .isZero();
        assertThat(persister.findPendingCommands())
                .isEmpty();

        // abandon is guarded on status='PENDING': a succeeded command is untouched
        persister.abandonCommand(succeeded);
        assertThat(persister.findAllCommandsForBackup())
                .filteredOn(c -> c.commandId().equals(succeeded))
                .singleElement()
                .satisfies(c -> assertThat(c.status())
                        .as("a guarded abandon leaves a SUCCEEDED command's status untouched")
                        .isEqualTo("SUCCEEDED"));
    }

    @Test
    void canSaveAndLoadCommandAndEvents() {
        UUID commandId = UUID.randomUUID();
        PlanTentativeConferenceRequest request = new PlanTentativeConferenceRequest();
        request.setConferenceId(commandId.toString());
        request.setName("Test Conference");
        request.setStartDate(LocalDateTime.now().plusDays(10));
        request.setEndDate(LocalDateTime.now().plusDays(12));
        request.setVenueName("Test Venue");
        request.setVenueStreet("Street");
        request.setVenueCity("City");
        request.setVenueCountry("Country");
        request.setVenuePostalCode("12345");

        persister.saveCommand(commandId, request);

        StoredEvent event = new StoredEvent(
                1L,
                ConferenceTentativelyPlanned.class,
                UUID.randomUUID(),
                Instant.now(),
                new ConferenceTentativelyPlanned(
                        ConferenceId.of(commandId),
                        "Test Conference",
                        ZonedTimestamp.fromLocal(request.getStartDate(), ZoneId.of("UTC")),
                        ZonedTimestamp.fromLocal(request.getEndDate(), ZoneId.of("UTC")),
                        "Test Venue",
                        new Address("Street", "City", null, "12345", "Country", null)
                ),
                commandId
        );

        persister.appendEvents(List.of(event), commandId);

        assertThat(persister.loadAllEvents())
                .hasSize(1);
        assertThat(persister.loadAllEvents().getFirst().sequence())
                .isEqualTo(1L);
        assertThat(((ConferenceTentativelyPlanned) persister.loadAllEvents().getFirst().payload()).name())
                .isEqualTo("Test Conference");

        assertThat(persister.getMaxSequence())
                .isEqualTo(1L);
    }

    @Test
    void backupCapturesEveryCommandStatusWithAllColumns() {
        // succeeded — has events, event_ids populated, status SUCCEEDED
        UUID succeeded = UUID.randomUUID();
        PlanTentativeConferenceRequest succeededReq = newRequest(succeeded, "Succeeded Conf");
        persister.saveCommand(succeeded, succeededReq);
        StoredEvent event = storedEvent(1L, succeeded, "Succeeded Conf", succeededReq);
        persister.appendEvents(List.of(event), succeeded);

        // domain failure — no events, error recorded
        UUID failed = UUID.randomUUID();
        persister.saveCommand(failed, newRequest(failed, "Failed Conf"));
        persister.markCommandFailed(failed, "FAILED_DOMAIN", "rejected by domain");

        // still pending — no events, no error
        UUID pending = UUID.randomUUID();
        persister.saveCommand(pending, newRequest(pending, "Pending Conf"));

        List<PostgresPersister.BackupCommandRow> commands = persister.findAllCommandsForBackup();

        assertThat(commands)
                .as("backup keeps all statuses, not only SUCCEEDED")
                .hasSize(3);

        PostgresPersister.BackupCommandRow succeededRow = rowFor(commands, succeeded);
        assertThat(succeededRow.status()).isEqualTo("SUCCEEDED");
        assertThat(succeededRow.error()).isNull();
        assertThat(succeededRow.eventIds())
                .as("succeeded command carries its event ids")
                .containsExactly(event.eventId());
        assertThat(succeededRow.type()).isEqualTo(succeededReq.getClass().getName());
        assertThat(succeededRow.payloadJson()).contains("Succeeded Conf");

        PostgresPersister.BackupCommandRow failedRow = rowFor(commands, failed);
        assertThat(failedRow.status()).isEqualTo("FAILED_DOMAIN");
        assertThat(failedRow.error()).isEqualTo("rejected by domain");
        assertThat(failedRow.eventIds())
                .as("a command with no events has null event_ids")
                .isNull();

        PostgresPersister.BackupCommandRow pendingRow = rowFor(commands, pending);
        assertThat(pendingRow.status()).isEqualTo("PENDING");
        assertThat(pendingRow.eventIds()).isNull();

        List<PostgresPersister.BackupEventRow> events = persister.findAllEventsForBackup();
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.sequence()).isEqualTo(1L);
            assertThat(e.eventId()).isEqualTo(event.eventId());
            assertThat(e.commandId()).isEqualTo(succeeded);
            assertThat(e.payloadJson()).contains("Succeeded Conf");
        });
    }

    @Test
    void restoreReinsertsCommandsAndEventsVerbatimAndIsIdempotent() {
        UUID succeeded = UUID.randomUUID();
        PlanTentativeConferenceRequest succeededReq = newRequest(succeeded, "Succeeded Conf");
        persister.saveCommand(succeeded, succeededReq);
        persister.appendEvents(
                List.of(storedEvent(1L, succeeded, "Succeeded Conf", succeededReq),
                        storedEvent(2L, succeeded, "Succeeded Conf", succeededReq)),
                succeeded);

        UUID failed = UUID.randomUUID();
        persister.saveCommand(failed, newRequest(failed, "Failed Conf"));
        persister.markCommandFailed(failed, "FAILED_DOMAIN", "rejected by domain");

        List<PostgresPersister.BackupCommandRow> commands = persister.findAllCommandsForBackup();
        List<PostgresPersister.BackupEventRow> events = persister.findAllEventsForBackup();

        persister.truncateAllTables();
        assertThat(persister.findAllCommandsForBackup()).isEmpty();
        assertThat(persister.findAllEventsForBackup()).isEmpty();

        persister.restoreCommandsAndEvents(commands, events);

        assertThat(persister.findAllCommandsForBackup())
                .as("commands come back byte-for-byte, including status/error/event_ids")
                .containsExactlyElementsOf(commands);
        assertThat(persister.findAllEventsForBackup())
                .as("events come back verbatim: same sequence, event_id, timestamp, payload")
                .containsExactlyElementsOf(events);

        // re-running the same file is a no-op (ON CONFLICT DO NOTHING) — resumable restore
        persister.restoreCommandsAndEvents(commands, events);

        assertThat(persister.findAllCommandsForBackup())
                .as("a second restore of the same file adds nothing")
                .containsExactlyElementsOf(commands);
        assertThat(persister.findAllEventsForBackup())
                .containsExactlyElementsOf(events);
    }

    private PostgresPersister.BackupCommandRow rowFor(List<PostgresPersister.BackupCommandRow> commands, UUID commandId) {
        return commands.stream()
                .filter(c -> c.commandId().equals(commandId))
                .findFirst()
                .orElseThrow();
    }
}

package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Address;
import dev.ted.jittertravel.domain.ConferenceCancelled;
import dev.ted.jittertravel.domain.ConferenceId;
import dev.ted.jittertravel.domain.ConferencePlanned;
import dev.ted.jittertravel.domain.Event;
import dev.ted.jittertravel.domain.OneOffTaskCompleted;
import dev.ted.jittertravel.domain.TrainCancelled;
import dev.ted.jittertravel.domain.TrainTripId;
import dev.ted.jittertravel.domain.ZonedTimestamp;
import dev.ted.jittertravel.web.PlanConferenceRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("spring")
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresPersister.class)
class PostgresPersisterTest extends AbstractTestcontainerIntegrationTest {

    @Autowired
    private PostgresPersister persister;

    @Autowired
    private JdbcClient jdbcClient;

    @TestConfiguration
    static class TestConfig {
        // needed for the instantiation of the PostgresPersister; use the pinned production config
        @Bean
        JsonMapper jsonMapper() {
            return EventJsonMapperFactory.create();
        }

        @Bean
        EventPayloadUpcaster eventPayloadUpcaster() {
            return EventPayloadUpcaster.standard();
        }

        /**
         * Advances one millisecond per read, so each saved command gets a distinct
         * {@code command_log.timestamp}. A frozen clock would tie every row and leave
         * {@code loadTimelinePage}'s {@code ORDER BY timestamp, command_id} sorting by
         * random UUID instead of by insertion order.
         */
        @Bean
        Clock clock() {
            return new AdvancingClock(Instant.parse("2026-06-25T12:00:00Z"));
        }
    }

    private static class AdvancingClock extends Clock {
        private final AtomicReference<Instant> current;

        AdvancingClock(Instant start) {
            this.current = new AtomicReference<>(start);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.getAndUpdate(instant -> instant.plusMillis(1));
        }
    }

    @Test
    void loadTimelinePageReflectsCommandStatus() {
        // command #1 — succeeds, produces one event
        UUID cmd1 = UUID.randomUUID();
        PlanConferenceRequest req1 = newRequest(cmd1, "Conf One");
        persister.saveCommand(cmd1, req1);
        persister.appendEvents(List.of(storedEvent(1L, cmd1, "Conf One", req1)), cmd1);

        // command #2 — domain failure: saved, marked failed, no events
        UUID cmd2 = UUID.randomUUID();
        PlanConferenceRequest req2 = newRequest(cmd2, "Conf Two (failed)");
        persister.saveCommand(cmd2, req2);
        persister.markCommandFailed(cmd2, "FAILED_DOMAIN", "rejected by domain");

        // command #3 — still pending: saved but never completed
        UUID cmd3 = UUID.randomUUID();
        PlanConferenceRequest req3 = newRequest(cmd3, "Conf Three (pending)");
        persister.saveCommand(cmd3, req3);

        // command #4 — succeeds, produces two events
        UUID cmd4 = UUID.randomUUID();
        PlanConferenceRequest req4 = newRequest(cmd4, "Conf Four");
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
        assertThat(new TimelineCommand(UUID.randomUUID(), null, "t", "{}", "FAILED_SEND").statusLabel())
                .as("an unmapped status would render as the raw enum string")
                .isEqualTo("Failed: send");

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

    @Test
    void anUnfilteredEventPageIsNewestFirstWithSimpleTypeNamesAndPrettyPayloads() {
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), "first"));
        StoredEvent second = appendEvent(2L, new TrainCancelled(TrainTripId.of(UUID.randomUUID()), "second"));
        appendEvent(3L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), "third"));

        List<PostgresPersister.EventLogRow> page = persister.loadEventPage(0, 50, EventLogFilter.none());

        assertThat(page)
                .extracting(PostgresPersister.EventLogRow::sequence)
                .as("newest first")
                .containsExactly(3L, 2L, 1L);
        PostgresPersister.EventLogRow trainRow = page.get(1);
        assertThat(trainRow.type())
                .isEqualTo("TrainCancelled");
        assertThat(trainRow.eventId())
                .isEqualTo(second.eventId());
        assertThat(trainRow.commandId())
                .isEqualTo(second.commandId());
        assertThat(trainRow.payloadJson())
                .as("pretty-printed: one field per line")
                .contains("\n  \"reason\" : \"second\"");
        assertThat(persister.countEvents(EventLogFilter.none()))
                .isEqualTo(3);
    }

    @Test
    void anEventPageHonoursItsLimitAndOffsetWithinTheFilteredRows() {
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));
        appendEvent(2L, new TrainCancelled(TrainTripId.of(UUID.randomUUID()), ""));
        appendEvent(3L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));
        appendEvent(4L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));
        EventLogFilter conferenceCancellations = new EventLogFilter(List.of("ConferenceCancelled"), List.of());

        assertThat(persister.loadEventPage(0, 2, conferenceCancellations))
                .extracting(PostgresPersister.EventLogRow::sequence)
                .as("the first page of two")
                .containsExactly(4L, 3L);
        assertThat(persister.loadEventPage(2, 2, conferenceCancellations))
                .extracting(PostgresPersister.EventLogRow::sequence)
                .as("the offset counts matching rows, so the train event between them is not a slot")
                .containsExactly(1L);
        assertThat(persister.countEvents(conferenceCancellations))
                .as("the count is of the rows the pages hold")
                .isEqualTo(3);
        assertThat(persister.countEvents())
                .as("the unfiltered total is untouched by a filter")
                .isEqualTo(4);
    }

    @Test
    void anEventPageFilteredByTypeHoldsOnlyRowsOfTheChosenTypes() {
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));
        appendEvent(2L, new TrainCancelled(TrainTripId.of(UUID.randomUUID()), ""));
        appendEvent(3L, new OneOffTaskCompleted("a-task", Instant.parse("2026-11-05T10:00:00Z")));

        EventLogFilter oneType = new EventLogFilter(List.of("TrainCancelled"), List.of());
        assertThat(persister.loadEventPage(0, 50, oneType))
                .extracting(PostgresPersister.EventLogRow::sequence)
                .containsExactly(2L);
        assertThat(persister.countEvents(oneType))
                .isEqualTo(1);

        EventLogFilter twoTypes = new EventLogFilter(List.of("TrainCancelled", "ConferenceCancelled"), List.of());
        assertThat(persister.loadEventPage(0, 50, twoTypes))
                .extracting(PostgresPersister.EventLogRow::sequence)
                .as("several types are any-of")
                .containsExactly(2L, 1L);
        assertThat(persister.countEvents(twoTypes))
                .isEqualTo(2);
    }

    @Test
    void aTypeFilterFindsRowsStoredUnderTheClassNameFromBeforeLogicalNames() {
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));
        appendEvent(2L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));
        jdbcClient.sql("UPDATE event_log SET type = :className WHERE sequence = 1")
                .param("className", ConferenceCancelled.class.getName())
                .update();

        EventLogFilter filter = new EventLogFilter(List.of("ConferenceCancelled"), List.of());

        assertThat(persister.loadEventPage(0, 50, filter))
                .extracting(PostgresPersister.EventLogRow::sequence)
                .as("the old row is the same type under its older spelling")
                .containsExactly(2L, 1L);
        assertThat(persister.loadEventPage(0, 50, filter))
                .extracting(PostgresPersister.EventLogRow::type)
                .as("and both are shown by their simple name")
                .containsExactly("ConferenceCancelled", "ConferenceCancelled");
    }

    @Test
    void anIdentifierMatchesAnIdInThePayloadOrTheEventIdOrTheCommandId() {
        UUID conferenceId = UUID.randomUUID();
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(conferenceId), ""));
        StoredEvent second = appendEvent(2L, new TrainCancelled(TrainTripId.of(UUID.randomUUID()), ""));
        StoredEvent third = appendEvent(3L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));

        assertThat(sequencesMatching(conferenceId.toString()))
                .as("an id inside the payload")
                .containsExactly(1L);
        assertThat(sequencesMatching(second.eventId().toString()))
                .as("the row's own event id, which is not in its payload")
                .containsExactly(2L);
        assertThat(sequencesMatching(third.commandId().toString()))
                .as("the row's command id, which is not in its payload")
                .containsExactly(3L);
        assertThat(sequencesMatching(UUID.randomUUID().toString()))
                .as("an id nothing carries")
                .isEmpty();
    }

    @Test
    void severalIdentifiersAreAnyOf() {
        UUID conferenceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(conferenceId), ""));
        appendEvent(2L, new TrainCancelled(TrainTripId.of(tripId), ""));
        appendEvent(3L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), ""));
        EventLogFilter eitherId = new EventLogFilter(List.of(), List.of(conferenceId.toString(), tripId.toString()));

        assertThat(persister.loadEventPage(0, 50, eitherId))
                .extracting(PostgresPersister.EventLogRow::sequence)
                .as("no row carries both ids, so all-of would be empty")
                .containsExactly(2L, 1L);
        assertThat(persister.countEvents(eitherId))
                .isEqualTo(2);
    }

    @Test
    void typesAndIdentifiersTogetherAreBothRequired() {
        UUID conferenceId = UUID.randomUUID();
        UUID tripId = UUID.randomUUID();
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(conferenceId), ""));
        appendEvent(2L, new TrainCancelled(TrainTripId.of(tripId), ""));
        appendEvent(3L, new TrainCancelled(TrainTripId.of(UUID.randomUUID()), ""));
        EventLogFilter trainsWithEitherId = new EventLogFilter(
                List.of("TrainCancelled"), List.of(conferenceId.toString(), tripId.toString()));

        assertThat(persister.loadEventPage(0, 50, trainsWithEitherId))
                .extracting(PostgresPersister.EventLogRow::sequence)
                .as("#1 has a matching id but the wrong type, #3 the right type but neither id")
                .containsExactly(2L);
        assertThat(persister.countEvents(trainsWithEitherId))
                .isEqualTo(1);
    }

    @Test
    void anIdentifierIsLookedForAsTextAndNeverAsAPattern() {
        appendEvent(1L, new ConferenceCancelled(ConferenceId.of(UUID.randomUUID()), "plain reason"));

        assertThat(sequencesMatching("%"))
                .as("% matches every row as a LIKE pattern; no payload here holds one")
                .isEmpty();
        assertThat(sequencesMatching("plain_reason"))
                .as("_ matches the space as a LIKE pattern; the payload holds a space, not an underscore")
                .isEmpty();
        assertThat(sequencesMatching("lain reas"))
                .as("part of a value is still found")
                .containsExactly(1L);
    }

    private List<Long> sequencesMatching(String identifier) {
        return persister.loadEventPage(0, 50, new EventLogFilter(List.of(), List.of(identifier)))
                .stream()
                .map(PostgresPersister.EventLogRow::sequence)
                .toList();
    }

    /** Appends one event under a command of its own, so its command id is in no payload. */
    private StoredEvent appendEvent(long sequence, Event payload) {
        UUID commandId = UUID.randomUUID();
        persister.saveCommand(commandId, newRequest(UUID.randomUUID(), "Command " + sequence));
        StoredEvent event = new StoredEvent(
                sequence, payload.getClass(), UUID.randomUUID(), Instant.now(), payload, commandId);
        persister.appendEvents(List.of(event), commandId);
        return event;
    }

    private PlanConferenceRequest newRequest(UUID id, String name) {
        return new PlanConferenceRequest(
                id.toString(), name,
                LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(12),
                "Venue", "Street", "City", null, "Country", "12345", null,
                null, null, null, null);
    }

    private StoredEvent storedEvent(long sequence, UUID commandId, String name, PlanConferenceRequest req) {
        return new StoredEvent(
                sequence,
                ConferencePlanned.class,
                UUID.randomUUID(),
                Instant.now(),
                new ConferencePlanned(
                        ConferenceId.of(commandId),
                        name,
                        ZonedTimestamp.fromLocal(req.startDate(), ZoneId.of("UTC")),
                        ZonedTimestamp.fromLocal(req.endDate(), ZoneId.of("UTC")),
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
        PlanConferenceRequest succeededReq = newRequest(succeeded, "Done Conf");
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
    void failedCommandsAreListedForReviewButNeverCountedAsPending() {
        UUID failedSend = UUID.randomUUID();
        persister.saveCommand(failedSend, newRequest(failedSend, "Failed Send"));
        persister.markCommandFailed(failedSend, "FAILED_SEND", "Brevo returned 502");
        UUID failedDomain = UUID.randomUUID();
        persister.saveCommand(failedDomain, newRequest(failedDomain, "Failed Domain"));
        persister.markCommandFailed(failedDomain, "FAILED_DOMAIN", "rejected");
        UUID succeeded = UUID.randomUUID();
        PlanConferenceRequest succeededReq = newRequest(succeeded, "Done Conf");
        persister.saveCommand(succeeded, succeededReq);
        persister.appendEvents(List.of(storedEvent(1L, succeeded, "Done Conf", succeededReq)), succeeded);

        assertThat(persister.findPendingCommands())
                .extracting(TimelineCommand::commandId)
                .as("failed rows are listed; a succeeded one is not")
                .containsExactlyInAnyOrder(failedSend, failedDomain);
        assertThat(persister.countPendingCommands())
                .as("the badge and the boot warning stay on PENDING, or every old failure would warn on every boot")
                .isZero();
    }

    @Test
    void canSaveAndLoadCommandAndEvents() {
        UUID commandId = UUID.randomUUID();
        PlanConferenceRequest request = new PlanConferenceRequest(
                commandId.toString(), "Test Conference",
                LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(12),
                "Test Venue", "Street", "City", null, "Country", "12345", null,
                null, null, null, null);

        persister.saveCommand(commandId, request);

        StoredEvent event = new StoredEvent(
                1L,
                ConferencePlanned.class,
                UUID.randomUUID(),
                Instant.now(),
                new ConferencePlanned(
                        ConferenceId.of(commandId),
                        "Test Conference",
                        ZonedTimestamp.fromLocal(request.startDate(), ZoneId.of("UTC")),
                        ZonedTimestamp.fromLocal(request.endDate(), ZoneId.of("UTC")),
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
        assertThat(((ConferencePlanned) persister.loadAllEvents().getFirst().payload()).name())
                .isEqualTo("Test Conference");
    }

    /**
     * {@code loadAllEvents()} orders by sequence, and that is <strong>contract</strong>:
     * {@code EventStore.reload()} takes its next sequence from the last row, so a dropped
     * {@code ORDER BY} would hand the store a sequence the log already holds and the next append
     * would collide on the primary key. Rows are written out of order here because Postgres
     * returns heap order without one, so nothing else in this class would notice.
     */
    @Test
    void loadAllEventsReturnsEventsInSequenceOrderHoweverTheyWereWritten() {
        for (long sequence : new long[]{3L, 1L, 2L}) {
            UUID commandId = UUID.randomUUID();
            PlanConferenceRequest request = newRequest(commandId, "Conference " + sequence);
            persister.saveCommand(commandId, request);
            persister.appendEvents(List.of(storedEvent(sequence, commandId, "Conference " + sequence, request)), commandId);
        }

        assertThat(persister.loadAllEvents())
                .extracting(StoredEvent::sequence)
                .as("the last row is the highest sequence, which is what reload() relies on")
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void backupCapturesEveryCommandStatusWithAllColumns() {
        // succeeded — has events, event_ids populated, status SUCCEEDED
        UUID succeeded = UUID.randomUUID();
        PlanConferenceRequest succeededReq = newRequest(succeeded, "Succeeded Conf");
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
    void appendStampsTheEventTypesCurrentSchemaVersion() {
        UUID commandId = UUID.randomUUID();
        PlanConferenceRequest req = newRequest(commandId, "Stamped Conf");
        persister.saveCommand(commandId, req);
        // ConferencePlanned migrated datetimes → ZonedTimestamp (v2) and then added the
        // format field (v3), then stored its venue's country as an ISO code (v4).
        persister.appendEvents(List.of(storedEvent(1L, commandId, "Stamped Conf", req)), commandId);

        assertThat(persister.findAllEventsForBackup())
                .singleElement()
                .satisfies(e -> assertThat(e.schemaVersion())
                        .as("a freshly appended event carries its type's current schema version")
                        .isEqualTo(4));
    }

    @Test
    void restoreReinsertsCommandsAndEventsVerbatimAndIsIdempotent() {
        UUID succeeded = UUID.randomUUID();
        PlanConferenceRequest succeededReq = newRequest(succeeded, "Succeeded Conf");
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

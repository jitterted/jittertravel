package dev.ted.jittertravel.infrastructure;

import dev.ted.jittertravel.domain.Event;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Array;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@SuppressWarnings("DataFlowIssue")
@Repository
public class PostgresPersister {
    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;
    private final EventPayloadUpcaster upcaster;
    private final Clock clock;

    public PostgresPersister(JdbcClient jdbcClient, JsonMapper jsonMapper, EventPayloadUpcaster upcaster,
                             Clock clock) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
        this.upcaster = upcaster;
        this.clock = clock;
    }

    public void saveCommand(UUID commandId, Object dto) {
        try {
            String payload = jsonMapper.writeValueAsString(dto);
            jdbcClient.sql("""
                            INSERT INTO command_log (command_id, timestamp, type, payload, status)
                            VALUES (:commandId, :timestamp, :type, CAST(:payload AS jsonb), 'PENDING')
                            """)
                    .param("commandId", commandId)
                    .param("timestamp", Instant.now(clock).atOffset(ZoneOffset.UTC))
                    .param("type", dto.getClass().getName())
                    .param("payload", payload)
                    .update();
        } catch (Exception e) {
            throw new RuntimeException("Failed to save command to WAL", e);
        }
    }

    /**
     * Records a command that did not produce events: {@code FAILED_DOMAIN} when the
     * domain rejected it (execute threw), {@code FAILED_PERSIST} when the event write
     * failed. The command row already exists (written PENDING by {@link #saveCommand}).
     */
    public void markCommandFailed(UUID commandId, String status, String errorMessage) {
        jdbcClient.sql("""
                        UPDATE command_log
                        SET status = :status, error = :error
                        WHERE command_id = :commandId
                        """)
                .param("status", status)
                .param("error", clip(errorMessage))
                .param("commandId", commandId)
                .update();
    }

    private static String clip(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    /**
     * Marks a still-PENDING command as ABANDONED. Guarded on the current status so a
     * command that has since completed (SUCCEEDED/FAILED) is never clobbered.
     */
    public void abandonCommand(UUID commandId) {
        jdbcClient.sql("""
                        UPDATE command_log
                        SET status = 'ABANDONED'
                        WHERE command_id = :commandId AND status = 'PENDING'
                        """)
                .param("commandId", commandId)
                .update();
    }

    @Transactional
    public void appendEvents(List<StoredEvent> events, UUID commandId) {
        StoredEvent currentEvent = null;
        try {
            for (StoredEvent event : events) {
                currentEvent = event;
                StoredEventRow row = StoredEventRow.fromStoredEvent(event, jsonMapper);

                jdbcClient.sql("""
                                INSERT INTO event_log (sequence, event_id, command_id, timestamp, type, payload, schema_version)
                                VALUES (:sequence, :eventId, :commandId, :timestamp, :type, CAST(:payloadJson AS jsonb), :schemaVersion)
                                """)
                        .param("sequence", row.sequence())
                        .param("eventId", row.eventId())
                        .param("commandId", row.commandId())
                        .param("timestamp", row.timestamp())
                        .param("type", row.type())
                        .param("payloadJson", row.payloadJson())
                        .param("schemaVersion", row.schemaVersion())
                        .update();
            }
            currentEvent = null;
            linkCommandToEvents(commandId, events.stream().map(StoredEvent::eventId).toList());
        } catch (Exception e) {
            String context = currentEvent != null
                    ? "event type=%s, eventId=%s".formatted(currentEvent.type().getSimpleName(), currentEvent.eventId())
                    : "linking events to command";
            throw new RuntimeException(
                    "Failed to persist event and link to command; commandId=%s, failing step: %s"
                            .formatted(commandId, context), e);
        }
    }

    private void linkCommandToEvents(UUID commandId, List<UUID> eventIds) {
        jdbcClient.sql("""
                        UPDATE command_log
                        SET event_ids = :eventIds, status = 'SUCCEEDED'
                        WHERE command_id = :commandId
                        """)
                .param("eventIds", eventIds.toArray(new UUID[0]))
                .param("commandId", commandId)
                .update();
    }

    public int countEvents() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM event_log")
                .query(Long.class)
                .single();
        return count.intValue();
    }

    public List<EventLogRow> loadEventPage(int offset, int limit) {
        return jdbcClient.sql("""
                        SELECT sequence,
                               event_id     AS eventId,
                               command_id   AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson
                        FROM event_log
                        ORDER BY sequence DESC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, _) -> new EventLogRow(
                        rs.getLong("sequence"),
                        (UUID) rs.getObject("eventId"),
                        (UUID) rs.getObject("commandId"),
                        rs.getObject("timestamp", OffsetDateTime.class),
                        simpleTypeName(rs.getString("type")),
                        prettyJson(rs.getString("payloadJson"))
                ))
                .list();
    }

    public record EventLogRow(
            long sequence,
            UUID eventId,
            UUID commandId,
            OffsetDateTime timestamp,
            String type,
            String payloadJson
    ) {}

    private static String simpleTypeName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    public int countCommands(String status) {
        String where = statusWhereClause(status);
        JdbcClient.StatementSpec spec = jdbcClient.sql("SELECT COUNT(*) FROM command_log " + where);
        if (needsStatusParam(status)) {
            spec = spec.param("status", status);
        }
        return spec.query(Long.class).single().intValue();
    }

    @Transactional
    public void deleteCommand(UUID commandId) {
        jdbcClient.sql("DELETE FROM event_log WHERE command_id = :commandId")
                .param("commandId", commandId)
                .update();
        jdbcClient.sql("DELETE FROM command_log WHERE command_id = :commandId")
                .param("commandId", commandId)
                .update();
    }

    public int countPendingCommands() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM command_log WHERE status = 'PENDING'")
                .query(Long.class)
                .single();
        return count.intValue();
    }

    /**
     * Loads all PENDING commands (oldest first) with their payloads pretty-printed,
     * for the admin pending-commands review page.
     */
    public List<TimelineCommand> findPendingCommands() {
        return jdbcClient.sql("""
                        SELECT command_id   AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson,
                               status
                        FROM command_log
                        WHERE status = 'PENDING'
                        ORDER BY timestamp ASC, command_id ASC
                        """)
                .query(TimelineCommand.class)
                .list()
                .stream()
                .map(c -> new TimelineCommand(c.commandId(), c.timestamp(), c.type(), prettyJson(c.payloadJson()), c.status()))
                .toList();
    }

    /**
     * Loads a page of commands (oldest first) along with their resulting events.
     * Within the returned page, an entry is marked {@code outOfOrder} if its events'
     * sequence numbers start before the running max sequence of previously-listed
     * commands (i.e. its events are interleaved with an earlier command's events).
     * Pass null or blank {@code status} to load all commands; "FAILED" matches both
     * FAILED_DOMAIN and FAILED_PERSIST.
     */
    public List<TimelineEntry> loadTimelinePage(int offset, int limit, String status) {
        String where = statusWhereClause(status);
        JdbcClient.StatementSpec spec = jdbcClient.sql("""
                        SELECT command_id   AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson,
                               status
                        FROM command_log
                        """ + where + """

                        ORDER BY timestamp ASC, command_id ASC
                        LIMIT :limit OFFSET :offset
                        """)
                .param("limit", limit)
                .param("offset", offset);
        if (needsStatusParam(status)) {
            spec = spec.param("status", status);
        }
        List<TimelineCommand> commands = spec
                .query(TimelineCommand.class)
                .list()
                .stream()
                .map(c -> new TimelineCommand(c.commandId(), c.timestamp(), c.type(), prettyJson(c.payloadJson()), c.status()))
                .toList();

        if (commands.isEmpty()) {
            return List.of();
        }

        UUID[] commandIds = commands.stream().map(TimelineCommand::commandId).toArray(UUID[]::new);

        Map<UUID, List<TimelineEvent>> eventsByCommand = new HashMap<>();
        jdbcClient.sql("""
                        SELECT command_id   AS commandId,
                               sequence,
                               event_id     AS eventId,
                               timestamp,
                               type,
                               payload::text AS payloadJson
                        FROM event_log
                        WHERE command_id = ANY(:commandIds)
                        ORDER BY sequence
                        """)
                .param("commandIds", commandIds)
                .query((rs, _) -> {
                    UUID cmdId = (UUID) rs.getObject("commandId");
                    TimelineEvent event = new TimelineEvent(
                            rs.getLong("sequence"),
                            (UUID) rs.getObject("eventId"),
                            rs.getObject("timestamp", OffsetDateTime.class),
                            rs.getString("type"),
                            prettyJson(rs.getString("payloadJson"))
                    );
                    eventsByCommand.computeIfAbsent(cmdId, _ -> new ArrayList<>()).add(event);
                    return event;
                })
                .list();

        List<TimelineEntry> entries = new ArrayList<>(commands.size());
        long runningMaxSeq = Long.MIN_VALUE;
        for (TimelineCommand command : commands) {
            List<TimelineEvent> events = eventsByCommand.getOrDefault(command.commandId(), List.of());
            boolean failed = command.failed();
            boolean outOfOrder = false;
            if (!events.isEmpty()) {
                long minSeq = events.getFirst().sequence();
                long maxSeq = events.getLast().sequence();
                if (minSeq < runningMaxSeq) {
                    outOfOrder = true;
                }
                if (maxSeq > runningMaxSeq) {
                    runningMaxSeq = maxSeq;
                }
            }
            entries.add(new TimelineEntry(command, events, failed, outOfOrder));
        }
        return entries;
    }

    private String statusWhereClause(String status) {
        if (status == null || status.isBlank()) return "";
        if ("FAILED".equals(status)) return "WHERE status LIKE 'FAILED%'";
        return "WHERE status = :status";
    }

    private boolean needsStatusParam(String status) {
        return status != null && !status.isBlank() && !"FAILED".equals(status);
    }

    private String prettyJson(String rawJson) {
        if (rawJson == null) {
            return "";
        }
        try {
            return jsonMapper.readTree(rawJson).toPrettyString();
        } catch (Exception e) {
            return rawJson;
        }
    }

    public List<TableStat> tableStats() {
        List<TableStat> stats = new ArrayList<>();
        for (String table : List.of("command_log", "event_log")) {
            long count = jdbcClient.sql("SELECT COUNT(*) FROM " + table)
                    .query(Long.class)
                    .single();
            stats.add(new TableStat(table, count));
        }
        return stats;
    }

    public record TableStat(String tableName, long rowCount) {}

    public void truncateAllTables() {
        jdbcClient.sql("TRUNCATE TABLE event_log, command_log").update();
    }

    /**
     * Every command_log row — all statuses (SUCCEEDED, FAILED_DOMAIN, FAILED_PERSIST, PENDING,
     * ABANDONED), all columns — for an event-oriented backup (see
     * docs/EventOrientedBackupRestorePlan.md). Carries event_ids/status/error so the
     * command-to-event linkage survives verbatim. {@code type} and {@code payloadJson} are
     * opaque here — restore never resolves or re-runs a command.
     */
    public List<BackupCommandRow> findAllCommandsForBackup() {
        return jdbcClient.sql("""
                        SELECT command_id    AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson,
                               event_ids     AS eventIds,
                               status,
                               error
                        FROM command_log
                        ORDER BY timestamp ASC, command_id ASC
                        """)
                .query((rs, _) -> new BackupCommandRow(
                        (UUID) rs.getObject("commandId"),
                        rs.getObject("timestamp", OffsetDateTime.class),
                        rs.getString("type"),
                        rs.getString("payloadJson"),
                        uuidList(rs.getArray("eventIds")),
                        rs.getString("status"),
                        rs.getString("error")
                ))
                .list();
    }

    /**
     * Every event_log row for a backup, verbatim: reused sequence, event_id, command_id,
     * timestamp, the stored logical {@code type}, and the raw payload JSON (no deserialize
     * — a backup is written from the bytes on disk). Ordered by sequence.
     */
    public List<BackupEventRow> findAllEventsForBackup() {
        return jdbcClient.sql("""
                        SELECT sequence,
                               event_id       AS eventId,
                               command_id     AS commandId,
                               timestamp,
                               type,
                               payload::text  AS payloadJson,
                               schema_version AS schemaVersion
                        FROM event_log
                        ORDER BY sequence
                        """)
                .query((rs, _) -> new BackupEventRow(
                        rs.getLong("sequence"),
                        (UUID) rs.getObject("eventId"),
                        (UUID) rs.getObject("commandId"),
                        rs.getObject("timestamp", OffsetDateTime.class),
                        rs.getString("type"),
                        rs.getString("payloadJson"),
                        (Integer) rs.getObject("schemaVersion")
                ))
                .list();
    }

    private List<UUID> uuidList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return null;  // preserve SQL NULL: a command with no events has no event_ids
        }
        return List.of((UUID[]) sqlArray.getArray());
    }

    /**
     * Restores commands then events — in that order, so event_log's FK to command_log never
     * fails — as verbatim inserts in one transaction. Idempotent: a command_id already in
     * command_log or a sequence already in event_log is skipped (ON CONFLICT DO NOTHING), so a
     * partially-applied restore resumes by re-running the same file. This does not go through
     * CommandExecutor by design: it is a bulk load of already-durable events, not new events
     * appended from a command (see docs/EventOrientedBackupRestorePlan.md).
     *
     * <p>Returns how many rows were actually inserted; a restore that skipped everything
     * (all ids already present) returns zeros, so a caller can report restored-vs-skipped.
     */
    @Transactional
    public RestoreCounts restoreCommandsAndEvents(List<BackupCommandRow> commands, List<BackupEventRow> events) {
        int commandsInserted = 0;
        for (BackupCommandRow command : commands) {
            commandsInserted += jdbcClient.sql("""
                            INSERT INTO command_log (command_id, timestamp, type, payload, event_ids, status, error)
                            VALUES (:commandId, :timestamp, :type, CAST(:payload AS jsonb), CAST(:eventIds AS uuid[]), :status, :error)
                            ON CONFLICT (command_id) DO NOTHING
                            """)
                    .param("commandId", command.commandId())
                    .param("timestamp", command.timestamp())
                    .param("type", command.type())
                    .param("payload", command.payloadJson())
                    .param("eventIds", command.eventIds() == null ? null : command.eventIds().toArray(new UUID[0]))
                    .param("status", command.status())
                    .param("error", command.error())
                    .update();
        }
        int eventsInserted = 0;
        for (BackupEventRow event : events) {
            eventsInserted += jdbcClient.sql("""
                            INSERT INTO event_log (sequence, event_id, command_id, timestamp, type, payload, schema_version)
                            VALUES (:sequence, :eventId, :commandId, :timestamp, :type, CAST(:payload AS jsonb), :schemaVersion)
                            ON CONFLICT (sequence) DO NOTHING
                            """)
                    .param("sequence", event.sequence())
                    .param("eventId", event.eventId())
                    .param("commandId", event.commandId())
                    .param("timestamp", event.timestamp())
                    .param("type", event.type())
                    .param("payload", event.payloadJson())
                    .param("schemaVersion", event.schemaVersion())
                    .update();
        }
        return new RestoreCounts(commandsInserted, eventsInserted);
    }

    /** How many rows a restore actually inserted (the rest were skipped as already present). */
    public record RestoreCounts(int commandsInserted, int eventsInserted) {}

    /**
     * Rewrites the {@code payload} and {@code schema_version} of the given {@code event_log} rows in
     * one transaction, matched by {@code sequence}. Only those two columns change — {@code sequence},
     * {@code event_id}, {@code command_id} and {@code timestamp} are untouched — so events keep their
     * verbatim identity. Used by the eager legacy migration (see
     * {@code docs/LegacyEventEagerMigrationPlan.md}); never on the append path, which stamps at write
     * time. Returns the number of rows updated. Not routed through {@code CommandExecutor}: it appends
     * no new events, it rewrites existing ones — the orchestrating service performs the read-only
     * check up front, mirroring restore.
     */
    @Transactional
    public int migrateEventPayloads(List<MigratedEventRow> rows) {
        int updated = 0;
        for (MigratedEventRow row : rows) {
            updated += jdbcClient.sql("""
                            UPDATE event_log
                            SET payload = CAST(:payload AS jsonb), schema_version = :schemaVersion
                            WHERE sequence = :sequence
                            """)
                    .param("payload", row.payloadJson())
                    .param("schemaVersion", row.schemaVersion())
                    .param("sequence", row.sequence())
                    .update();
        }
        return updated;
    }

    /** A single event_log row's post-migration payload and schema-version stamp, matched by sequence. */
    public record MigratedEventRow(long sequence, String payloadJson, int schemaVersion) {}

    /**
     * A command_log row for backup/restore, verbatim. {@code eventIds} is null when the
     * command produced no events (a failed, pending, or abandoned command).
     */
    public record BackupCommandRow(
            UUID commandId,
            OffsetDateTime timestamp,
            String type,
            String payloadJson,
            List<UUID> eventIds,
            String status,
            String error
    ) {}

    /**
     * An event_log row for backup/restore, verbatim. {@code type} is the stored logical name.
     * {@code schemaVersion} is null for rows written before the stamp existed (and for events read
     * from a pre-stamp backup); a stamped restore carries it through unchanged.
     */
    public record BackupEventRow(
            long sequence,
            UUID eventId,
            UUID commandId,
            OffsetDateTime timestamp,
            String type,
            String payloadJson,
            Integer schemaVersion
    ) {}

    /**
     * Of the given command ids, those already present in {@code command_log}. Lets import skip
     * commands it has already written, so re-running a backup file is safe and resumable.
     */
    public Set<UUID> existingCommandIds(Collection<UUID> commandIds) {
        if (commandIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> found = jdbcClient.sql("SELECT command_id FROM command_log WHERE command_id IN (:commandIds)")
                .param("commandIds", commandIds)
                .query((rs, _) -> (UUID) rs.getObject("command_id"))
                .list();
        return new HashSet<>(found);
    }

    public long getMaxSequence() {
        // Returns max event sequence number, but if there are no events yet, returns 0.
        return jdbcClient.sql("SELECT COALESCE(MAX(sequence), 0) FROM event_log")
                .query(Long.class)
                .single();
    }

    public List<StoredEvent> loadAllEvents() {
        return jdbcClient.sql("""
                        SELECT sequence,
                               event_id AS eventId,
                               command_id AS commandId,
                               timestamp,
                               type,
                               payload::text AS payloadJson,
                               schema_version AS schemaVersion
                        FROM event_log
                        ORDER BY sequence
                        """)
                .query(StoredEventRow.class)
                .list() // materialize the JDBC stream (which is not a Collections Stream!) into a list
                .stream()
                .map(row -> row.toStoredEvent(jsonMapper, upcaster))
                .toList();
    }

    private record StoredEventRow(
            long sequence,
            UUID eventId,
            UUID commandId,
            OffsetDateTime timestamp,
            String type,
            String payloadJson,
            // Non-null on write (fromStoredEvent stamps the current version); nullable on read, since
            // legacy rows predate the column. toStoredEvent still upcasts structurally, so a null read
            // here is harmless — the stamp is the forward anchor, not this replay path's discriminator.
            Integer schemaVersion
    ) {
        static StoredEventRow fromStoredEvent(StoredEvent event, JsonMapper jsonMapper) {
            try {
                return new StoredEventRow(
                        event.sequence(),
                        event.eventId(),
                        event.commandId(),
                        event.timestamp().atOffset(ZoneOffset.UTC),
                        EventTypes.logicalNameFor(event.type()),
                        jsonMapper.writeValueAsString(event.payload()),
                        EventTypes.currentSchemaVersion(event.type())
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert StoredEvent to a database StoredEventRow object", e);
            }
        }

        StoredEvent toStoredEvent(JsonMapper jsonMapper, EventPayloadUpcaster upcaster) {
            try {
                Class<? extends Event> eventClass = EventTypes.classFor(type);

                // Legacy rows (bare scalar datetimes) are rewritten to the current shape before
                // binding; new rows pass through untouched.
                JsonNode tree = upcaster.upcast(type, jsonMapper.readTree(payloadJson));
                Event payload = jsonMapper.treeToValue(tree, eventClass);

                return new StoredEvent(
                        sequence,
                        eventClass,
                        eventId,
                        timestamp.toInstant(),
                        payload,
                        commandId
                );
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert StoredEventRow database object to a StoredEvent", e);
            }
        }
    }
}